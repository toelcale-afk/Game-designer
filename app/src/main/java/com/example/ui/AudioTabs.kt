package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.FileKind
import com.example.core.GeminiApi
import com.example.core.ProjectLayout
import com.example.core.SfxParams
import com.example.core.SfxPreset
import com.example.core.SfxWave
import com.example.core.Sfxr
import com.example.core.Wav
import com.example.data.GeminiClient
import com.example.data.GeminiSettings
import com.example.data.ProjectRepository
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Plays 16-bit mono PCM once (sound effect previews). */
object PcmPlayer {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(samples: ShortArray, sampleRate: Int = Sfxr.SAMPLE_RATE) {
        stop()
        if (samples.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        t.write(samples, 0, samples.size)
        t.play()
        track = t
    }

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}

/** Saved audio files in a project folder, each with a play button. */
@Composable
private fun AudioFileList(dir: File, refresh: Int, title: String) {
    val files by produceState(emptyList<File>(), dir, refresh) {
        value = withContext(Dispatchers.IO) {
            dir.walkTopDown().filter { it.isFile && ProjectLayout.kindOf(it.name) == FileKind.AUDIO }.sortedByDescending { it.lastModified() }.take(50).toList()
        }
    }
    if (files.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    files.forEach { f -> AudioPreview(f) }
}

// ---------------------------------------------------------------- Sound

@Composable
fun SoundScreen(project: File) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var params by remember { mutableStateOf(Sfxr.preset(SfxPreset.LASER, 1)) }
    var preset by remember { mutableStateOf(SfxPreset.LASER) }
    var name by remember { mutableStateOf("laser") }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { PcmPlayer.stop() } }

