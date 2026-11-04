package com.example.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Test helper: a binary manifest laid out like aapt2's output, written without Axml's encoder. */
object TestAxml {
    /** Builds a small binary manifest the way aapt2 lays it out (independent of Axml's encoder). */
    fun buildManifest(utf8: Boolean, withStyle: Boolean = false): ByteArray {
        val strings = listOf(
            "package", "versionCode", "versionName", "label", "manifest", "application",
            "com.packflow.shell.placeholder", "0.0.0-packflow", "PackFlowShellAppName",
            "com.packflow.shell.placeholder.SOME_PERMISSION", "android", "http://schemas.android.com/apk/res/android"
        )
        // --- string pool ---
        val data = ByteArrayOutputStream()
        val offsets = strings.map { s ->
            val off = data.size()
            if (utf8) {
                val enc = s.toByteArray(Charsets.UTF_8)
                data.write(s.length); data.write(enc.size); data.write(enc); data.write(0)
            } else {
                data.write(s.length and 0xff); data.write(s.length shr 8)
                s.forEach { c -> data.write(c.code and 0xff); data.write(c.code shr 8) }
                data.write(0); data.write(0)
            }
            off
        }
        while (data.size() % 4 != 0) data.write(0)
        val styleData = if (withStyle) byteArrayOf(1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, -1, -1, -1, -1) else ByteArray(0)
        val styleCount = if (withStyle) 1 else 0
        val headerSize = 28
        val stringsStart = headerSize + 4 * strings.size + 4 * styleCount
        val poolSize = stringsStart + data.size() + styleData.size
        val pool = ByteBuffer.allocate(poolSize).order(ByteOrder.LITTLE_ENDIAN)
        pool.putShort(1).putShort(headerSize.toShort()).putInt(poolSize).putInt(strings.size).putInt(styleCount)
            .putInt(if (utf8) 0x100 else 0).putInt(stringsStart).putInt(if (withStyle) stringsStart + data.size() else 0)
        offsets.forEach { pool.putInt(it) }
        if (withStyle) pool.putInt(0)
        pool.put(data.toByteArray()).put(styleData)

        // --- elements ---
        fun element(nameIdx: Int, attrs: List<IntArray>): ByteArray {
            // attr: ns, name, raw, dataType, data
            val size = 16 + 20 + attrs.size * 20
            val e = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            e.putShort(0x0102).putShort(16).putInt(size).putInt(1).putInt(-1)
            e.putInt(-1).putInt(nameIdx).putShort(20).putShort(20).putShort(attrs.size.toShort())
                .putShort(0).putShort(0).putShort(0)
            attrs.forEach { a ->
                e.putInt(a[0]).putInt(a[1]).putInt(a[2]).putShort(8).put(0).put(a[3].toByte()).putInt(a[4])
            }
            return e.array()
        }
        val manifest = element(4, listOf(
            intArrayOf(-1, 0, 6, 0x03, 6),        // package="placeholder"
            intArrayOf(11, 1, -1, 0x10, 1),       // versionCode=1
            intArrayOf(11, 2, 7, 0x03, 7)         // versionName
        ))
        val app = element(5, listOf(intArrayOf(11, 3, 8, 0x03, 8)))  // label
        val perm = element(9, emptyList())
        val total = 8 + poolSize + manifest.size + app.size + perm.size
        val file = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        file.putShort(3).putShort(8).putInt(total).put(pool.array()).put(manifest).put(app).put(perm)
        return file.array()
    }

}
