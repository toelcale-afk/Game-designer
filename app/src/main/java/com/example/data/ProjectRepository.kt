package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.ProjectLayout
import com.example.core.ProjectMeta
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectInfo(val dir: File, val meta: ProjectMeta, val fileCount: Int, val sizeBytes: Long, val lastModified: Long)

/**
 * Game projects in PackFlow's private storage (filesDir/projects/<slug>/).
 * Every operation is checked to stay inside the projects folder. Blocking: use Dispatchers.IO.
 */
class ProjectRepository(context: Context) {
    private val app = context.applicationContext
    val root: File = File(app.filesDir, "projects").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("projects", Context.MODE_PRIVATE)

    // ---------- projects ----------

    fun list(): List<ProjectInfo> = root.listFiles()?.filter { it.isDirectory }?.map { info(it) }
        ?.sortedByDescending { it.lastModified } ?: emptyList()

    fun info(dir: File): ProjectInfo {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        return ProjectInfo(
            dir = dir,
            meta = readMeta(dir),
            fileCount = files.size,
            sizeBytes = files.sumOf { it.length() },
            lastModified = files.maxOfOrNull { it.lastModified() } ?: dir.lastModified()
        )
    }

    fun readMeta(dir: File): ProjectMeta {
        val f = File(dir, ProjectLayout.META)
        return if (f.exists()) ProjectMeta.fromJson(f.readText(), dir.name) else ProjectMeta(name = dir.name)
    }

    fun writeMeta(dir: File, meta: ProjectMeta) {
        File(dir, ProjectLayout.META).writeText(meta.toJson())
    }