    fun play(p: SfxParams = params) {
        scope.launch {
            val s = withContext(Dispatchers.Default) { Sfxr.synth(p) }
            PcmPlayer.play(s)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("sound_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Retro sound effects (offline)", style = MaterialTheme.typography.titleLarge)
        Text("Pick a kind of sound for a new random variation, tweak the sliders, then save it as a WAV into sounds/.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfxPreset.entries.forEach { p ->
                FilterChip(selected = preset == p, onClick = {
                    preset = p
                    params = Sfxr.preset(p)
                    name = p.label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")
                    play(params)
                }, label = { Text(p.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { play() }, modifier = Modifier.testTag("sfx_play")) { Text("Play") }
            FilledTonalButton(onClick = { params = Sfxr.preset(preset); play(params) }) { Text("New variation") }
            OutlinedButton(onClick = { params = mutate(params); play(params) }) { Text("Mutate") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SfxWave.entries.forEach { w -> FilterChip(selected = params.wave == w, onClick = { params = params.copy(wave = w) }, label = { Text(w.label) }) }
        }
        SfxSlider("Pitch", params.baseFreq, 0f..1f) { params = params.copy(baseFreq = it) }
        SfxSlider("Pitch slide", params.freqRamp, -1f..1f) { params = params.copy(freqRamp = it) }
        SfxSlider("Attack", params.attack, 0f..1f) { params = params.copy(attack = it) }
        SfxSlider("Sustain", params.sustain, 0f..1f) { params = params.copy(sustain = it) }
        SfxSlider("Punch", params.punch, 0f..1f) { params = params.copy(punch = it) }
        SfxSlider("Decay", params.decay, 0f..1f) { params = params.copy(decay = it) }
        SfxSlider("Duty (square)", params.duty, 0f..1f) { params = params.copy(duty = it) }
        SfxSlider("Vibrato depth", params.vibStrength, 0f..1f) { params = params.copy(vibStrength = it) }
        SfxSlider("Vibrato speed", params.vibSpeed, 0f..1f) { params = params.copy(vibSpeed = it) }
        SfxSlider("Low-pass", params.lpfFreq, 0f..1f) { params = params.copy(lpfFreq = it) }
        SfxSlider("High-pass", params.hpfFreq, 0f..1f) { params = params.copy(hpfFreq = it) }
        SfxSlider("Repeat", params.repeatSpeed, 0f..1f) { params = params.copy(repeatSpeed = it) }
        SfxSlider("Arpeggio", params.arpMod, -1f..1f) { params = params.copy(arpMod = it) }
        SfxSlider("Volume", params.volume, 0f..1f) { params = params.copy(volume = it) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("File name") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val wav = Wav.encode(Sfxr.synth(params), Sfxr.SAMPLE_RATE)
                            val base = ProjectLayout.safeName(name.trim().ifBlank { "sound" }) ?: "sound"
                            val dir = File(project, "sounds")
                            val f = repo.saveNew(dir, "$base.wav", wav)
                            // Keep the settings next to the WAV so the sound can be tweaked later.
                            repo.saveReplacing(File(dir, ".sfx"), f.nameWithoutExtension + ".json", params.toJson().toByteArray())
                            f
                        }
                    }.onSuccess { message = "Saved sounds/${it.name}"; refresh++ }
                        .onFailure { ErrorLog.error("Save sound failed", it); message = "Save failed: ${it.message}" }
                }
            }, modifier = Modifier.testTag("sfx_save")) { Text("Save WAV") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        AudioFileList(File(project, "sounds"), refresh, "Saved sounds")
    }
}

private fun mutate(p: SfxParams): SfxParams {
    val r = kotlin.random.Random(System.nanoTime())
    fun m(v: Float, lo: Float = 0f, hi: Float = 1f) = if (r.nextInt(2) == 0) v else (v + (r.nextFloat() - 0.5f) * 0.1f).coerceIn(lo, hi)
    return p.copy(
        baseFreq = m(p.baseFreq), freqRamp = m(p.freqRamp, -1f, 1f), duty = m(p.duty), sustain = m(p.sustain),
        decay = m(p.decay), punch = m(p.punch), vibStrength = m(p.vibStrength), lpfFreq = m(p.lpfFreq), hpfFreq = m(p.hpfFreq)
    )
}

@Composable
private fun SfxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Music

@Composable
fun MusicScreen(project: File, onNeedKey: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val settings = remember { GeminiSettings(context) }
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("Grim, slow orchestral battle theme with war drums, low brass and a distant choir, 90 BPM, loopable") }
    var name by remember { mutableStateOf("battle_theme") }
    var model by remember { mutableStateOf(settings.musicModel) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.importUri(uri, File(project, "music")) } }
                .onSuccess { message = "Imported music/${it.name}"; refresh++ }
                .onFailure { message = "Import failed: ${it.message}" }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("music_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Music", style = MaterialTheme.typography.titleLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Import a track made in the Gemini app", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Free option: make a track in the Gemini app, save it to your phone, then import it here into music/.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { importer.launch(arrayOf("audio/*")) }, modifier = Modifier.testTag("music_import")) { Text("Import audio file") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Generate with Lyria (your Gemini key)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Google lists Lyria music models without a free tier (about \$0.04 per 30-second clip at the time of writing), so this may need billing on your key. Model names change often; you can type a current one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModelPicker(model, GeminiApi.MUSIC_MODELS) { model = it; settings.musicModel = it }
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Describe the music") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Composing… this can take a minute.")
                    }
                } else {
                    Button(onClick = {
                        val key = settings.key()
                        if (key.isNullOrBlank()) { onNeedKey(); return@Button }
                        busy = true
                        message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    var last: Throwable? = null
                                    for (body in GeminiApi.musicBodies(prompt)) {
                                        try {
                                            val media = GeminiApi.parseMedia(GeminiClient.generate(key, model, body)).first { it.mimeType.startsWith("audio") }
                                            val (ext, bytes) = GeminiApi.toAudioFile(media)
                                            val base = ProjectLayout.safeName(name.trim().ifBlank { "track" }) ?: "track"
                                            val f = repo.saveNew(File(project, "music"), "$base.$ext", bytes)
                                            if (media.text.isNotBlank()) repo.saveReplacing(File(project, "music"), f.nameWithoutExtension + ".txt", media.text.toByteArray())
                                            return@withContext f to media.text
                                        } catch (t: Throwable) {
                                            last = t
                                        }
                                    }
                                    throw last ?: IllegalStateException("No audio returned")
                                }
                            }.onSuccess { (f, text) -> message = "Saved music/${f.name}"; lyrics = text.ifBlank { null }; refresh++ }
                                .onFailure { ErrorLog.error("Music generation failed", it); message = it.message }
                            busy = false
                        }
                    }, modifier = Modifier.testTag("music_generate")) { Text("Generate track") }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        lyrics?.let { Text("Structure / lyrics:\n$it", style = MaterialTheme.typography.labelSmall) }
        AudioFileList(File(project, "music"), refresh, "Tracks in this project")
    }
}

@Composable
private fun ModelPicker(model: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = model, onValueChange = { v -> onChange(v.trim()) }, label = { Text("Model ID") }, singleLine = true,
            isError = !GeminiApi.isValidModel(model), modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { open = true }) { Text("Pick") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
            }
        }
    }
}

// ---------------------------------------------------------------- Voice

@Composable
fun VoiceScreen(project: File, onNeedKey: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val settings = remember { GeminiSettings(context) }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("For the Emperor! Hold the line!") }
    var name by remember { mutableStateOf("hold_the_line") }
    var useGemini by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(GeminiApi.VOICES.first()) }
    var direction by remember { mutableStateOf("Say it like a battle-hardened sergeant:") }
    var model by remember { mutableStateOf(settings.ttsModel) }
    var pitch by remember { mutableStateOf(1f) }
    var rate by remember { mutableStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Android's built-in, offline text-to-speech.
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        tts[0] = TextToSpeech(context.applicationContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        onDispose { tts[0]?.shutdown() }
    }
    var androidVoice by remember { mutableStateOf<Voice?>(null) }
    val androidVoices = remember(ttsReady) {
        if (!ttsReady) emptyList() else runCatching {
            tts[0]?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }
    var voiceMenu by remember { mutableStateOf(false) }

    fun outFile(ext: String): File {
        val base = ProjectLayout.safeName(name.trim().ifBlank { "line" }) ?: "line"
        return ProjectRepository.uniqueChild(File(project, "voice").apply { mkdirs() }, "$base.$ext")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("voice_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Voice lines", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Line to speak") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useGemini = false }) {
            RadioButton(selected = !useGemini, onClick = { useGemini = false })
            Text("Android voice (offline, free)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useGemini = true }) {
            RadioButton(selected = useGemini, onClick = { useGemini = true })
            Text("Gemini voice (your key, more expressive)")
        }
        if (!useGemini) {
            if (!ttsReady) Text("Starting the phone's text-to-speech engine…", style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { voiceMenu = true }, enabled = androidVoices.isNotEmpty()) {
                    Text(androidVoice?.let { "${it.locale.displayName} · ${it.name.takeLast(18)}" } ?: "Default voice")
                }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    androidVoices.take(60).forEach { v ->
                        DropdownMenuItem(text = { Text("${v.locale.displayName} · ${v.name}") }, onClick = { androidVoice = v; voiceMenu = false })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Pitch", Modifier.width(60.dp)); Slider(pitch, { pitch = it }, valueRange = 0.5f..2f, modifier = Modifier.weight(1f)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Speed", Modifier.width(60.dp)); Slider(rate, { rate = it }, valueRange = 0.5f..2f, modifier = Modifier.weight(1f)) }
        } else {
            var gVoiceMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { gVoiceMenu = true }) { Text("Voice: $voice") }
                DropdownMenu(expanded = gVoiceMenu, onDismissRequest = { gVoiceMenu = false }) {
                    GeminiApi.VOICES.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { voice = v; gVoiceMenu = false }) }
                }
            }
            OutlinedTextField(value = direction, onValueChange = { direction = it }, label = { Text("How to say it (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ModelPicker(model, GeminiApi.TTS_MODELS) { model = it; settings.ttsModel = it }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  Recording…") }
        } else {
            Button(onClick = {
                if (text.isBlank()) return@Button
                message = null
                if (useGemini) {
                    val key = settings.key()
                    if (key.isNullOrBlank()) { onNeedKey(); return@Button }
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val media = GeminiApi.parseMedia(GeminiClient.generate(key, model, GeminiApi.ttsBody(text, voice, direction)))
                                    .first { it.mimeType.startsWith("audio") }
                                val (ext, bytes) = GeminiApi.toAudioFile(media)
                                outFile(ext).apply { writeBytes(bytes) }
                            }
                        }.onSuccess { message = "Saved voice/${it.name}"; refresh++ }
                            .onFailure { ErrorLog.error("Gemini TTS failed", it); message = it.message }
                        busy = false
                    }
                } else {
                    val engine = tts[0]
                    if (engine == null || !ttsReady) { message = "Text-to-speech isn't ready on this phone."; return@Button }
                    busy = true
                    scope.launch {
                        val file = outFile("wav")
                        val ok = synthesize(engine, text, file, androidVoice, pitch, rate)
                        message = if (ok) "Saved voice/${file.name}" else "The phone's text-to-speech couldn't record that line."
                        if (ok) refresh++ else file.delete()
                        busy = false
                    }
                }
            }, modifier = Modifier.testTag("voice_generate")) { Text("Record line") }
            if (!useGemini) TextButton(onClick = {
                tts[0]?.let { t ->
                    androidVoice?.let { t.voice = it }
                    t.setPitch(pitch); t.setSpeechRate(rate)
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
                }
            }) { Text("Preview aloud") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        AudioFileList(File(project, "voice"), refresh, "Voice clips in this project")
    }
}

private suspend fun synthesize(tts: TextToSpeech, text: String, file: File, voice: Voice?, pitch: Float, rate: Float): Boolean =
    suspendCancellableCoroutine { cont ->
        val id = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(file.exists() && file.length() > 44) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
            override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id && cont.isActive) cont.resume(false) }
        })
        voice?.let { tts.voice = it }
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
        val result = tts.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
    }
