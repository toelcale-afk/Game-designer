package com.example.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class SfxWave(val label: String) { SQUARE("Square"), SAWTOOTH("Saw"), SINE("Sine"), NOISE("Noise") }

/**
 * Parameters of an sfxr-style retro sound effect (after DrPetter's public-domain sfxr).
 * All values are in sfxr's 0..1 (or -1..1) ranges.
 */
data class SfxParams(
    val wave: SfxWave = SfxWave.SQUARE,
    val baseFreq: Float = 0.3f,
    val freqLimit: Float = 0f,
    val freqRamp: Float = 0f,
    val freqDeltaRamp: Float = 0f,
    val vibStrength: Float = 0f,
    val vibSpeed: Float = 0f,
    val arpMod: Float = 0f,
    val arpSpeed: Float = 0f,
    val duty: Float = 0f,
    val dutyRamp: Float = 0f,
    val repeatSpeed: Float = 0f,
    val phaserOffset: Float = 0f,
    val phaserRamp: Float = 0f,
    val lpfFreq: Float = 1f,
    val lpfRamp: Float = 0f,
    val lpfResonance: Float = 0f,
    val hpfFreq: Float = 0f,
    val hpfRamp: Float = 0f,
    val attack: Float = 0f,
    val sustain: Float = 0.3f,
    val punch: Float = 0f,
    val decay: Float = 0.4f,
    val volume: Float = 0.5f
) {
    fun toJson(): String = Json.stringify(
        linkedMapOf<String, Any?>(
            "wave" to wave.name, "baseFreq" to baseFreq, "freqLimit" to freqLimit, "freqRamp" to freqRamp,
            "freqDeltaRamp" to freqDeltaRamp, "vibStrength" to vibStrength, "vibSpeed" to vibSpeed,
            "arpMod" to arpMod, "arpSpeed" to arpSpeed, "duty" to duty, "dutyRamp" to dutyRamp,
            "repeatSpeed" to repeatSpeed, "phaserOffset" to phaserOffset, "phaserRamp" to phaserRamp,
            "lpfFreq" to lpfFreq, "lpfRamp" to lpfRamp, "lpfResonance" to lpfResonance,
            "hpfFreq" to hpfFreq, "hpfRamp" to hpfRamp, "attack" to attack, "sustain" to sustain,
            "punch" to punch, "decay" to decay, "volume" to volume
        ).mapValues { (_, v) -> if (v is Float) v.toDouble() else v },
        indent = 2
    )

    companion object {
        fun fromJson(text: String): SfxParams {
            val m = Json.parseObject(text)
            fun f(k: String, d: Float) = m.dbl(k, d.toDouble()).toFloat()
            val d = SfxParams()
            return SfxParams(
                wave = runCatching { SfxWave.valueOf(m.str("wave")) }.getOrDefault(d.wave),
                baseFreq = f("baseFreq", d.baseFreq), freqLimit = f("freqLimit", d.freqLimit),
                freqRamp = f("freqRamp", d.freqRamp), freqDeltaRamp = f("freqDeltaRamp", d.freqDeltaRamp),
                vibStrength = f("vibStrength", d.vibStrength), vibSpeed = f("vibSpeed", d.vibSpeed),
                arpMod = f("arpMod", d.arpMod), arpSpeed = f("arpSpeed", d.arpSpeed),
                duty = f("duty", d.duty), dutyRamp = f("dutyRamp", d.dutyRamp),
                repeatSpeed = f("repeatSpeed", d.repeatSpeed), phaserOffset = f("phaserOffset", d.phaserOffset),
                phaserRamp = f("phaserRamp", d.phaserRamp), lpfFreq = f("lpfFreq", d.lpfFreq),
                lpfRamp = f("lpfRamp", d.lpfRamp), lpfResonance = f("lpfResonance", d.lpfResonance),
                hpfFreq = f("hpfFreq", d.hpfFreq), hpfRamp = f("hpfRamp", d.hpfRamp),
                attack = f("attack", d.attack), sustain = f("sustain", d.sustain), punch = f("punch", d.punch),
                decay = f("decay", d.decay), volume = f("volume", d.volume)
            )
        }
    }
}

