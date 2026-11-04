package com.example.core

/** Kinds of files the project browser can preview. */
enum class FileKind { IMAGE, AUDIO, TEXT, OTHER }

/** project.json: the project's identity and build settings. */
data class ProjectMeta(
    val name: String,
    val appName: String = name,
    val packageName: String = ShellApk.suggestPackageName(name),
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val template: String = "blank",
    val createdAt: Long = 0L
) {
    fun toJson(): String = Json.stringify(
        mapOf(
            "name" to name, "appName" to appName, "packageName" to packageName,
            "versionName" to versionName, "versionCode" to versionCode,
            "template" to template, "createdAt" to createdAt, "format" to 1
        ),
        indent = 2
    )

    companion object {
        fun fromJson(text: String, fallbackName: String): ProjectMeta {
            val m = runCatching { Json.parseObject(text) }.getOrDefault(emptyMap())
            val name = m.str("name", fallbackName).ifBlank { fallbackName }
            return ProjectMeta(
                name = name,
                appName = m.str("appName", name).ifBlank { name },
                packageName = m.str("packageName").takeIf { ShellApk.isValidPackageName(it) } ?: ShellApk.suggestPackageName(name),
                versionName = m.str("versionName", "1.0"),
                versionCode = m.int("versionCode", 1).coerceAtLeast(1),
                template = m.str("template", "blank"),
                createdAt = m.long("createdAt")
            )
        }
    }
}

/**
 * Folder layout of a game project and how it maps to the files a built app serves.
 *
 * ```
 * <project>/project.json
 *           code/      HTML/JS/CSS (index.html is the entry); code/data/ holds designer JSON
 *           sprites/ tiles/ levels/ sounds/ music/ voice/   assets
 *           icon/      icon maker design + generated launcher icons
 *           builds/    APKs and ZIPs PackFlow produced
 * ```
 * In the app, code/ becomes the web root and each asset folder keeps its name, so game code
 * refers to `sprites/marine.png`, `levels/level1.json`, `sounds/laser.wav`.
 */
object ProjectLayout {
    const val META = "project.json"
    const val CODE = "code"
    const val ICON = "icon"
    const val BUILDS = "builds"
    const val DATA = "code/data"
    val ASSET_DIRS = listOf("sprites", "tiles", "levels", "sounds", "music", "voice")
    val FOLDERS = listOf(CODE) + ASSET_DIRS + listOf(ICON, BUILDS)

    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    private val AUDIO = setOf("wav", "mp3", "ogg", "m4a", "aac", "flac", "opus")
    private val TEXT = setOf("html", "htm", "js", "mjs", "css", "json", "txt", "md", "xml", "kt", "java", "jsx", "tsx", "ts", "csv", "svg", "yml", "yaml", "glsl")

    fun kindOf(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE -> FileKind.IMAGE
        in AUDIO -> FileKind.AUDIO
        in TEXT -> FileKind.TEXT
        else -> FileKind.OTHER
    }

    /** Folder-safe name: "Space Marines: Act 1" -> "space-marines-act-1". */
    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "project" }

    /** A file or folder name the user typed, made safe (no separators, no dot-dot). */
    fun safeName(name: String): String? {
        val t = name.trim().replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
        return t.takeIf { it.isNotEmpty() && it != "." && it != ".." && t.length <= 120 }
    }

    /** True if [relative] stays inside the project (no absolute paths, no "..", no empty parts). */
    fun isSafeRelative(relative: String): Boolean {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains('\\')) return false
        return relative.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /**
     * Project-relative path -> path inside the built app's web root, or null if the file
     * isn't shipped (icon/, builds/, project.json, hidden files).
     */
    fun webPathFor(relative: String): String? {
        if (!isSafeRelative(relative)) return null
        if (relative.split('/').any { it.startsWith(".") }) return null
        val top = relative.substringBefore('/')
        return when {
            top == CODE && relative.length > CODE.length + 1 -> relative.substring(CODE.length + 1)
            top in ASSET_DIRS && relative.contains('/') -> relative
            else -> null
        }
    }

    /** Web-root path -> project-relative path (inverse of [webPathFor]); null if unsafe. */
    fun projectPathForWeb(webPath: String): String? {
        val clean = webPath.trimStart('/')
        if (!isSafeRelative(clean)) return null
        return if (clean.substringBefore('/') in ASSET_DIRS && clean.contains('/')) clean else "$CODE/$clean"
    }

    /** Maps project files to web-root files; the first file wins on a clash. */
    fun webFiles(projectFiles: Map<String, ByteArray>): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        projectFiles.keys.sorted().forEach { rel ->
            webPathFor(rel)?.let { web -> if (web !in out) out[web] = projectFiles.getValue(rel) }
        }
        return out
    }

    fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "m4a", "aac" -> "audio/mp4"
        "txt", "md", "csv" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** Starter page for a blank project. */
    fun blankIndexHtml(title: String): String = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
  <title>${GeneratedProjectFiles.xmlEscape(title)}</title>
  <style>
    html, body { margin: 0; height: 100%; background: #0B1120; color: #F1F5F9; font-family: sans-serif; }
    canvas { display: block; width: 100vw; height: 100vh; touch-action: none; }
  </style>
</head>
<body>
  <canvas id="game"></canvas>
  <script src="main.js"></script>
</body>
</html>
"""

    const val BLANK_MAIN_JS = """// Your game starts here. Assets live next to code/: sprites/, sounds/, levels/ ...
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
function resize() { canvas.width = innerWidth * devicePixelRatio; canvas.height = innerHeight * devicePixelRatio; }
addEventListener('resize', resize); resize();
let t = 0;
function frame() {
  t += 1 / 60;
  ctx.fillStyle = '#0B1120';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#34D399';
  const r = 30 * devicePixelRatio;
  ctx.beginPath();
  ctx.arc(canvas.width / 2 + Math.cos(t) * 80 * devicePixelRatio, canvas.height / 2, r, 0, Math.PI * 2);
  ctx.fill();
  requestAnimationFrame(frame);
}
frame();
"""
}
