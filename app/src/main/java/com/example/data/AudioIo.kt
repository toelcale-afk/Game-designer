package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import com.example.core.MonoAudio
import com.example.core.WavReader
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Decodes an audio file to mono floats: WAV directly, anything else (MP3, M4A, OGG…) with MediaCodec. */
object AudioDecoder {
    private const val MAX_SECONDS = 300

    fun decode(file: File): MonoAudio {
        if (file.extension.equals("wav", ignoreCase = true)) {
            runCatching { return WavReader.read(file.readBytes()) }
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("${file.name} has no audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val mono = ArrayList<Float>(rate * 10)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (outIdx >= 0) {
                        val out = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.asShortBuffer()
                        val frames = shorts.remaining() / channels.coerceAtLeast(1)
                        for (i in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += shorts.get() / 32768f
                            mono.add(sum / channels)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        if (mono.size > rate * MAX_SECONDS) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            if (mono.isEmpty()) throw IOException("Couldn't decode ${file.name}")
            return MonoAudio(mono.toFloatArray(), rate)
        } finally {
            extractor.release()
        }
    }
}

/** Records mono 16-bit audio from the microphone (needs RECORD_AUDIO). Blocking: run on Dispatchers.IO. */
class MicRecorder(private val context: Context) {
    @Volatile
    private var running = false

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun stop() {
        running = false
    }

    /** Records until [stop] or [maxSeconds]. [onLevel] gets a 0..1 loudness about 20 times a second. */
    fun record(maxSeconds: Int = 60, onLevel: (Float) -> Unit = {}): MonoAudio {
        if (!hasPermission) throw SecurityException("Microphone permission not granted")
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw IOException("This phone can't record at $rate Hz")
        @Suppress("MissingPermission") // checked above
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate / 5 * 2))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IOException("Couldn't open the microphone")
        }
        val chunk = ShortArray(rate / 20)
        val out = ArrayList<Float>(rate * 10)
        running = true
        try {
            rec.startRecording()
            while (running && out.size < rate * maxSeconds) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var sq = 0.0
                for (i in 0 until n) {
                    val v = chunk[i] / 32768f
                    out.add(v)
                    sq += v * v
                }
                onLevel(sqrt(sq / n).toFloat().times(4f).coerceIn(0f, 1f))
            }
        } finally {
            running = false
            runCatching { rec.stop() }
            rec.release()
        }
        return MonoAudio(trimSilence(out.toFloatArray(), rate), rate)
    }

    companion object {
        /** Cuts silence at both ends (keeps 50 ms around the voice). */
        fun trimSilence(x: FloatArray, rate: Int, threshold: Float = 0.02f): FloatArray {
            val first = x.indexOfFirst { abs(it) > threshold }
            if (first < 0) return x
            val last = x.indexOfLast { abs(it) > threshold }
            val pad = rate / 20
            return x.copyOfRange((first - pad).coerceAtLeast(0), (last + pad).coerceAtMost(x.size - 1) + 1)
        }
    }
}
