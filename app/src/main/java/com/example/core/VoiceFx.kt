package com.example.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tanh

/** Mono audio as floats in -1..1. */
class MonoAudio(val samples: FloatArray, val sampleRate: Int) {
    val seconds: Double get() = samples.size.toDouble() / sampleRate

    fun toPcm16(): ShortArray = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }

    fun toWav(): ByteArray = Wav.encode(toPcm16(), sampleRate)

    companion object {
        fun fromPcm16(pcm: ShortArray, rate: Int) = MonoAudio(FloatArray(pcm.size) { pcm[it] / 32768f }, rate)
    }
}

/** Reads PCM WAV files (8/16/24/32-bit integer or 32-bit float, any channel count) as mono. */
object WavReader {
    fun read(bytes: ByteArray): MonoAudio {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV file" }
        var pos = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = b.getInt(pos + 4).let { if (it < 0) bytes.size - pos - 8 else it }
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = b.getShort(body).toInt() and 0xffff
                    channels = b.getShort(body + 2).toInt()
                    rate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt()
                    if (format == 0xFFFE && len >= 26) format = b.getShort(body + 24).toInt() and 0xffff // WAVE_FORMAT_EXTENSIBLE
                }
                "data" -> {
                    dataStart = body
                    dataLen = minOf(len, bytes.size - body)
                }
            }
            if (dataStart >= 0 && format != 0) break
            pos = body + len + (len and 1)
        }
        require(dataStart >= 0 && channels > 0 && rate > 0) { "WAV file has no audio data" }
        require(format == 1 || format == 3) { "Only PCM WAV files are supported (format $format)" }
        val bytesPer = bits / 8
        require(bytesPer in 1..4) { "Unsupported bit depth $bits" }
        val frames = dataLen / (bytesPer * channels)
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val at = dataStart + (f * channels + c) * bytesPer
                sum += when {
                    format == 3 && bits == 32 -> b.getFloat(at)
                    bits == 8 -> ((bytes[at].toInt() and 0xff) - 128) / 128f
                    bits == 16 -> b.getShort(at) / 32768f
                    bits == 24 -> ((bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or (bytes[at + 2].toInt() shl 16)) / 8388608f
                    else -> b.getInt(at) / 2147483648f
                }
            }
            out[f] = sum / channels
        }
        return MonoAudio(out, rate)
    }
}

/** Voice changer settings. All amounts are 0..1 unless noted. */
data class VoiceFxParams(
    val pitch: Float = 0f,          // semitones, -12..12 (keeps the timing)
    val speed: Float = 1f,          // 0.5..2, tape-style (changes pitch and length)
    val robot: Float = 0f,          // ring modulation
    val robotFreq: Float = 70f,     // Hz
    val radio: Float = 0f,          // band-pass + crunch + hiss
    val distortion: Float = 0f,
    val bitcrush: Float = 0f,
    val tremolo: Float = 0f,
    val echo: Float = 0f,
    val reverb: Float = 0f,
    val doubleLow: Float = 0f,      // mixes in a copy an octave down (demon)
    val reverse: Boolean = false
)

enum class VoicePreset(val label: String, val params: VoiceFxParams) {
    NONE("Original", VoiceFxParams()),
    DEEP("Deep", VoiceFxParams(pitch = -5f)),
    GIANT("Giant", VoiceFxParams(pitch = -8f, reverb = 0.35f)),
    MONSTER("Monster", VoiceFxParams(pitch = -7f, distortion = 0.45f, reverb = 0.3f, doubleLow = 0.4f)),
    DEMON("Demon", VoiceFxParams(pitch = -4f, doubleLow = 0.8f, distortion = 0.25f, reverb = 0.45f)),
    ORK("Gruff warrior", VoiceFxParams(pitch = -3f, distortion = 0.55f)),
    VOX_HELMET("Armored helmet vox", VoiceFxParams(pitch = -2f, radio = 0.75f, distortion = 0.2f, reverb = 0.12f)),
    RADIO("Radio", VoiceFxParams(radio = 1f)),
    ROBOT("Robot", VoiceFxParams(robot = 0.85f, robotFreq = 55f, bitcrush = 0.25f)),
    SERVITOR("Machine servant", VoiceFxParams(pitch = -1f, robot = 0.55f, robotFreq = 110f, radio = 0.35f, bitcrush = 0.35f)),
    ALIEN("Alien", VoiceFxParams(pitch = 4f, robot = 0.35f, robotFreq = 160f, tremolo = 0.5f, reverb = 0.25f)),
    CHIPMUNK("Chipmunk", VoiceFxParams(pitch = 7f)),
    GHOST("Ghost", VoiceFxParams(pitch = 2f, tremolo = 0.35f, reverb = 0.8f, echo = 0.35f)),
    CATHEDRAL("Cathedral", VoiceFxParams(reverb = 0.85f)),
    ECHO("Canyon echo", VoiceFxParams(echo = 0.7f)),
    SLOW_MO("Slow motion", VoiceFxParams(speed = 0.6f)),
    REVERSE("Reverse", VoiceFxParams(reverse = true))
}

