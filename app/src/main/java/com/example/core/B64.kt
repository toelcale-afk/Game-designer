package com.example.core

/** Base64 (RFC 4648, standard alphabet) that works on every API level (java.util.Base64 needs API 26). */
object B64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE = IntArray(128) { -1 }.also { t ->
        ALPHABET.forEachIndexed { i, c -> t[c.code] = i }
        t['-'.code] = 62; t['_'.code] = 63 // also accept URL-safe input
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xff shl 16) or (data[i + 1].toInt() and 0xff shl 8) or (data[i + 2].toInt() and 0xff)
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63])
                .append(ALPHABET[n shr 6 and 63]).append(ALPHABET[n and 63])
            i += 3
        }
        val rem = data.size - i
        if (rem == 1) {
            val n = data[i].toInt() and 0xff shl 16
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63]).append("==")
        } else if (rem == 2) {
            val n = (data[i].toInt() and 0xff shl 16) or (data[i + 1].toInt() and 0xff shl 8)
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63])
                .append(ALPHABET[n shr 6 and 63]).append('=')
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buf = 0
        var bits = 0
        for (c in text) {
            if (c == '=' ) break
            if (c.isWhitespace()) continue
            val v = if (c.code < 128) DECODE[c.code] else -1
            require(v >= 0) { "Invalid base64 character '$c'" }
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write(buf shr bits and 0xff)
            }
        }
        return out.toByteArray()
    }
}
