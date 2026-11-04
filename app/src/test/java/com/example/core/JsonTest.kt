package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test
    fun roundTripsNestedValues() {
        val value = mapOf(
            "name" to "Space \"Marine\"\n",
            "hp" to 12L,
            "speed" to 1.5,
            "tags" to listOf("a", "b"),
            "ok" to true,
            "none" to null
        )
        val text = Json.stringify(value, indent = 2)
        assertEquals(value, Json.parse(text))
    }

    @Test
    fun parsesUnicodeEscapesAndNumbers() {
        val m = Json.parseObject("""{"s":"é☃","n":-3,"d":2.5e2}""")
        assertEquals("é☃", m.str("s"))
        assertEquals(-3, m.int("n"))
        assertEquals(250.0, m.dbl("d"), 0.0)
    }

    @Test
    fun wholeDoublesWriteWithoutDecimals() {
        assertEquals("[3,2.5]", Json.stringify(listOf(3.0, 2.5)))
    }

    @Test(expected = Json.ParseException::class)
    fun rejectsTrailingGarbage() {
        Json.parse("{} x")
    }

    @Test
    fun accessorsUseDefaults() {
        val m = Json.parseObject("{}")
        assertEquals("d", m.str("missing", "d"))
        assertEquals(7, m.int("missing", 7))
        assertTrue(m.objList("missing").isEmpty())
        assertNull(m.obj("missing"))
    }
}