    /** Creates a project with all standard folders. [starterFiles] are project-relative paths. */
    fun create(name: String, template: String = "blank", starterFiles: Map<String, ByteArray>? = null): File {
        val clean = name.trim().ifBlank { "My Game" }
        val dir = uniqueChild(root, ProjectLayout.slug(clean))
        dir.mkdirs()
        ProjectLayout.FOLDERS.forEach { File(dir, it).mkdirs() }
        File(dir, ProjectLayout.DATA).mkdirs()
        writeMeta(dir, ProjectMeta(name = clean, template = template, createdAt = System.currentTimeMillis()))
        val files = starterFiles ?: mapOf(
            "code/index.html" to ProjectLayout.blankIndexHtml(clean).toByteArray(),
            "code/main.js" to ProjectLayout.BLANK_MAIN_JS.toByteArray()
        )
        files.forEach { (rel, bytes) ->
            require(ProjectLayout.isSafeRelative(rel)) { "Bad starter path $rel" }
            File(dir, rel).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        return dir
    }

    var currentProject: File?
        get() = prefs.getString("current", null)?.let { File(it) }?.takeIf { it.isDirectory && isInside(it, root) }
        set(value) {
            prefs.edit().putString("current", value?.absolutePath).apply()
        }

    // ---------- files ----------

    fun isInside(file: File, parent: File): Boolean {
        val f = file.canonicalFile
        val p = parent.canonicalFile
        return f == p || f.path.startsWith(p.path + File.separator)
    }

    private fun requireInRoot(file: File) {
        if (!isInside(file, root) || file.canonicalFile == root.canonicalFile) throw IOException("Outside the projects folder: ${file.name}")
    }

    fun children(dir: File): List<File> = (dir.listFiles() ?: emptyArray())
        .filterNot { it.name.startsWith(".") }
        .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))

    fun rename(file: File, newName: String): File {
        requireInRoot(file)
        val safe = ProjectLayout.safeName(newName) ?: throw IOException("'$newName' isn't a valid name")
        val target = File(file.parentFile, safe)
        if (target.exists()) throw IOException("$safe already exists")
        if (!file.renameTo(target)) throw IOException("Couldn't rename ${file.name}")
        return target
    }

    fun move(file: File, destDir: File): File {
        requireInRoot(file)
        requireInRoot(destDir)
        if (file.isDirectory && isInside(destDir, file)) throw IOException("Can't move a folder into itself")
        val target = uniqueChild(destDir, file.name)
        if (!file.renameTo(target)) {
            file.copyRecursively(target)
            file.deleteRecursively()
        }
        return target
    }

    fun delete(file: File) {
        requireInRoot(file)
        if (!file.deleteRecursively()) throw IOException("Couldn't delete ${file.name}")
    }

    fun newFolder(parent: File, name: String): File {
        requireInRoot(parent)
        val safe = ProjectLayout.safeName(name) ?: throw IOException("'$name' isn't a valid name")
        return uniqueChild(parent, safe).apply { mkdirs() }
    }

    fun writeText(file: File, text: String) {
        requireInRoot(file)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    /** Copies a picked document into [destDir], keeping its name (adds " (2)" on clashes). */
    fun importUri(uri: Uri, destDir: File): File {
        requireInRoot(destDir)
        val name = ProjectLayout.safeName(displayName(uri) ?: "imported_${System.currentTimeMillis()}") ?: "imported"
        destDir.mkdirs()
        val target = uniqueChild(destDir, name)
        app.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Couldn't open the picked file")
        return target
    }

    fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Saves bytes as a new file in a project folder (asset generator, builds…). */
    fun saveNew(dir: File, name: String, bytes: ByteArray): File {
        dir.mkdirs()
        requireInRoot(dir)
        val safe = ProjectLayout.safeName(name) ?: "file"
        return uniqueChild(dir, safe).apply { writeBytes(bytes) }
    }

    /** Overwrites (or creates) a project file. */
    fun saveReplacing(dir: File, name: String, bytes: ByteArray): File {
        dir.mkdirs()
        requireInRoot(dir)
        val safe = ProjectLayout.safeName(name) ?: "file"
        return File(dir, safe).apply { writeBytes(bytes) }
    }

    /** Every file of a project as project-relative path -> bytes (skips builds/). */
    fun readAll(project: File, skipBuilds: Boolean = true): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        project.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(project).invariantSeparatorsPath
            if (skipBuilds && rel.startsWith(ProjectLayout.BUILDS + "/")) return@forEach
            out[rel] = f.readBytes()
        }
        return out
    }

    /** The project's web root (code/ + asset folders) for previews and builds. */
    fun webFiles(project: File): Map<String, ByteArray> = ProjectLayout.webFiles(readAll(project))

    // ---------- zip ----------

    fun exportZip(project: File, out: OutputStream, includeBuilds: Boolean = false) {
        ZipOutputStream(out.buffered()).use { zip ->
            project.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(project).invariantSeparatorsPath
                if (!includeBuilds && rel.startsWith(ProjectLayout.BUILDS + "/")) return@forEach
                zip.putNextEntry(ZipEntry("${project.name}/$rel"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Imports a project zip (with or without a top folder). Returns the new project folder. */
    fun importZip(uri: Uri): File {
        val tmp = File(app.cacheDir, "import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var total = 0L
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name.replace('\\', '/').trimStart('/')
                        if (e.isDirectory || !ProjectLayout.isSafeRelative(name)) continue
                        val target = File(tmp, name)
                        if (!isInside(target, tmp)) continue
                        target.parentFile?.mkdirs()
                        target.outputStream().use { o ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_IMPORT_BYTES) throw IOException("Zip is larger than 500 MB")
                                o.write(buf, 0, n)
                            }
                        }
                    }
                }
            } ?: throw IOException("Couldn't open the zip")
            // Single top-level folder? Use it as the project root.
            val tops = tmp.listFiles().orEmpty()
            val content = if (tops.size == 1 && tops[0].isDirectory) tops[0] else tmp
            val meta = File(content, ProjectLayout.META).takeIf { it.exists() }
                ?.let { ProjectMeta.fromJson(it.readText(), content.name) }
            val name = meta?.name ?: (displayName(uri)?.substringBeforeLast('.') ?: "Imported project")
            val dir = uniqueChild(root, ProjectLayout.slug(name))
            content.copyRecursively(dir)
            ProjectLayout.FOLDERS.forEach { File(dir, it).mkdirs() }
            if (meta == null) writeMeta(dir, ProjectMeta(name = name, createdAt = System.currentTimeMillis()))
            return dir
        } finally {
            tmp.deleteRecursively()
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 500L * 1024 * 1024

        fun uniqueChild(dir: File, name: String): File {
            var f = File(dir, name)
            if (!f.exists()) return f
            val base = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.') && !name.startsWith(".")) "." + name.substringAfterLast('.') else ""
            var i = 2
            while (f.exists()) {
                f = File(dir, "$base ($i)$ext")
                i++
            }
            return f
        }
    }
}
