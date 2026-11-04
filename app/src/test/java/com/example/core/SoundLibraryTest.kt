package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLibraryTest {
    @Test
    fun hasHundredsOfUniqueSounds() {
        val all = SoundLibrary.all
        assertTrue("only ${all.size}", all.size >= 400)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.id.matches(Regex("^[a-z0-9_]+$")) })
        assertTrue(SoundLibrary.categories.size >= 8)
    }

    @Test
    fun everySoundIsAudibleAndShort() {
        SoundLibrary.all.forEach { s ->
            val pcm = SoundLibrary.render(s)
            val seconds = Wav.durationSeconds(pcm, Sfxr.SAMPLE_RATE)
            assertTrue("${s.id} is ${seconds}s", seconds in 0.01..6.0)
            assertTrue("${s.id} is silent", pcm.maxOf { kotlin.math.abs(it.toInt()) } > 200)
        }
    }

    @Test
    fun deterministic() {
        val a = SoundLibrary.byId("laser_03")!!
        assertEquals(a.params, SoundLibrary.all.first { it.id == "laser_03" }.params)
        assertTrue(SoundLibrary.render(a).contentEquals(SoundLibrary.render(a)))
    }

    @Test
    fun searchMatchesNamesTagsAndCategories() {
        assertTrue(SoundLibrary.search("tyranid").all { it.category == "Creatures" })
        assertTrue(SoundLibrary.search("laser 0").isNotEmpty())
        assertTrue(SoundLibrary.search("", category = "UI").all { it.category == "UI" })
        assertEquals(1, SoundLibrary.search("", onlyIds = setOf("coin_01")).size)
        assertTrue(SoundLibrary.search("zzzz").isEmpty())
    }
}
