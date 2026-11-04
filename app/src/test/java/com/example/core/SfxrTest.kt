package com.example.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxrTest {
    @Test
    fun everyPresetMakesAShortAudibleSound() {
        SfxPreset.entries.forEach { preset ->
            repeat(5) { i ->
                val p = Sfxr.preset(preset, seed = i.toLong())
                val s = Sfxr.synth(p)
                val seconds = Wav.durationSeconds(s, Sfxr.SAMPLE_RATE)
                assertTrue("$preset #$i length $seconds", seconds > 0.01 && seconds <= 10.0)
                val peak = s.maxOf { kotlin.math.abs(it.toInt()) }
                assertTrue("$preset #$i silent (peak $peak)", peak > 300)
            }
        }
    }

    @Test
    fun sameSeedSameSound() {
        val p = Sfxr.preset(SfxPreset.EXPLOSION, 42)
        assertEquals(p, Sfxr.preset(SfxPreset.EXPLOSION, 42))
        assertArrayEquals(Sfxr.synth(p, 7), Sfxr.synth(p, 7))
    }

    @Test
    fun footstepsAreShort() {
        val s = Sfxr.synth(Sfxr.preset(SfxPreset.FOOTSTEP, 3))
        assertTrue(Wav.durationSeconds(s, Sfxr.SAMPLE_RATE) < 0.5)
    }

    @Test
    fun paramsRoundTripThroughJson() {
        val p = Sfxr.preset(SfxPreset.LASER, 9)
        val back = SfxParams.fromJson(p.toJson())
        assertEquals(p.wave, back.wave)
        assertEquals(p.baseFreq, back.baseFreq, 1e-6f)
        assertEquals(p.dutyRamp, back.dutyRamp, 1e-6f)
    }

    @Test
    fun wavHeaderIsValid() {
        val wav = Wav.encode(shortArrayOf(1, -1, 32767), 44100)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(wav.size - 8, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1, b.getShort(22).toInt())        // mono
        assertEquals(44100, b.getInt(24))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(6, b.getInt(40))                  // data bytes
        assertEquals(-1, b.getShort(46).toInt())
        assertEquals(24000, Wav.rateFromMime("audio/L16;codec=pcm;rate=24000"))
        assertEquals(16000, Wav.rateFromMime("audio/L16;rate=16000"))
        assertEquals(24000, Wav.rateFromMime(null))
    }
}