/** Offline voice effects. Pure DSP: input and output are [MonoAudio]. */
object VoiceFx {

    fun process(input: MonoAudio, p: VoiceFxParams): MonoAudio {
        val rate = input.sampleRate
        var x = input.samples.copyOf()
        if (abs(p.speed - 1f) > 0.01f) x = resample(x, p.speed.coerceIn(0.25f, 4f).toDouble())
        val low = if (p.doubleLow > 0.01f) pitchShift(x, rate, p.pitch - 12f) else null
        if (abs(p.pitch) > 0.05f) x = pitchShift(x, rate, p.pitch.coerceIn(-24f, 24f))
        if (low != null) for (i in x.indices) x[i] = x[i] * (1f - p.doubleLow * 0.4f) + low.getOrElse(i) { 0f } * p.doubleLow
        if (p.distortion > 0.01f) distort(x, p.distortion)
        if (p.robot > 0.01f) ringMod(x, rate, p.robotFreq, p.robot)
        if (p.radio > 0.01f) radio(x, rate, p.radio)
        if (p.bitcrush > 0.01f) bitcrush(x, p.bitcrush)
        if (p.tremolo > 0.01f) tremolo(x, rate, p.tremolo)
        if (p.echo > 0.01f) x = echo(x, rate, p.echo)
        if (p.reverb > 0.01f) x = reverb(x, rate, p.reverb)
        if (p.reverse) x.reverse()
        normalize(x)
        return MonoAudio(x, rate)
    }

    /** Pitch shift that keeps the duration: two crossfaded read heads over a moving delay. */
    fun pitchShift(x: FloatArray, rate: Int, semitones: Float): FloatArray {
        val ratio = 2.0.pow(semitones / 12.0)
        val win = (rate * 0.045).coerceAtLeast(64.0)
        val out = FloatArray(x.size)
        var d = 0.0
        val step = 1.0 - ratio
        for (n in x.indices) {
            d += step
            if (d < 0) d += win
            if (d >= win) d -= win
            val d2 = (d + win / 2) % win
            val g1 = sin(PI * d / win).pow(2)
            val g2 = sin(PI * d2 / win).pow(2)
            out[n] = (g1 * read(x, n - d) + g2 * read(x, n - d2)).toFloat()
        }
        return out
    }

    private fun read(x: FloatArray, pos: Double): Double {
        if (pos < 0) return 0.0
        val i = floor(pos).toInt()
        if (i >= x.size - 1) return if (i < x.size) x[i].toDouble() else 0.0
        val f = pos - i
        return x[i] * (1 - f) + x[i + 1] * f
    }

    /** Tape-style speed change (linear interpolation). */
    fun resample(x: FloatArray, speed: Double): FloatArray {
        val n = (x.size / speed).toInt().coerceAtLeast(1)
        return FloatArray(n) { read(x, it * speed).toFloat() }
    }

    private fun distort(x: FloatArray, amount: Float) {
        val drive = 1f + amount * 12f
        val norm = tanh(drive)
        for (i in x.indices) x[i] = tanh(x[i] * drive) / norm
    }

    private fun ringMod(x: FloatArray, rate: Int, freq: Float, mix: Float) {
        val w = 2 * PI * freq / rate
        for (i in x.indices) x[i] = (x[i] * (1 - mix) + x[i] * sin(w * i).toFloat() * mix * 1.4f)
    }

    private fun radio(x: FloatArray, rate: Int, amount: Float) {
        val hp = Biquad.highPass(rate, 250.0 + 250.0 * amount, 0.7)
        val lp = Biquad.lowPass(rate, 4200.0 - 1400.0 * amount, 0.9)
        val rnd = java.util.Random(7)
        val drive = 1f + amount * 4f
        for (i in x.indices) {
            val filtered = lp.process(hp.process(x[i].toDouble())).toFloat()
            val crunchy = tanh(filtered * drive) / tanh(drive)
            x[i] = x[i] * (1 - amount) + (crunchy + (rnd.nextFloat() - 0.5f) * 0.012f * amount) * amount * 1.6f
        }
    }

