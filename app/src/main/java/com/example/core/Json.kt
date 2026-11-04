package com.example.core

/**
 * Tiny dependency-free JSON reader/writer used by the pure-logic layer (project files,
 * unit/mission/level data, GitHub and Gemini request bodies).
 *
 * Values map to: Map<String, Any?> (objects, insertion ordered), List<Any?> (arrays),
 * String, Double/Long (numbers), Boolean and null.
 */
object Json {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Unexpected trailing data at ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw ParseException("Expected a JSON object")

    fun stringify(value: Any?, indent: Int = 0): String {
        val sb = StringBuilder()
        write(sb, value, indent, 0)
        return sb.toString()
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        writeString(sb, s)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?, indent: Int, level: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> writeDouble(sb, v)
            is Float -> writeDouble(sb, v.toDouble())
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, indent, level + 1)
                    writeString(sb, k.toString())
                    sb.append(if (indent > 0) ": " else ":")
                    write(sb, value, indent, level + 1)
                }
                newline(sb, indent, level)
                sb.append('}')
            }
            is Iterable<*> -> {
                val list = v.toList()
                if (list.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                list.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    newline(sb, indent, level + 1)
                    write(sb, item, indent, level + 1)
                }
                newline(sb, indent, level)
                sb.append(']')
            }
            is Array<*> -> write(sb, v.toList(), indent, level)
            is IntArray -> write(sb, v.toList(), indent, level)
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeDouble(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) { sb.append("null"); return }
        if (d == Math.floor(d) && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
    }

    private fun newline(sb: StringBuilder, indent: Int, level: Int) {
        if (indent <= 0) return
        sb.append('\n')
        repeat(indent * level) { sb.append(' ') }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') {
                    sb.append(String.format("\\u%04x", c.code))
                } else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (atEnd()) throw ParseException("Unexpected end of JSON")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Expected $word at $pos")
            pos += word.length
            return v
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return m }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw ParseException("Expected key at $pos")
                val k = str()
                skipWs()
                if (atEnd() || s[pos] != ':') throw ParseException("Expected ':' at $pos")
                pos++
                m[k] = value()
                skipWs()
                if (atEnd()) throw ParseException("Unclosed object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw ParseException("Expected ',' or '}' at $pos")
                }
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return l }
            while (true) {
                l.add(value())
                skipWs()
                if (atEnd()) throw ParseException("Unclosed array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw ParseException("Expected ',' or ']' at $pos")
                }
            }
        }

        fun str(): String {
            pos++
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unclosed string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) {
                t.toDoubleOrNull() ?: throw ParseException("Bad number $t")
            } else {
                t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw ParseException("Bad number $t")
            }
        }
    }
}

/** Convenience accessors for parsed JSON maps. */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.objList(key: String): List<Map<String, Any?>> =
    list(key).mapNotNull { it as? Map<String, Any?> }

fun Map<String, Any?>.str(key: String, default: String = ""): String =
    when (val v = this[key]) {
        null -> default
        is String -> v
        else -> v.toString()
    }

fun Map<String, Any?>.int(key: String, default: Int = 0): Int =
    (this[key] as? Number)?.toInt() ?: (this[key] as? String)?.toIntOrNull() ?: default

fun Map<String, Any?>.long(key: String, default: Long = 0): Long =
    (this[key] as? Number)?.toLong() ?: (this[key] as? String)?.toLongOrNull() ?: default

fun Map<String, Any?>.dbl(key: String, default: Double = 0.0): Double =
    (this[key] as? Number)?.toDouble() ?: (this[key] as? String)?.toDoubleOrNull() ?: default

fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean =
    this[key] as? Boolean ?: default
