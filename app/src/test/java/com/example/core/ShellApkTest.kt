package com.example.core

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShellApkTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun png(size: Int): ByteArray {
        val b = ByteArray(40)
        byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82).copyInto(b)
        fun put(at: Int, v: Int) { b[at] = (v shr 24).toByte(); b[at + 1] = (v shr 16).toByte(); b[at + 2] = (v shr 8).toByte(); b[at + 3] = v.toByte() }
        put(16, size); put(20, size)
        return b
    }

    private fun fakeShell(): File {
        val f = tmp.newFile("shell.apk")
        ZipOutputStream(f.outputStream()).use { z ->
            fun add(name: String, data: ByteArray, stored: Boolean = false) {
                val e = ZipEntry(name)
                if (stored) {
                    e.method = ZipEntry.STORED; e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                z.putNextEntry(e); z.write(data); z.closeEntry()
            }
            add("AndroidManifest.xml", TestAxml.buildManifest(utf8 = false))
            add("classes.dex", ByteArray(100) { it.toByte() })
            add("resources.arsc", ByteArray(77), stored = true)
            add("res/mipmap-mdpi-v4/ic_launcher.png", png(48), stored = true)
            add("res/mipmap-xxhdpi-v4/ic_launcher.png", png(144), stored = true)
            add("res/drawable/other.png", png(30), stored = true)
            add("assets/www/index.html", "<p>shell placeholder</p>".toByteArray())
            add("META-INF/MANIFEST.MF", "old".toByteArray())
            add("META-INF/CERT.RSA", "old".toByteArray())
        }
        return f
    }

    private val spec = QuickBuildSpec(
        appName = "Space Marines Tactics",
        packageName = "com.me.spacemarines",
        versionName = "1.4",
        versionCode = 7,
        webFiles = mapOf("index.html" to "<h1>game</h1>".toByteArray(), "sprites/marine.png" to png(16)),
        icons = mapOf(Density.MDPI to png(48).also { it[39] = 9 }, Density.XXHDPI to png(144).also { it[39] = 9 })
    )

    @Test
    fun patchesManifestFilesAndIcons() {
        val out = File(tmp.root, "out.apk")
        val report = ShellApk.patch(fakeShell(), out, spec)
        assertEquals(2, report.replacedIcons)
        ZipFile(out).use { z ->
            val ax = Axml.parse(z.getInputStream(z.getEntry("AndroidManifest.xml")).readBytes())
            assertEquals("com.me.spacemarines", ax.attributeString("manifest", "package"))
            assertEquals("Space Marines Tactics", ax.attributeString("application", "label"))
            assertEquals("1.4", ax.attributeString("manifest", "versionName"))
            assertEquals(7, ax.attributes().first { it.name == "versionCode" }.data)
            assertEquals("<h1>game</h1>", String(z.getInputStream(z.getEntry("assets/www/index.html")).readBytes()))
            assertNotNull(z.getEntry("assets/www/sprites/marine.png"))
            assertTrue(String(z.getInputStream(z.getEntry(ShellApk.CONFIG_PATH)).readBytes()).contains("\"fullscreen\": true"))
            assertNull(z.getEntry("META-INF/MANIFEST.MF"))
            assertNull(z.getEntry("META-INF/CERT.RSA"))
            assertEquals(9, z.getInputStream(z.getEntry("res/mipmap-xxhdpi-v4/ic_launcher.png")).readBytes()[39].toInt())
            assertEquals(0, z.getInputStream(z.getEntry("res/drawable/other.png")).readBytes()[39].toInt())
            assertEquals(ZipEntry.STORED, z.getEntry("resources.arsc").method)
        }
    }

    @Test
    fun storedEntriesAreFourByteAligned() {
        val out = File(tmp.root, "aligned.apk")
        ShellApk.patch(fakeShell(), out, spec)
        val stored = ZipFile(out).use { z -> z.entries().toList().filter { it.method == ZipEntry.STORED }.map { it.name } }
        assertTrue(stored.contains("resources.arsc"))
        RandomAccessFile(out, "r").use { raf ->
            var pos = 0L
            var checked = 0
            while (pos < raf.length() - 4) {
                raf.seek(pos)
                val sig = Integer.reverseBytes(raf.readInt())
                if (sig != 0x04034b50) break
                raf.seek(pos + 6); val flags = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                val method = java.lang.Short.reverseBytes(raf.readShort()).toInt()
                raf.seek(pos + 18); val csize = Integer.reverseBytes(raf.readInt()).toLong() and 0xffffffffL
                raf.seek(pos + 26)
                val n = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                val x = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                val nameBytes = ByteArray(n); raf.readFully(nameBytes)
                val dataStart = pos + 30 + n + x
                if (method == 0) {
                    assertEquals("${String(nameBytes)} misaligned", 0L, dataStart % 4)
                    checked++
                    pos = dataStart + csize
                } else {
                    // deflated entries use a data descriptor; find the next local header by scanning
                    var p = dataStart + 1
                    while (p < raf.length() - 4) {
                        raf.seek(p)
                        val s = Integer.reverseBytes(raf.readInt())
                        if (s == 0x04034b50 || s == 0x02014b50) break
                        p++
                    }
                    pos = p
                    assertTrue(flags >= 0)
                }
            }
            assertEquals(stored.size, checked)
        }
    }

    @Test
    fun validatesSpec() {
        assertNotNull(spec.copy(packageName = "Com.Bad").validationError())
        assertNotNull(spec.copy(packageName = "com.new.game").validationError()) // 'new' is a keyword
        assertNotNull(spec.copy(packageName = ShellApk.PLACEHOLDER_PACKAGE).validationError())
        assertNotNull(spec.copy(webFiles = mapOf("game.html" to ByteArray(1))).validationError())
        assertNull(spec.validationError())
    }

    @Test
    fun suggestsValidPackageNames() {
        assertEquals("com.packflow.games.mycoolgame", ShellApk.suggestPackageName("My Cool Game!"))
        assertTrue(ShellApk.isValidPackageName(ShellApk.suggestPackageName("123 !!!")))
        assertTrue(ShellApk.isValidPackageName(ShellApk.suggestPackageName("New")))
    }

    @Test
    fun rejectsPathTraversal() {
        val bad = spec.copy(webFiles = spec.webFiles + ("../evil.txt" to ByteArray(1)))
        val r = runCatching { ShellApk.patch(fakeShell(), File(tmp.root, "x.apk"), bad) }
        assertTrue(r.isFailure)
        assertFalse(File(tmp.root, "evil.txt").exists())
    }
}
