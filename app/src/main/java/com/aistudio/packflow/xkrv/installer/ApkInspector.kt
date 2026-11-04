package com.aistudio.packflow.xkrv.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class SignerInfo(
    val subject: String,
    val sha256: String,
    val sha1: String,
    val isDebugKey: Boolean,
)

data class PermissionEntry(val name: String, val sensitive: Boolean)

data class ApkInfo(
    val file: File,
    val fileName: String,
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val icon: Bitmap?,
    val permissions: List<PermissionEntry>,
    val signers: List<SignerInfo>,
    val fileSha256: String,
    val sizeBytes: Long,
)

class ApkInspectException(message: String) : Exception(message)

object ApkInspector {

    private val splitExtensions = listOf(".apks", ".xapk", ".apkm", ".aab")

    /** Copies the picked file into cache and reads it with the system's own APK parser. */
    suspend fun inspect(context: Context, uri: Uri): ApkInfo = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "selected.apk"
        ErrorLog.info("Picked $name")

        if (splitExtensions.any { name.lowercase().endsWith(it) }) {
            throw ApkInspectException(
                "$name is a split bundle or app bundle. Only single .apk files are supported right now."
            )
        }

        val dir = File(context.cacheDir, "incoming").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val apk = File(dir, "candidate.apk")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected file")
        input.use { src -> apk.outputStream().use { dst -> src.copyTo(dst) } }

        inspectCopiedFile(context, apk, name)
    }

    /** Copies an APK that PackFlow built (GitHub or quick build) into cache/incoming and inspects it. */
    suspend fun inspectFile(context: Context, file: File): ApkInfo = withContext(Dispatchers.IO) {
        ErrorLog.info("Opening built APK ${file.name}")
        val dir = File(context.cacheDir, "incoming").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val apk = File(dir, "candidate.apk")
        file.copyTo(apk, overwrite = true)
        inspectCopiedFile(context, apk, file.name)
    }

    /** Copies the app's own base APK into cache/incoming/candidate.apk for instant testing of the preview and install flow. */
    suspend fun inspectSampleApk(context: Context): ApkInfo = withContext(Dispatchers.IO) {
        val name = "sample-packflow-test.apk"
        ErrorLog.info("Loading built-in sample APK ($name)")
        val dir = File(context.cacheDir, "incoming").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val apk = File(dir, "candidate.apk")
        val sourceDir = context.applicationInfo.sourceDir
        if (sourceDir.isNullOrBlank() || !File(sourceDir).exists()) {
            throw ApkInspectException("Sample APK source was not accessible in this container.")
        }
        File(sourceDir).copyTo(apk, overwrite = true)
        inspectCopiedFile(context, apk, name)
    }

    fun deleteCachedCandidates(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, "incoming")
            val deletedCount = dir.listFiles()?.count { it.delete() } ?: 0
            if (deletedCount > 0) {
                ErrorLog.info("Deleted $deletedCount temporary APK file(s) from cache/incoming")
            }
        }
    }

    private fun inspectCopiedFile(context: Context, apk: File, name: String): ApkInfo {
        if (!looksLikeZip(apk)) {
            apk.delete()
            throw ApkInspectException("$name is not an APK (no ZIP header found).")
        }

        if (!containsAndroidManifest(apk)) {
            apk.delete()
            throw ApkInspectException("$name is a ZIP archive, not an APK (no AndroidManifest.xml found).")
        }

        val pm = context.packageManager
        val info = readArchive(pm, apk)
            ?: run {
                apk.delete()
                throw ApkInspectException("Android could not parse $name. It may be corrupt, unsigned, or built for a newer Android.")
            }

        val appInfo = info.applicationInfo?.apply {
            sourceDir = apk.path
            publicSourceDir = apk.path
        }
        val label = appInfo?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }
            ?: info.packageName
        val icon = appInfo?.let { runCatching { it.loadIcon(pm).toBitmapSafe() }.getOrNull() }

        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

        val permissions = (info.requestedPermissions ?: emptyArray()).sorted().map {
            PermissionEntry(it, isSensitive(pm, it))
        }

        val signers = readSigners(pm, apk, info).map { toSignerInfo(it) }
        if (signers.isEmpty()) ErrorLog.error("No signing certificate found in $name")

        return ApkInfo(
            file = apk,
            fileName = name,
            label = label,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            minSdk = if (Build.VERSION.SDK_INT >= 24) appInfo?.minSdkVersion else null,
            targetSdk = appInfo?.targetSdkVersion,
            icon = icon,
            permissions = permissions,
            signers = signers,
            fileSha256 = hex(digestFile(apk, "SHA-256")),
            sizeBytes = apk.length(),
        ).also { ErrorLog.info("Parsed ${it.packageName} ${it.versionName} (${it.versionCode})") }
    }

    private fun readArchive(pm: PackageManager, apk: File): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        return getArchiveInfo(pm, apk.path, flags)
    }

    private fun getArchiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, flags)
        }

    @Suppress("DEPRECATION")
    private fun readSigners(pm: PackageManager, apk: File, info: PackageInfo): List<Signature> {
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { s ->
                val list = if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
                if (!list.isNullOrEmpty()) return list.toList()
            }
        }
        // Fallback: some Android builds leave signingInfo null for archives.
        info.signatures?.takeIf { it.isNotEmpty() }?.let { return it.toList() }
        val legacy = getArchiveInfo(pm, apk.path, PackageManager.GET_SIGNATURES)
        return legacy?.signatures?.toList() ?: emptyList()
    }

    private fun toSignerInfo(sig: Signature): SignerInfo {
        val bytes = sig.toByteArray()
        val subject = runCatching {
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            cert.subjectX500Principal.name
        }.getOrDefault("(unreadable certificate)")
        return SignerInfo(
            subject = subject,
            sha256 = hex(MessageDigest.getInstance("SHA-256").digest(bytes)),
            sha1 = hex(MessageDigest.getInstance("SHA-1").digest(bytes)),
            isDebugKey = subject.contains("CN=Android Debug", ignoreCase = true),
        )
    }

    private fun isSensitive(pm: PackageManager, name: String): Boolean = runCatching {
        val p = pm.getPermissionInfo(name, 0)
        val base = if (Build.VERSION.SDK_INT >= 28) p.protection
        else @Suppress("DEPRECATION") (p.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        base == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    private fun looksLikeZip(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val h = ByteArray(4)
            s.read(h) == 4 && h[0] == 0x50.toByte() && h[1] == 0x4B.toByte()
        }
    }.getOrDefault(false)

    private fun containsAndroidManifest(f: File): Boolean = runCatching {
        ZipFile(f).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null
        }
    }.getOrDefault(false)

    private fun digestFile(f: File, algo: String): ByteArray {
        val md = MessageDigest.getInstance(algo)
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest()
    }

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    private fun Drawable.toBitmapSafe(fallback: Int = 192): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else fallback
        val h = if (intrinsicHeight > 0) intrinsicHeight else fallback
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, w, h)
        draw(canvas)
        return bmp
    }
}