    private fun bitcrush(x: FloatArray, amount: Float) {
        val levels = 2.0.pow(12.0 - 8.0 * amount).toFloat()
        val hold = 1 + (amount * 6).toInt()
        var held = 0f
        for (i in x.indices) {
            if (i % hold == 0) held = round(x[i] * levels) / levels
            x[i] = held
        }
    }

    private fun tremolo(x: FloatArray, rate: Int, depth: Float) {
        val w = 2 * PI * 6.0 / rate
        for (i in x.indices) x[i] *= (1f - depth * 0.5f * (1f + sin(w * i).toFloat()))
    }

    private fun echo(x: FloatArray, rate: Int, amount: Float): FloatArray {
        val delay = (rate * (0.18 + 0.22 * amount)).toInt()
        val fb = 0.25f + 0.35f * amount
        val out = FloatArray(x.size + delay * 4)
        for (i in out.indices) {
            val dry = if (i < x.size) x[i] else 0f
            out[i] = dry + if (i >= delay) out[i - delay] * fb else 0f
        }
        return trimTail(out, x.size)
    }

    /** Schroeder reverb: 4 parallel combs + 2 allpasses. */
    private fun reverb(x: FloatArray, rate: Int, amount: Float): FloatArray {
        val k = rate / 44100.0
        val tail = (rate * (0.6 + 1.6 * amount)).toInt()
        val n = x.size + tail
        val wet = FloatArray(n)
        val fb = 0.74f + 0.2f * amount
        for (d0 in intArrayOf(1557, 1617, 1491, 1422)) {
            val d = (d0 * k).toInt().coerceAtLeast(1)
            val buf = FloatArray(n)
            var lp = 0f
            for (i in 0 until n) {
                val inp = if (i < x.size) x[i] else 0f
                val delayed = if (i >= d) buf[i - d] else 0f
                lp = delayed * 0.6f + lp * 0.4f // damping
                buf[i] = inp + lp * fb
                wet[i] += delayed * 0.25f
            }
        }
        var y = wet
        for (d0 in intArrayOf(225, 556)) {
            val d = (d0 * k).toInt().coerceAtLeast(1)
            val o = FloatArray(n)
            for (i in 0 until n) {
                val xd = if (i >= d) y[i - d] else 0f
                val yd = if (i >= d) o[i - d] else 0f
                o[i] = -0.5f * y[i] + xd + 0.5f * yd
            }
            y = o
        }
        val out = FloatArray(n)
        val mix = 0.15f + 0.6f * amount
        for (i in 0 until n) out[i] = (if (i < x.size) x[i] else 0f) * (1f - mix * 0.5f) + y[i] * mix
        return trimTail(out, x.size)
    }

    /** Drops the silent end of a tail (keeps at least [minLen] samples). */
    private fun trimTail(x: FloatArray, minLen: Int): FloatArray {
        var end = x.size
        while (end > minLen && abs(x[end - 1]) < 0.0008f) end--
        // short fade-out to avoid a click
        val fade = minOf(512, end)
        for (i in 0 until fade) x[end - 1 - i] *= i / fade.toFloat()
        return x.copyOf(end)
    }

    /** Peak-normalizes to -1 dBFS (quiet input stays audible, loud effects don't clip). */
    fun normalize(x: FloatArray, peak: Float = 0.89f) {
        var max = 0f
        for (v in x) if (abs(v) > max) max = abs(v)
        if (max < 1e-6f) return
        val g = peak / max
        for (i in x.indices) x[i] *= g
    }

    /** RBJ cookbook biquad. */
    class Biquad private constructor(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }

        companion object {
            private fun make(rate: Int, f: Double, q: Double, high: Boolean): Biquad {
                val w = 2 * PI * f.coerceIn(10.0, rate / 2.2) / rate
                val alpha = sin(w) / (2 * q)
                val c = cos(w)
                val a0 = 1 + alpha
                val b0 = if (high) (1 + c) / 2 else (1 - c) / 2
                val b1 = if (high) -(1 + c) else 1 - c
                return Biquad(b0 / a0, b1 / a0, b0 / a0, -2 * c / a0, (1 - alpha) / a0)
            }

            fun lowPass(rate: Int, f: Double, q: Double = 0.707) = make(rate, f, q, high = false)
            fun highPass(rate: Int, f: Double, q: Double = 0.707) = make(rate, f, q, high = true)
        }
    }
}
