package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import com.example.core.Density
import com.example.core.IconDesign
import com.example.core.IconShape
import com.example.core.IconSpec
import com.example.core.PxRect
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders launcher icons from an [IconDesign]: legacy square/round PNGs for every density,
 * adaptive foreground/background layers, and the 512 px store icon.
 */
class IconRenderer(
    private val design: IconDesign,
    private val source: Bitmap?,
    private val backgroundImage: Bitmap?
) {
    private val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun PxRect.toRectF() = RectF(left, top, right, bottom)

    private fun drawBackground(canvas: Canvas, size: Float) {
        canvas.drawColor(design.backgroundColor.toInt())
        backgroundImage?.let { bg ->
            canvas.drawBitmap(bg, null, IconSpec.coverRect(size, bg.width, bg.height).toRectF(), filter)
        }
    }

    private fun drawSource(canvas: Canvas, frame: PxRect) {
        val src = source ?: return
        canvas.drawBitmap(src, null, IconSpec.imageRect(frame, src.width, src.height, design.transform).toRectF(), filter)
    }

    fun foregroundLayer(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawSource(Canvas(bmp), IconSpec.adaptiveVisibleFrame(px.toFloat()))
        return bmp
    }

    fun backgroundLayer(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawBackground(Canvas(bmp), px.toFloat())
        return bmp
    }

    /** What a launcher shows: background + image filling the visible square (no mask). */
    fun visible(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, px.toFloat())
        drawSource(canvas, PxRect(0f, 0f, px.toFloat(), px.toFloat()))
        return bmp
    }

    /** [visible] masked to [shape] with smooth edges. */
    fun masked(px: Int, shape: IconShape): Bitmap {
        val content = visible(px)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(shapePath(shape, px.toFloat()), maskPaint)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(content, 0f, 0f, maskPaint)
        content.recycle()
        return out
    }

    /** All icon files for a generated project: res paths -> PNG bytes. */
    fun renderAll(): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        Density.entries.forEach { d ->
            out[IconSpec.legacyPath(d)] = masked(d.legacyPx, IconShape.ROUNDED).toPng()
            out[IconSpec.legacyRoundPath(d)] = masked(d.legacyPx, IconShape.CIRCLE).toPng()
            out[IconSpec.foregroundPath(d)] = foregroundLayer(d.adaptivePx).toPng()
            out[IconSpec.backgroundPath(d)] = backgroundLayer(d.adaptivePx).toPng()
        }
        out[IconSpec.PLAY_STORE_PATH] = visible(IconSpec.PLAY_STORE_PX).toPng()
        return out
    }

    companion object {
        fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
            val r = RectF(0f, 0f, size, size)
            when (shape) {
                IconShape.CIRCLE -> addOval(r, Path.Direction.CW)
                IconShape.SQUIRCLE -> addRoundRect(r, size * 0.32f, size * 0.32f, Path.Direction.CW)
                IconShape.ROUNDED -> addRoundRect(r, size * 0.18f, size * 0.18f, Path.Direction.CW)
                IconShape.SQUARE -> addRect(r, Path.Direction.CW)
                IconShape.TEARDROP -> {
                    val big = size / 2f
                    val small = size * 0.1f
                    addRoundRect(r, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
                }
            }
        }

        fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { bos ->
            compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }

        /** Decodes an image no larger than [maxPx] on its longer side (keeps memory small). */
        fun decodeFile(file: File, maxPx: Int = 1024): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }
            return BitmapFactory.decodeFile(file.path, opts)
        }

        fun sampleSize(w: Int, h: Int, maxPx: Int): Int {
            var s = 1
            while (w / (s * 2) >= maxPx || h / (s * 2) >= maxPx) s *= 2
            return s
        }

        /** Copies a picked image into private storage so the design keeps working later. */
        fun importImage(context: Context, uri: Uri, dest: File): File {
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalArgumentException("Could not open the picked image")
            return dest
        }
    }
}
