package com.example.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxmlTest {

    private fun roundTrip(utf8: Boolean, withStyle: Boolean) {
        val ax = Axml.parse(TestAxml.buildManifest(utf8, withStyle))
        assertEquals(utf8, ax.isUtf8)
        assertEquals("com.packflow.shell.placeholder", ax.attributeString("manifest", "package"))
        assertEquals(2, ax.replacePackagePrefix("com.packflow.shell.placeholder", "com.me.supergame"))
        assertEquals(1, ax.replaceString("PackFlowShellAppName", "Space Marines: Ünïcödé Tactics ⚔"))
        assertEquals(1, ax.replaceString("0.0.0-packflow", "1.2.3"))
        assertTrue(ax.setIntAttribute("manifest", "versionCode", 42))

        val bytes = ax.toByteArray()
        assertEquals(bytes.size, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4))
        val again = Axml.parse(bytes)
        assertEquals("com.me.supergame", again.attributeString("manifest", "package"))
        assertEquals("Space Marines: Ünïcödé Tactics ⚔", again.attributeString("application", "label"))
        assertEquals("1.2.3", again.attributeString("manifest", "versionName"))
        assertEquals(42, again.attributes().first { it.name == "versionCode" }.data)
        assertTrue(again.strings.contains("com.me.supergame.SOME_PERMISSION"))
        // untouched strings survive
        assertEquals("http://schemas.android.com/apk/res/android", again.strings.last())
    }

    @Test fun editsUtf16Pool() = roundTrip(utf8 = false, withStyle = false)

    @Test fun editsUtf8Pool() = roundTrip(utf8 = true, withStyle = false)

    @Test fun keepsStyleData() = roundTrip(utf8 = false, withStyle = true)

    @Test
    fun longStringsUseTwoByteLengths() {
        val ax = Axml.parse(TestAxml.buildManifest(utf8 = true))
        val long = "x".repeat(300)
        ax.replaceString("PackFlowShellAppName", long)
        assertEquals(long, Axml.parse(ax.toByteArray()).attributeString("application", "label"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonBinaryXml() {
        Axml.parse("<manifest/>".toByteArray() + ByteArray(16))
    }
}
