package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.IconDesign
import com.example.core.IconShape
import com.example.core.IconSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRendererTest {

    private fun source(): Bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @Test
    fun rendersEveryTargetAtTheRightSize() {
        val pngs = IconRenderer(IconDesign(sourcePath = "x"), source(), null).renderAll()
        IconSpec.allPngTargets().forEach { (path, px) ->
            val bytes = pngs[path] ?: error("missing $path")
            assertEquals(path, px to px, IconSpec.pngSize(bytes))
        }
    }

    @Test
    fun roundIconHasTransparentCorners() {
        val r = IconRenderer(IconDesign(sourcePath = "x"), source(), null)
        val round = r.masked(96, IconShape.CIRCLE)
        assertEquals(0, Color.alpha(round.getPixel(0, 0)))
        assertTrue(Color.alpha(round.getPixel(48, 48)) > 200)
    }

    @Test
    fun foregroundLayerLeavesBackgroundTransparentAroundSmallImage() {
        val tiny = IconDesign(sourcePath = "x", transform = com.example.core.IconTransform(scale = 0.3f))
        val fg = IconRenderer(tiny, source(), null).foregroundLayer(108)
        assertEquals(0, Color.alpha(fg.getPixel(2, 2)))
        assertEquals(Color.RED, fg.getPixel(54, 54))
    }
}