enum class SfxPreset(val label: String) {
    LASER("Laser"), EXPLOSION("Explosion"), HIT("Hit"), PICKUP("Pickup"), FOOTSTEP("Footstep"),
    JUMP("Jump"), POWERUP("Power-up"), BLIP("Blip"), RANDOM("Random")
}

object Sfxr {
    const val SAMPLE_RATE = 44100
    private const val MAX_SECONDS = 10

    /** A new random variation of [preset] (same seed -> same sound). */
    fun preset(preset: SfxPreset, seed: Long = System.nanoTime()): SfxParams {
        val r = Random(seed)
        fun frnd(x: Float) = r.nextFloat() * x
        fun rnd(n: Int) = r.nextInt(n + 1)
        var p = SfxParams()
        when (preset) {
            SfxPreset.PICKUP -> {
                p = p.copy(baseFreq = 0.4f + frnd(0.5f), attack = 0f, sustain = frnd(0.1f), decay = 0.1f + frnd(0.4f), punch = 0.3f + frnd(0.3f))
                if (rnd(1) == 1) p = p.copy(arpSpeed = 0.5f + frnd(0.2f), arpMod = 0.2f + frnd(0.4f))
            }
            SfxPreset.LASER -> {
                var wave = rnd(2)
                if (wave == 2 && rnd(1) == 1) wave = rnd(1)
                var base = 0.5f + frnd(0.5f)
                var limit = (base - 0.2f - frnd(0.6f)).coerceAtLeast(0.2f)
                var ramp = -0.15f - frnd(0.2f)
                if (rnd(2) == 0) { base = 0.3f + frnd(0.6f); limit = frnd(0.1f); ramp = -0.35f - frnd(0.3f) }
                p = p.copy(wave = SfxWave.entries[wave], baseFreq = base, freqLimit = limit, freqRamp = ramp)
                p = if (rnd(1) == 1) p.copy(duty = frnd(0.5f), dutyRamp = frnd(0.2f)) else p.copy(duty = 0.4f + frnd(0.5f), dutyRamp = -frnd(0.7f))
                p = p.copy(attack = 0f, sustain = 0.1f + frnd(0.2f), decay = frnd(0.4f))
                if (rnd(1) == 1) p = p.copy(punch = frnd(0.3f))
                if (rnd(2) == 0) p = p.copy(phaserOffset = frnd(0.2f), phaserRamp = -frnd(0.2f))
                if (rnd(1) == 1) p = p.copy(hpfFreq = frnd(0.3f))
            }
            SfxPreset.EXPLOSION -> {
                var base: Float
                var ramp: Float
                if (rnd(1) == 1) { base = 0.1f + frnd(0.4f); ramp = -0.1f + frnd(0.4f) } else { base = 0.2f + frnd(0.7f); ramp = -0.2f - frnd(0.2f) }
                base *= base
                if (rnd(4) == 0) ramp = 0f
                p = p.copy(wave = SfxWave.NOISE, baseFreq = base, freqRamp = ramp)
                if (rnd(2) == 0) p = p.copy(repeatSpeed = 0.3f + frnd(0.5f))
                p = p.copy(attack = 0f, sustain = 0.1f + frnd(0.3f), decay = frnd(0.5f))
                if (rnd(1) == 0) p = p.copy(phaserOffset = -0.3f + frnd(0.9f), phaserRamp = -frnd(0.3f))
                p = p.copy(punch = 0.2f + frnd(0.6f))
                if (rnd(1) == 1) p = p.copy(vibStrength = frnd(0.7f), vibSpeed = frnd(0.6f))
                if (rnd(2) == 0) p = p.copy(arpSpeed = 0.6f + frnd(0.3f), arpMod = 0.8f - frnd(1.6f))
            }
            SfxPreset.POWERUP -> {
                p = if (rnd(1) == 1) p.copy(wave = SfxWave.SAWTOOTH) else p.copy(duty = frnd(0.6f))
                p = if (rnd(1) == 1) {
                    p.copy(baseFreq = 0.2f + frnd(0.3f), freqRamp = 0.1f + frnd(0.4f), repeatSpeed = 0.4f + frnd(0.4f))
                } else {
                    val q = p.copy(baseFreq = 0.2f + frnd(0.3f), freqRamp = 0.05f + frnd(0.2f))
                    if (rnd(1) == 1) q.copy(vibStrength = frnd(0.7f), vibSpeed = frnd(0.6f)) else q
                }
                p = p.copy(attack = 0f, sustain = frnd(0.4f), decay = 0.1f + frnd(0.4f))
            }
            SfxPreset.HIT -> {
                var wave = rnd(2)
                if (wave == 2) wave = 3
                p = p.copy(wave = SfxWave.entries[wave])
                if (wave == 0) p = p.copy(duty = frnd(0.6f))
                p = p.copy(baseFreq = 0.2f + frnd(0.6f), freqRamp = -0.3f - frnd(0.4f), attack = 0f, sustain = frnd(0.1f), decay = 0.1f + frnd(0.2f))
                if (rnd(1) == 1) p = p.copy(hpfFreq = frnd(0.3f))
            }
            SfxPreset.JUMP -> {
                p = p.copy(wave = SfxWave.SQUARE, duty = frnd(0.6f), baseFreq = 0.3f + frnd(0.3f), freqRamp = 0.1f + frnd(0.2f),
                    attack = 0f, sustain = 0.1f + frnd(0.3f), decay = 0.1f + frnd(0.2f))
                if (rnd(1) == 1) p = p.copy(hpfFreq = frnd(0.3f))
                if (rnd(1) == 1) p = p.copy(lpfFreq = 1f - frnd(0.6f))
            }
            SfxPreset.BLIP -> {
                val wave = rnd(1)
                p = p.copy(wave = SfxWave.entries[wave], baseFreq = 0.2f + frnd(0.4f), attack = 0f, sustain = 0.1f + frnd(0.1f), decay = frnd(0.2f), hpfFreq = 0.1f)
                if (wave == 0) p = p.copy(duty = frnd(0.6f))
            }
            SfxPreset.FOOTSTEP -> {
                // Short, low, filtered noise thud (not in the original sfxr).
                p = p.copy(wave = SfxWave.NOISE, baseFreq = 0.08f + frnd(0.12f), freqRamp = -0.05f - frnd(0.1f),
                    attack = 0f, sustain = frnd(0.04f), decay = 0.08f + frnd(0.1f), punch = 0.15f + frnd(0.2f),
                    lpfFreq = 0.25f + frnd(0.2f), lpfResonance = frnd(0.3f), hpfFreq = frnd(0.04f))
            }
            SfxPreset.RANDOM -> {
                val base = frnd(2f) - 1f
                p = SfxParams(
                    wave = SfxWave.entries[rnd(3)],
                    baseFreq = (base * base * base + 0.5f).coerceIn(0.05f, 1f),
                    freqRamp = (frnd(2f) - 1f).pow(5), freqDeltaRamp = (frnd(2f) - 1f).pow(3),
                    duty = frnd(2f) - 1f, dutyRamp = (frnd(2f) - 1f).pow(3),
                    vibStrength = (frnd(2f) - 1f).pow(3), vibSpeed = frnd(2f) - 1f,
                    attack = (frnd(2f) - 1f).pow(3), sustain = (frnd(2f) - 1f).pow(2), decay = frnd(2f) - 1f,
                    punch = (frnd(0.8f)).pow(2), lpfResonance = frnd(2f) - 1f, lpfFreq = 1f - frnd(1f).pow(3),
                    lpfRamp = (frnd(2f) - 1f).pow(3), hpfFreq = frnd(1f).pow(5), hpfRamp = (frnd(2f) - 1f).pow(5),
                    phaserOffset = (frnd(2f) - 1f).pow(3), phaserRamp = (frnd(2f) - 1f).pow(3),
                    repeatSpeed = frnd(2f) - 1f, arpSpeed = frnd(2f) - 1f, arpMod = frnd(2f) - 1f
                ).let { q ->
                    q.copy(
                        sustain = abs(q.sustain).coerceAtLeast(0.05f), decay = abs(q.decay).coerceAtLeast(0.1f),
                        attack = abs(q.attack), duty = abs(q.duty), vibSpeed = abs(q.vibSpeed),
                        repeatSpeed = abs(q.repeatSpeed), arpSpeed = abs(q.arpSpeed), lpfResonance = abs(q.lpfResonance)
                    )
                }
                if (p.lpfFreq < 0.1f && p.lpfRamp < -0.05f) p = p.copy(lpfRamp = -p.lpfRamp)
            }
        }
        return p
    }

