package com.example.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class B64Test {
    @Test
    fun matchesJdkForAllPaddingLengths() {
        val rnd = Random(42)
        for (len in 0..40) {
            val data = rnd.nextBytes(len)
            val expected = Base64.getEncoder().encodeToString(data)
            assertEquals(expected, B64.encode(data))
            assertArrayEquals(data, B64.decode(expected))
        }
    }

    @Test
    fun decodesUrlSafeAndWhitespace() {
        assertArrayEquals(byteArrayOf(-5, -1), B64.decode("+/8=".replace('+', '-').replace('/', '_')))
        assertArrayEquals("hello".toByteArray(), B64.decode("aGVs\nbG8="))
    }
}
