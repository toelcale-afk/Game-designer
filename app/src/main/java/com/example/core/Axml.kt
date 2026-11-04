package com.example.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads and edits Android binary XML (the compiled AndroidManifest.xml inside an APK).
 *
 * Editing works by re-encoding the whole string pool, so replacement strings can be any
 * length: package name, app label and version name are plain strings in the pool, referenced
 * by index from the attributes. Indices never change, so the rest of the file stays valid;
 * only the pool chunk size and the file size in the header are updated.
 */
class Axml private constructor(
    private val prefix: ByteArray,          // file header (8 bytes)
    private val poolHeader: PoolHeader,
    val strings: MutableList<String>,
    private val styleOffsets: IntArray,
    private val styleData: ByteArray,
    private val rest: ByteArray             // every chunk after the string pool
) {
    private class PoolHeader(val headerSize: Int, val flags: Int, val extraHeader: ByteArray)

    val isUtf8: Boolean get() = poolHeader.flags and UTF8_FLAG != 0

    /** Replaces every string equal to [old] with [new]. Returns how many were replaced. */
    fun replaceString(old: String, new: String): Int {
        var n = 0
        for (i in strings.indices) if (strings[i] == old) { strings[i] = new; n++ }
        return n
    }

    /** Rewrites strings that equal [oldPrefix] or start with "[oldPrefix]." (package-derived names). */
    fun replacePackagePrefix(oldPrefix: String, newPrefix: String): Int {
        var n = 0
        for (i in strings.indices) {
            val s = strings[i]
            if (s == oldPrefix) { strings[i] = newPrefix; n++ }
            else if (s.startsWith("$oldPrefix.")) { strings[i] = newPrefix + s.substring(oldPrefix.length); n++ }
        }
        return n
    }

    /** All attributes of all start elements, in document order. */
    fun attributes(): List<AxmlAttribute> {
        val out = ArrayList<AxmlAttribute>()
        val buf = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0
        while (pos + 8 <= rest.size) {
            val type = buf.getShort(pos).toInt() and 0xffff
            val headerSize = buf.getShort(pos + 2).toInt() and 0xffff
            val size = buf.getInt(pos + 4)
            if (size < 8 || pos + size > rest.size) throw IllegalArgumentException("Corrupt XML chunk at $pos")
            if (type == RES_XML_START_ELEMENT_TYPE) {
                val ext = pos + headerSize
                val elementName = strings.getOrNull(buf.getInt(ext + 4)) ?: ""
                val attrStart = buf.getShort(ext + 8).toInt() and 0xffff
                val attrSize = buf.getShort(ext + 10).toInt() and 0xffff
                val count = buf.getShort(ext + 12).toInt() and 0xffff
                for (i in 0 until count) {
                    val a = ext + attrStart + i * attrSize
                    val nameIdx = buf.getInt(a + 4)
                    val raw = buf.getInt(a + 8)
                    val dataType = rest[a + 15].toInt() and 0xff
                    val data = buf.getInt(a + 16)
                    out.add(
                        AxmlAttribute(
                            element = elementName,
                            name = strings.getOrNull(nameIdx) ?: "",
                            rawValue = if (raw >= 0) strings.getOrNull(raw) else null,
                            dataType = dataType,
                            data = data,
                            dataOffset = a + 16
                        )
                    )
                }
            }
            pos += size
        }
        return out
    }

    /** String value of the first attribute [name] on [element] (e.g. manifest/package). */
    fun attributeString(element: String, name: String): String? =
        attributes().firstOrNull { it.element == element && it.name == name }?.let { a ->
            a.rawValue ?: if (a.dataType == TYPE_STRING) strings.getOrNull(a.data) else null
        }

    /** Sets an integer attribute in place (e.g. manifest/versionCode). Returns false if absent. */
    fun setIntAttribute(element: String, name: String, value: Int): Boolean {
        val a = attributes().firstOrNull { it.element == element && it.name == name && it.dataType in INT_TYPES }
            ?: return false
        ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN).putInt(a.dataOffset, value)
        return true
    }

    fun toByteArray(): ByteArray {
        val pool = encodePool()
        val total = prefix.size + pool.size + rest.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put(prefix)
        out.putInt(4, total)
        out.position(prefix.size)
        out.put(pool)
        out.put(rest)
        return out.array()
    }

    private fun encodePool(): ByteArray {
        val utf8 = isUtf8
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = data.size()
            if (utf8) writeUtf8(data, s) else writeUtf16(data, s)
        }
        while (data.size() % 4 != 0) data.write(0)
        val stringData = data.toByteArray()
        val headerSize = poolHeader.headerSize
        val stringsStart = headerSize + 4 * strings.size + 4 * styleOffsets.size
        val stylesStart = if (styleOffsets.isEmpty()) 0 else stringsStart + stringData.size
        val size = stringsStart + stringData.size + styleData.size
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(RES_STRING_POOL_TYPE.toShort())
        b.putShort(headerSize.toShort())
        b.putInt(size)
        b.putInt(strings.size)
        b.putInt(styleOffsets.size)
        b.putInt(poolHeader.flags and SORTED_FLAG.inv()) // edits may break sort order
        b.putInt(stringsStart)
        b.putInt(stylesStart)
        b.put(poolHeader.extraHeader)
        offsets.forEach { b.putInt(it) }
        styleOffsets.forEach { b.putInt(it) }
        b.put(stringData)
        b.put(styleData)
        return b.array()
    }

    companion object {
        const val RES_XML_TYPE = 0x0003
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val UTF8_FLAG = 0x100
        const val SORTED_FLAG = 0x1
        const val TYPE_STRING = 0x03
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_HEX = 0x11
        private val INT_TYPES = setOf(TYPE_INT_DEC, TYPE_INT_HEX)

        fun parse(bytes: ByteArray): Axml {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bytes.size >= 16) { "File too small to be binary XML" }
            val fileType = b.getShort(0).toInt() and 0xffff
            val fileHeader = b.getShort(2).toInt() and 0xffff
            require(fileType == RES_XML_TYPE) { "Not Android binary XML (type 0x${Integer.toHexString(fileType)})" }
            val p = fileHeader
            val poolType = b.getShort(p).toInt() and 0xffff
            require(poolType == RES_STRING_POOL_TYPE) { "Binary XML has no string pool first" }
            val poolHeaderSize = b.getShort(p + 2).toInt() and 0xffff
            val poolSize = b.getInt(p + 4)
            val stringCount = b.getInt(p + 8)
            val styleCount = b.getInt(p + 12)
            val flags = b.getInt(p + 16)
            val stringsStart = b.getInt(p + 20)
            val stylesStart = b.getInt(p + 24)
            val extra = bytes.copyOfRange(p + 28, p + poolHeaderSize)
            val utf8 = flags and UTF8_FLAG != 0
            val strings = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                val off = b.getInt(p + poolHeaderSize + 4 * i)
                val at = p + stringsStart + off
                strings.add(if (utf8) readUtf8(bytes, at) else readUtf16(b, at))
            }
            val styleOffsets = IntArray(styleCount) { b.getInt(p + poolHeaderSize + 4 * stringCount + 4 * it) }
            val styleData = if (styleCount > 0 && stylesStart > 0) bytes.copyOfRange(p + stylesStart, p + poolSize) else ByteArray(0)
            return Axml(
                prefix = bytes.copyOfRange(0, fileHeader),
                poolHeader = PoolHeader(poolHeaderSize, flags, extra),
                strings = strings,
                styleOffsets = styleOffsets,
                styleData = styleData,
                rest = bytes.copyOfRange(p + poolSize, bytes.size)
            )
        }

        private fun readUtf16(b: ByteBuffer, at: Int): String {
            var pos = at
            var len = b.getShort(pos).toInt() and 0xffff
            pos += 2
            if (len and 0x8000 != 0) {
                len = ((len and 0x7fff) shl 16) or (b.getShort(pos).toInt() and 0xffff)
                pos += 2
            }
            val chars = CharArray(len) { (b.getShort(pos + it * 2).toInt() and 0xffff).toChar() }
            return String(chars)
        }

        private fun readUtf8(bytes: ByteArray, at: Int): String {
            var pos = at
            // UTF-16 length (ignored), then UTF-8 byte length; each is 1 or 2 bytes.
            pos += if (bytes[pos].toInt() and 0x80 != 0) 2 else 1
            var len = bytes[pos].toInt() and 0xff
            if (len and 0x80 != 0) {
                len = ((len and 0x7f) shl 8) or (bytes[pos + 1].toInt() and 0xff)
                pos += 2
            } else pos += 1
            return String(bytes, pos, len, Charsets.UTF_8)
        }

        private fun writeUtf16(out: ByteArrayOutputStream, s: String) {
            val len = s.length
            if (len > 0x7fff) {
                writeU16(out, 0x8000 or (len shr 16))
                writeU16(out, len and 0xffff)
            } else writeU16(out, len)
            s.forEach { writeU16(out, it.code) }
            writeU16(out, 0)
        }

        private fun writeUtf8(out: ByteArrayOutputStream, s: String) {
            val enc = s.toByteArray(Charsets.UTF_8)
            writeLen8(out, s.length)
            writeLen8(out, enc.size)
            out.write(enc)
            out.write(0)
        }

        private fun writeLen8(out: ByteArrayOutputStream, len: Int) {
            require(len <= 0x7fff) { "String too long for binary XML" }
            if (len > 0x7f) {
                out.write(0x80 or (len shr 8))
                out.write(len and 0xff)
            } else out.write(len)
        }

        private fun writeU16(out: ByteArrayOutputStream, v: Int) {
            out.write(v and 0xff)
            out.write(v shr 8 and 0xff)
        }
    }
}

data class AxmlAttribute(
    val element: String,
    val name: String,
    val rawValue: String?,
    val dataType: Int,
    val data: Int,
    internal val dataOffset: Int
)
