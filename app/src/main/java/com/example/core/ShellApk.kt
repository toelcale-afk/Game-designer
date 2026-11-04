package com.example.core

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** What to put into the prebuilt WebView shell. */
data class QuickBuildSpec(
    val appName: String,
    val packageName: String,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    /** Files served from assets/www/ (index.html must be among them). */
    val webFiles: Map<String, ByteArray>,
    /** Square legacy launcher PNGs keyed by density; empty keeps the shell's icon. */
    val icons: Map<Density, ByteArray> = emptyMap(),
    val fullscreen: Boolean = true,
    /** "sensor", "landscape" or "portrait". */
    val orientation: String = "sensor"
) {
    fun validationError(): String? = when {
        appName.isBlank() -> "Enter an app name."
        appName.length > 50 -> "App name is too long (50 characters max)."
        !ShellApk.isValidPackageName(packageName) ->
            "'$packageName' isn't a valid package name. Use lowercase words separated by dots, like com.yourname.game."
        packageName == ShellApk.PLACEHOLDER_PACKAGE || packageName.startsWith("com.aistudio.packflow") ->
            "Pick your own package name (this one is reserved)."
        versionCode < 1 -> "Version code must be 1 or more."
        "index.html" !in webFiles -> "The web files need an index.html."
        else -> null
    }
}

data class ShellPatchReport(val replacedStrings: Int, val replacedIcons: Int, val webFiles: Int)

/**
 * Turns the prebuilt shell APK into an unsigned, zip-aligned APK for one game.
 * Signing happens afterwards (apksig, on the device).
 */
object ShellApk {
    /** Values compiled into shell/src/main/AndroidManifest.xml and shell/build.gradle.kts. */
    const val PLACEHOLDER_PACKAGE = "com.packflow.shell.placeholder"
    const val PLACEHOLDER_LABEL = "PackFlowShellAppName"
    const val PLACEHOLDER_VERSION_NAME = "0.0.0-packflow"
    const val WWW = "assets/www/"
    const val CONFIG_PATH = "assets/packflow_shell.json"

    private val PACKAGE = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    fun isValidPackageName(name: String): Boolean =
        PACKAGE.matches(name) && name.split('.').none { it in JAVA_KEYWORDS } && name.length <= 150

    /** "My Cool Game!" -> "com.packflow.games.mycoolgame" (a starting point the user can edit). */
    fun suggestPackageName(appName: String, owner: String = "packflow.games"): String {
        val slug = appName.lowercase().replace(Regex("[^a-z0-9]+"), "").trimStart { it.isDigit() }.take(30)
        val last = slug.ifBlank { "game" }.let { if (it in JAVA_KEYWORDS) "${it}game" else it }
        return "com.$owner.$last"
    }

    fun configJson(spec: QuickBuildSpec): String = Json.stringify(
        mapOf("appName" to spec.appName, "fullscreen" to spec.fullscreen, "orientation" to spec.orientation),
        indent = 2
    )

    private fun isOldSignature(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" || upper.endsWith(".SF") || upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") || upper.endsWith(".EC")
    }

    fun patch(shell: File, out: File, spec: QuickBuildSpec): ShellPatchReport {
        spec.validationError()?.let { throw IllegalArgumentException(it) }
        var replacedStrings = 0
        var replacedIcons = 0
        out.parentFile?.mkdirs()
        ZipFile(shell).use { zip ->
            AlignedZipWriter(out).use { w ->
                val entries = zip.entries().toList()
                require(entries.any { it.name == "AndroidManifest.xml" }) { "The shell APK has no AndroidManifest.xml" }
                for (e in entries) {
                    val name = e.name
                    if (e.isDirectory || isOldSignature(name) || name.startsWith(WWW) || name == CONFIG_PATH) continue
                    val bytes = zip.getInputStream(e).use { it.readBytes() }
                    val stored = e.method == ZipEntry.STORED
                    when {
                        name == "AndroidManifest.xml" -> {
                            val ax = Axml.parse(bytes)
                            replacedStrings += ax.replacePackagePrefix(PLACEHOLDER_PACKAGE, spec.packageName)
                            replacedStrings += ax.replaceString(PLACEHOLDER_LABEL, spec.appName)
                            replacedStrings += ax.replaceString(PLACEHOLDER_VERSION_NAME, spec.versionName)
                            ax.setIntAttribute("manifest", "versionCode", spec.versionCode)
                            check(ax.attributeString("manifest", "package") == spec.packageName) {
                                "The shell manifest doesn't use the placeholder package; can't rename it"
                            }
                            w.add(name, ax.toByteArray(), stored = false)
                        }
                        name.startsWith("res/") && name.endsWith(".png") -> {
                            val size = IconSpec.pngSize(bytes)
                            val density = size?.takeIf { it.first == it.second }?.let { Density.forLegacyPx(it.first) }
                            val replacement = density?.let { spec.icons[it] }
                            if (replacement != null) replacedIcons++
                            w.add(name, replacement ?: bytes, stored)
                        }
                        else -> w.add(name, bytes, stored)
                    }
                }
                spec.webFiles.toSortedMap().forEach { (path, bytes) ->
                    val clean = path.replace('\\', '/').trimStart('/')
                    require(!clean.split('/').contains("..")) { "Bad web file path: $path" }
                    w.add(WWW + clean, bytes, stored = false)
                }
                w.add(CONFIG_PATH, configJson(spec).toByteArray(), stored = false)
            }
        }
        check(replacedStrings >= 2) { "The shell's placeholders were not found (got $replacedStrings replacements)" }
        return ShellPatchReport(replacedStrings, replacedIcons, spec.webFiles.size)
    }
}

/**
 * ZipOutputStream wrapper that starts the data of STORED entries on a 4-byte boundary
 * (zipalign-compatible, using the 0xD935 alignment extra field like apksig does).
 */
class AlignedZipWriter(file: File) : AutoCloseable {
    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private val counter = Counting(file.outputStream().buffered())
    private val zip = ZipOutputStream(counter)

    fun add(name: String, data: ByteArray, stored: Boolean) {
        val e = ZipEntry(name)
        e.time = FIXED_TIME
        if (stored) {
            val crc = CRC32().apply { update(data) }
            e.method = ZipEntry.STORED
            e.size = data.size.toLong()
            e.compressedSize = data.size.toLong()
            e.crc = crc.value
            zip.flush()
            val nameLen = name.toByteArray(Charsets.UTF_8).size
            val headerEnd = counter.count + 30 + nameLen
            e.extra = alignmentExtra(headerEnd, if (name.endsWith(".so")) 4096 else 4)
        } else {
            e.method = ZipEntry.DEFLATED
        }
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }

    companion object {
        /** 2008-01-01, like aapt/zipalign, so output doesn't depend on the clock. */
        private const val FIXED_TIME = 1199145600000L

        /** Extra field (id 0xD935) whose length puts the entry data on an [align] boundary. */
        fun alignmentExtra(headerEndWithoutExtra: Long, align: Int): ByteArray {
            val minLen = 6
            val pad = ((align - ((headerEndWithoutExtra + minLen) % align)) % align).toInt()
            val len = minLen + pad
            val extra = ByteArray(len)
            extra[0] = 0x35; extra[1] = 0xD9.toByte()
            extra[2] = (len - 4).toByte(); extra[3] = ((len - 4) shr 8).toByte()
            extra[4] = align.toByte(); extra[5] = (align shr 8).toByte()
            return extra
        }
    }
}
