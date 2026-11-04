package com.example.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFxTest {
    private val rate = 22050

    private fun tone(freq: Double, seconds: Double = 0.5) =
        MonoAudio(FloatArray((rate * seconds).toInt()) { (0.5 * sin(2 * PI * freq * it / rate)).toFloat() }, rate)

    /** Rough pitch estimate from zero crossings. */
    private fun freqOf(x: FloatArray): Double {
        val mid = x.copyOfRange(x.size / 4, x.size * 3 / 4)
        var crossings = 0
        for (i in 1 until mid.size) if ((mid[i - 1] < 0) != (mid[i] < 0)) crossings++
        return crossings / 2.0 / (mid.size.toDouble() / rate)
    }

    @Test
    fun pitchShiftKeepsLengthAndMovesPitch() {
        val src = tone(220.0)
        val up = VoiceFx.process(src, VoiceFxParams(pitch = 12f))
        assertEquals(src.samples.size, up.samples.size)
        assertEquals(440.0, freqOf(up.samples), 40.0)
        val down = VoiceFx.process(src, VoiceFxParams(pitch = -12f))
        assertEquals(110.0, freqOf(down.samples), 20.0)
    }

    @Test
    fun speedChangesLength() {
        val out = VoiceFx.process(tone(220.0), VoiceFxParams(speed = 2f))
        assertEquals(tone(220.0).samples.size / 2.0, out.samples.size.toDouble(), 2.0)
    }

    @Test
    fun everyPresetGivesCleanAudio() {
        val src = tone(180.0)
        VoicePreset.entries.forEach { p ->
            val out = VoiceFx.process(src, p.params)
            assertTrue("${p.name} empty", out.samples.isNotEmpty())
            assertTrue("${p.name} NaN", out.samples.none { it.isNaN() })
            val peak = out.samples.maxOf { abs(it) }
            assertTrue("${p.name} peak $peak", peak in 0.5f..0.9f)
        }
    }

    @Test
    fun reverbAndEchoAddATail() {
        val src = tone(300.0, 0.2)
        assertTrue(VoiceFx.process(src, VoiceFxParams(reverb = 0.8f)).samples.size > src.samples.size)
        assertTrue(VoiceFx.process(src, VoiceFxParams(echo = 0.7f)).samples.size > src.samples.size)
    }

    @Test
    fun readsWavFormats() {
        val pcm = shortArrayOf(0, 16384, -16384, 32767)
        val mono = WavReader.read(Wav.encode(pcm, 16000))
        assertEquals(16000, mono.sampleRate)
        assertEquals(0.5f, mono.samples[1], 0.001f)
        // stereo 16-bit: left 0.5, right -0.5 -> mono 0
        val stereoPcm = byteArrayOf(0, 64, 0, -64)
        val stereo = WavReader.read(Wav.fromPcm(stereoPcm, 8000, 2, 16))
        assertEquals(1, stereo.samples.size)
        assertEquals(0f, stereo.samples[0], 0.001f)
        // 8-bit unsigned
        val eight = WavReader.read(Wav.fromPcm(byteArrayOf(-1, 0, -128), 8000, 1, 8))
        assertEquals(-1f, eight.samples[1], 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonWav() {
        WavReader.read(ByteArray(64))
    }
}
