package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.IconDesign
import com.example.core.IconShape
import com.example.core.IconSpec
import com.example.core.IconTransform
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IconMakerUi(
    val design: IconDesign = IconDesign(),
    val source: Bitmap? = null,
    val backgroundImage: Bitmap? = null,
    /** Generated files (res paths -> PNG). Empty means "use the default icon". */
    val pngs: Map<String, ByteArray> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    /** Bumps whenever the design changes, so previews re-render. */
    val revision: Int = 0
) {
    val hasIcon: Boolean get() = pngs.isNotEmpty()
    val isStale: Boolean get() = source != null && pngs.isEmpty()
}

/**
 * Pipeline step 2. Keeps the icon design for the current project in [iconDir]
 * (icon.json + source images + generated PNGs) so it survives restarts.
 */
class IconMakerController(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(IconMakerUi())
    val state: StateFlow<IconMakerUi> = _state.asStateFlow()

    private var iconDir: File? = null

    /** Loads the saved design from [dir] (a project's icon/ folder, or PackFlow's scratch folder). */
    fun load(dir: File) {
        iconDir = dir
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                dir.mkdirs()
                val json = File(dir, DESIGN_FILE)
                val design = if (json.exists()) runCatching { IconDesign.fromJson(json.readText()) }.getOrDefault(IconDesign()) else IconDesign()
                val src = design.sourcePath?.let { IconRenderer.decodeFile(resolve(dir, it)) }
                val bg = design.backgroundImagePath?.let { IconRenderer.decodeFile(resolve(dir, it)) }
                val pngs = readGenerated(dir)
                IconMakerUi(design = design, source = src, backgroundImage = bg, pngs = pngs)
            }
            _state.value = loaded
        }
    }

    private fun resolve(dir: File, path: String): File = File(path).let { if (it.isAbsolute) it else File(dir, path) }

    private fun readGenerated(dir: File): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val gen = File(dir, GENERATED_DIR)
        if (!gen.isDirectory) return out
        IconSpec.allPngTargets().forEach { (path, _) ->
            val f = File(gen, path)
            if (f.exists()) out[path] = f.readBytes()
        }
        return out
    }

    fun pickSource(context: Context, uri: Uri) = importInto(context, uri, isBackground = false)

    fun pickBackgroundImage(context: Context, uri: Uri) = importInto(context, uri, isBackground = true)

    /** Uses an image file that's already in the project (sprites/, icon/ ...). */
    fun useProjectImage(file: File) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { IconRenderer.decodeFile(file) }
            if (bmp == null) {
                _state.update { it.copy(message = "Couldn't read ${file.name} as an image") }
                return@launch
            }
            change { it.copy(design = it.design.copy(sourcePath = file.absolutePath, transform = IconTransform()), source = bmp) }
        }
    }

    private fun importInto(context: Context, uri: Uri, isBackground: Boolean) {
        val dir = iconDir ?: return
        scope.launch {
            try {
                val name = if (isBackground) "background_source.img" else "icon_source.img"
                val file = withContext(Dispatchers.IO) { IconRenderer.importImage(context, uri, File(dir, name)) }
                val bmp = withContext(Dispatchers.IO) { IconRenderer.decodeFile(file) }
                    ?: throw IllegalArgumentException("That file isn't an image Android can read")
                change {
                    if (isBackground) it.copy(design = it.design.copy(backgroundImagePath = name), backgroundImage = bmp)
                    else it.copy(design = it.design.copy(sourcePath = name, transform = IconTransform()), source = bmp)
                }
            } catch (t: Throwable) {
                ErrorLog.error("Icon image import failed", t)
                _state.update { it.copy(message = t.message ?: "Import failed") }
            }
        }
    }

    fun clearBackgroundImage() = change { it.copy(design = it.design.copy(backgroundImagePath = null), backgroundImage = null) }

    fun setBackgroundColor(argb: Long) = change { it.copy(design = it.design.copy(backgroundColor = argb)) }

    fun setTransform(t: IconTransform) = change { it.copy(design = it.design.copy(transform = t.clamped())) }

    fun setPreviewShape(shape: IconShape) {
        _state.update { it.copy(design = it.design.copy(previewShape = shape)) }
        saveDesign()
    }

    /** Any change to the look invalidates the generated files until "Generate" runs again. */
    private fun change(f: (IconMakerUi) -> IconMakerUi) {
        _state.update { f(it).copy(pngs = emptyMap(), revision = it.revision + 1, message = null) }
        saveDesign()
    }

    private fun saveDesign() {
        val dir = iconDir ?: return
        val design = _state.value.design
        scope.launch(Dispatchers.IO) {
            runCatching { File(dir, DESIGN_FILE).writeText(design.toJson()) }
        }
    }

    /** Renders every launcher icon size and saves them under icon/generated/. */
    fun generate(onDone: (() -> Unit)? = null) {
        val s = _state.value
        if (s.source == null) {
            _state.update { it.copy(message = "Pick an image first") }
            return
        }
        val dir = iconDir
        _state.update { it.copy(busy = true, message = "Rendering icons…") }
        scope.launch {
            try {
                val pngs = withContext(Dispatchers.Default) { IconRenderer(s.design, s.source, s.backgroundImage).renderAll() }
                if (dir != null) withContext(Dispatchers.IO) {
                    val gen = File(dir, GENERATED_DIR)
                    gen.deleteRecursively()
                    pngs.forEach { (path, bytes) -> File(gen, path).apply { parentFile?.mkdirs() }.writeBytes(bytes) }
                }
                _state.update { it.copy(busy = false, pngs = pngs, message = "${pngs.size} icon files ready (all densities + adaptive layers)") }
                onDone?.invoke()
            } catch (t: Throwable) {
                ErrorLog.error("Icon rendering failed", t)
                _state.update { it.copy(busy = false, message = "Rendering failed: ${t.message}") }
            }
        }
    }

    /** Adaptive XML files to add next to the PNGs (only when an icon was generated). */
    fun xmlFiles(): Map<String, String> = if (_state.value.hasIcon) IconSpec.xmlFiles() else emptyMap()

    /** PNGs for the Android project (the store icon goes to icon/, outside res/). */
    fun projectPngs(): Map<String, ByteArray> = _state.value.pngs

    companion object {
        const val DESIGN_FILE = "icon.json"
        const val GENERATED_DIR = "generated"
    }
}
