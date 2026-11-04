package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSpecTest {
    @Test
    fun densitySizesMatchAndroidGuidelines() {
        assertEquals(listOf(48, 72, 96, 144, 192), Density.entries.map { it.legacyPx })
        assertEquals(listOf(108, 162, 216, 324, 432), Density.entries.map { it.adaptivePx })
        assertEquals(Density.XXHDPI, Density.forLegacyPx(144))
        assertNull(Density.forLegacyPx(100))
    }

    @Test
    fun coverFitFillsShorterSide() {
        val r = IconSpec.imageRect(PxRect(0f, 0f, 100f, 100f), 200, 100, IconTransform())
        assertEquals(100f, r.height, 0.001f)
        assertEquals(200f, r.width, 0.001f)
        assertEquals(-50f, r.left, 0.001f)
    }

    @Test
    fun offsetAndScaleMoveImage() {
        val r = IconSpec.imageRect(PxRect(0f, 0f, 100f, 100f), 50, 50, IconTransform(scale = 0.5f, offsetX = 0.25f))
        assertEquals(50f, r.width, 0.001f)
        assertEquals(50f, r.left, 0.001f) // centre moved from 50 to 75
    }

    @Test
    fun adaptiveVisibleFrameIsMiddleTwoThirds() {
        val f = IconSpec.adaptiveVisibleFrame(108f)
        assertEquals(18f, f.left, 0.001f)
        assertEquals(72f, f.width, 0.001f)
    }

    @Test
    fun targetsCoverEveryDensityAndStoreIcon() {
        val t = IconSpec.allPngTargets()
        assertEquals(Density.entries.size * 4 + 1, t.size)
        assertTrue(t.any { it.first == "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" && it.second == 432 })
        assertTrue(IconSpec.xmlFiles().keys.all { it.contains("mipmap-anydpi-v26") })
    }

    @Test
    fun parsesColors() {
        assertEquals(0xFF112233, IconSpec.parseColor("#112233"))
        assertEquals(0x80112233, IconSpec.parseColor("80112233"))
        assertNull(IconSpec.parseColor("#12"))
    }

    @Test
    fun readsPngHeader() {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 48, 0, 0, 1, 0)
        assertEquals(48 to 256, IconSpec.pngSize(png))
        assertNull(IconSpec.pngSize(ByteArray(30)))
    }

    @Test
    fun designRoundTripsThroughJson() {
        val d = IconDesign("icon_source.img", 0xFF123456, null, IconTransform(1.5f, 0.1f, -0.2f), IconShape.TEARDROP)
        assertEquals(d, IconDesign.fromJson(d.toJson()))
    }
}