    /** Renders 16-bit mono PCM at [SAMPLE_RATE]. */
    fun synth(p: SfxParams, seed: Long = 1L): ShortArray = Synth(p, Random(seed)).run()

    private class Synth(val p: SfxParams, val rnd: Random) {
        var playing = true
        var phase = 0
        var fperiod = 0.0
        var fmaxperiod = 0.0
        var fslide = 0.0
        var fdslide = 0.0
        var period = 0
        var squareDuty = 0f
        var squareSlide = 0f
        var envStage = 0
        var envTime = 0
        val envLength = IntArray(3)
        var envVol = 0f
        var fphase = 0f
        var fdphase = 0f
        var iphase = 0
        val phaserBuffer = FloatArray(1024)
        var ipp = 0
        val noiseBuffer = FloatArray(32)
        var fltp = 0f
        var fltdp = 0f
        var fltw = 0f
        var fltwD = 0f
        var fltdmp = 0f
        var fltphp = 0f
        var flthp = 0f
        var flthpD = 0f
        var vibPhase = 0.0
        var vibSpeed = 0.0
        var vibAmp = 0.0
        var repTime = 0
        var repLimit = 0
        var arpTime = 0
        var arpLimit = 0
        var arpMod = 0.0

        fun frnd(x: Float) = rnd.nextFloat() * x

