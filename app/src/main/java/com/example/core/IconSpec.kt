package com.example.core

/** One Android screen density bucket. */
enum class Density(val folder: String, val factor: Float) {
    MDPI("mdpi", 1f),
    HDPI("hdpi", 1.5f),
    XHDPI("xhdpi", 2f),
    XXHDPI("xxhdpi", 3f),
    XXXHDPI("xxxhdpi", 4f);

    /** Legacy launcher icon: 48 dp. */
    val legacyPx: Int get() = (48 * factor).toInt()

    /** Adaptive icon layer: 108 dp. */
    val adaptivePx: Int get() = (108 * factor).toInt()

    companion object {
        /** Density whose legacy icon is [px] wide, if any (used to match icons inside a prebuilt APK). */
        fun forLegacyPx(px: Int): Density? = entries.firstOrNull { it.legacyPx == px }
    }
}

/** Axis-aligned rectangle in pixels. */
data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * How the picked image sits in the icon. scale = 1 means the image just covers the visible
 * icon area (shorter side fills it); offsets are fractions of the visible area (0.5 = half).
 */
data class IconTransform(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    fun clamped(): IconTransform = copy(
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
        offsetX = offsetX.coerceIn(-1.5f, 1.5f),
        offsetY = offsetY.coerceIn(-1.5f, 1.5f)
    )

    companion object {
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 4f
    }
}

enum class IconShape(val label: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    ROUNDED("Rounded"),
    SQUARE("Square"),
    TEARDROP("Teardrop")
}

/** Everything the icon maker needs to render an icon. Saved as icon/icon.json in a project. */
data class IconDesign(
    val sourcePath: String? = null,
    val backgroundColor: Long = 0xFF0B1120,
    val backgroundImagePath: String? = null,
    val transform: IconTransform = IconTransform(),
    val previewShape: IconShape = IconShape.CIRCLE
) {
    fun toJson(): String = Json.stringify(
        mapOf(
            "source" to sourcePath,
            "backgroundColor" to String.format("#%08X", backgroundColor and 0xFFFFFFFFL),
            "backgroundImage" to backgroundImagePath,
            "scale" to transform.scale.toDouble(),
            "offsetX" to transform.offsetX.toDouble(),
            "offsetY" to transform.offsetY.toDouble(),
            "shape" to previewShape.name
        ),
        indent = 2
    )

    companion object {
        fun fromJson(text: String): IconDesign {
            val m = Json.parseObject(text)
            return IconDesign(
                sourcePath = m["source"] as? String,
                backgroundColor = IconSpec.parseColor(m.str("backgroundColor")) ?: 0xFF0B1120,
                backgroundImagePath = m["backgroundImage"] as? String,
                transform = IconTransform(
                    m.dbl("scale", 1.0).toFloat(),
                    m.dbl("offsetX").toFloat(),
                    m.dbl("offsetY").toFloat()
                ).clamped(),
                previewShape = runCatching { IconShape.valueOf(m.str("shape")) }.getOrDefault(IconShape.CIRCLE)
            )
        }
    }
}

object IconSpec {
    const val RES = "app/src/main/res"

    /** Of a 108 dp adaptive layer, launchers show the middle 72 dp. */
    const val ADAPTIVE_VISIBLE_FRACTION = 72f / 108f

    fun legacyPath(d: Density) = "$RES/mipmap-${d.folder}/ic_launcher.png"
    fun legacyRoundPath(d: Density) = "$RES/mipmap-${d.folder}/ic_launcher_round.png"
    fun foregroundPath(d: Density) = "$RES/mipmap-${d.folder}/ic_launcher_foreground.png"
    fun backgroundPath(d: Density) = "$RES/mipmap-${d.folder}/ic_launcher_background.png"
    const val ADAPTIVE_XML_PATH = "$RES/mipmap-anydpi-v26/ic_launcher.xml"
    const val ADAPTIVE_ROUND_XML_PATH = "$RES/mipmap-anydpi-v26/ic_launcher_round.xml"
    const val PLAY_STORE_PATH = "icon/ic_launcher-playstore.png"
    const val PLAY_STORE_PX = 512

    const val ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""

    /** Every PNG path the generator writes, with its pixel size. */
    fun allPngTargets(): List<Pair<String, Int>> = Density.entries.flatMap { d ->
        listOf(
            legacyPath(d) to d.legacyPx,
            legacyRoundPath(d) to d.legacyPx,
            foregroundPath(d) to d.adaptivePx,
            backgroundPath(d) to d.adaptivePx
        )
    } + (PLAY_STORE_PATH to PLAY_STORE_PX)

    fun xmlFiles(): Map<String, String> = mapOf(ADAPTIVE_XML_PATH to ADAPTIVE_XML, ADAPTIVE_ROUND_XML_PATH to ADAPTIVE_XML)

    /**
     * Where to draw an image of [imgW] x [imgH] so it follows [t] inside a visible square
     * [frame] (pixels). The result may extend outside the frame (it gets clipped).
     */
    fun imageRect(frame: PxRect, imgW: Int, imgH: Int, t: IconTransform): PxRect {
        require(imgW > 0 && imgH > 0) { "Image has no pixels" }
        val coverScale = maxOf(frame.width / imgW, frame.height / imgH) * t.scale
        val w = imgW * coverScale
        val h = imgH * coverScale
        val cx = frame.left + frame.width / 2f + t.offsetX * frame.width
        val cy = frame.top + frame.height / 2f + t.offsetY * frame.height
        return PxRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Rectangle that "covers" a square canvas of [size] with an image (for background images). */
    fun coverRect(size: Float, imgW: Int, imgH: Int): PxRect =
        imageRect(PxRect(0f, 0f, size, size), imgW, imgH, IconTransform())

    /** The visible part of an adaptive layer of [layerPx] pixels. */
    fun adaptiveVisibleFrame(layerPx: Float): PxRect {
        val inset = layerPx * (1f - ADAPTIVE_VISIBLE_FRACTION) / 2f
        return PxRect(inset, inset, layerPx - inset, layerPx - inset)
    }

    /** "#RRGGBB" or "#AARRGGBB" -> ARGB; null if invalid. */
    fun parseColor(text: String): Long? {
        val hex = text.trim().removePrefix("#")
        val v = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> 0xFF000000L or v
            8 -> v
            else -> null
        }
    }

    /** Reads width/height from a PNG header, or null if the bytes aren't a PNG. */
    fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in sig.indices) if (bytes[i] != sig[i]) return null
        fun int(at: Int) = (bytes[at].toInt() and 0xff shl 24) or (bytes[at + 1].toInt() and 0xff shl 16) or
            (bytes[at + 2].toInt() and 0xff shl 8) or (bytes[at + 3].toInt() and 0xff)
        return int(16) to int(20)
    }
}