        fun reset(restart: Boolean) {
            if (!restart) phase = 0
            fperiod = 100.0 / (p.baseFreq * p.baseFreq + 0.001)
            period = fperiod.toInt()
            fmaxperiod = 100.0 / (p.freqLimit * p.freqLimit + 0.001)
            fslide = 1.0 - p.freqRamp.toDouble().pow(3.0) * 0.01
            fdslide = -p.freqDeltaRamp.toDouble().pow(3.0) * 0.000001
            squareDuty = 0.5f - p.duty * 0.5f
            squareSlide = -p.dutyRamp * 0.00005f
            arpMod = if (p.arpMod >= 0f) 1.0 - p.arpMod.toDouble().pow(2.0) * 0.9 else 1.0 + p.arpMod.toDouble().pow(2.0) * 10.0
            arpTime = 0
            arpLimit = ((1f - p.arpSpeed).toDouble().pow(2.0) * 20000 + 32).toInt()
            if (p.arpSpeed == 1f) arpLimit = 0
            if (!restart) {
                fltp = 0f; fltdp = 0f
                fltw = p.lpfFreq.toDouble().pow(3.0).toFloat() * 0.1f
                fltwD = 1f + p.lpfRamp * 0.0001f
                fltdmp = (5f / (1f + p.lpfResonance * p.lpfResonance * 20f) * (0.01f + fltw)).coerceAtMost(0.8f)
                fltphp = 0f
                flthp = p.hpfFreq * p.hpfFreq * 0.1f
                flthpD = 1f + p.hpfRamp * 0.0003f
                vibPhase = 0.0
                vibSpeed = p.vibSpeed.toDouble().pow(2.0) * 0.01
                vibAmp = p.vibStrength * 0.5
                envVol = 0f; envStage = 0; envTime = 0
                envLength[0] = (p.attack * p.attack * 100000f).toInt()
                envLength[1] = (p.sustain * p.sustain * 100000f).toInt()
                envLength[2] = (p.decay * p.decay * 100000f).toInt()
                fphase = p.phaserOffset * p.phaserOffset * 1020f
                if (p.phaserOffset < 0f) fphase = -fphase
                fdphase = p.phaserRamp * p.phaserRamp
                if (p.phaserRamp < 0f) fdphase = -fdphase
                iphase = abs(fphase.toInt())
                ipp = 0
                phaserBuffer.fill(0f)
                for (i in 0 until 32) noiseBuffer[i] = frnd(2f) - 1f
                repTime = 0
                repLimit = ((1f - p.repeatSpeed).toDouble().pow(2.0) * 20000 + 32).toInt()
                if (p.repeatSpeed == 0f) repLimit = 0
            }
        }

        fun run(): ShortArray {
            reset(false)
            val max = SAMPLE_RATE * MAX_SECONDS
            val out = ShortArray(max)
            var n = 0
            val masterVol = 0.05f
            while (playing && n < max) {
                repTime++
                if (repLimit != 0 && repTime >= repLimit) {
                    repTime = 0
                    reset(true)
                }
                arpTime++
                if (arpLimit != 0 && arpTime >= arpLimit) {
                    arpLimit = 0
                    fperiod *= arpMod
                }
                fslide += fdslide
                fperiod *= fslide
                if (fperiod > fmaxperiod) {
                    fperiod = fmaxperiod
                    if (p.freqLimit > 0f) playing = false
                }
                var rfperiod = fperiod
                if (vibAmp > 0.0) {
                    vibPhase += vibSpeed
                    rfperiod = fperiod * (1.0 + sin(vibPhase) * vibAmp)
                }
                period = rfperiod.toInt().coerceAtLeast(8)
                squareDuty = (squareDuty + squareSlide).coerceIn(0f, 0.5f)
                envTime++
                if (envTime > envLength[envStage]) {
                    envTime = 0
                    envStage++
                    if (envStage == 3) {
                        playing = false
                        break
                    }
                }
                envVol = when (envStage) {
                    0 -> if (envLength[0] == 0) 1f else envTime.toFloat() / envLength[0]
                    1 -> 1f + (1f - envTime.toFloat() / envLength[1].coerceAtLeast(1)) * 2f * p.punch
                    else -> 1f - envTime.toFloat() / envLength[2].coerceAtLeast(1)
                }
                fphase += fdphase
                iphase = abs(fphase.toInt()).coerceAtMost(1023)
                if (flthpD != 0f) flthp = (flthp * flthpD).coerceIn(0.00001f, 0.1f)

                var ssample = 0f
                for (si in 0 until 8) {
                    var sample: Float
                    phase++
                    if (phase >= period) {
                        phase %= period
                        if (p.wave == SfxWave.NOISE) for (i in 0 until 32) noiseBuffer[i] = frnd(2f) - 1f
                    }
                    val fp = phase.toFloat() / period
                    sample = when (p.wave) {
                        SfxWave.SQUARE -> if (fp < squareDuty) 0.5f else -0.5f
                        SfxWave.SAWTOOTH -> 1f - fp * 2f
                        SfxWave.SINE -> sin(fp * 2 * PI).toFloat()
                        SfxWave.NOISE -> noiseBuffer[(phase * 32 / period).coerceIn(0, 31)]
                    }
                    val pp = fltp
                    fltw = (fltw * fltwD).coerceIn(0f, 0.1f)
                    if (p.lpfFreq != 1f) {
                        fltdp += (sample - fltp) * fltw
                        fltdp -= fltdp * fltdmp
                    } else {
                        fltp = sample
                        fltdp = 0f
                    }
                    fltp += fltdp
                    fltphp += fltp - pp
                    fltphp -= fltphp * flthp
                    sample = fltphp
                    phaserBuffer[ipp and 1023] = sample
                    sample += phaserBuffer[(ipp - iphase + 1024) and 1023]
                    ipp = (ipp + 1) and 1023
                    ssample += sample * envVol
                }
                ssample = ssample / 8f * masterVol * 2f * p.volume
                // sfxr's master volume is quiet; scale up for game use, then clamp.
                val v = (ssample * 4f).coerceIn(-1f, 1f)
                out[n++] = (v * 32767f).toInt().toShort()
            }
            return out.copyOf(n)
        }
    }
}

/** Minimal RIFF/WAVE writer and reader for 16-bit PCM. */
object Wav {
    fun encode(samples: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            pcm[i * 2] = (s.toInt() and 0xff).toByte()
            pcm[i * 2 + 1] = (s.toInt() shr 8 and 0xff).toByte()
        }
        return fromPcm(pcm, sampleRate, channels, 16)
    }

    /** Wraps raw little-endian PCM (e.g. Gemini TTS output) in a WAV header. */
    fun fromPcm(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(sampleRate).putInt(byteRate).putShort((channels * bitsPerSample / 8).toShort()).putShort(bitsPerSample.toShort())
        header.put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }

    /** Sample rate from a "audio/L16;codec=pcm;rate=24000" style MIME type (default 24 kHz). */
    fun rateFromMime(mime: String?, default: Int = 24000): Int =
        mime?.let { Regex("rate=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: default

    fun durationSeconds(samples: ShortArray, sampleRate: Int): Double = samples.size.toDouble() / sampleRate
}
