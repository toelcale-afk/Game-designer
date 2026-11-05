package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.core.LibrarySound
import com.example.core.MonoAudio
import com.example.core.ProjectLayout
import com.example.core.SfxParams
import com.example.core.SoundLibrary
import com.example.core.VoiceFx
import com.example.core.VoiceFxParams
import com.example.core.VoicePreset
import com.example.core.Wav
import com.example.data.AudioDecoder
import com.example.data.MicRecorder
import com.example.data.ProjectRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Sound library

private const val FAVORITES = "★ Favorites"

/** Built-in sound database: search, preview, favorite, add to the project or tweak in the Sound tab. */
@Composable
fun SoundLibraryScreen(project: File, onEdit: (SfxParams, String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val prefs = remember { context.getSharedPreferences("sound_library", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { PcmPlayer.stop() } }

    val inProject by produceState(emptySet<String>(), project, added) {
        value = withContext(Dispatchers.IO) { File(project, "sounds").list()?.toSet() ?: emptySet() }
    }
    val results = remember(query, category, favorites) {
        if (category == FAVORITES) SoundLibrary.search(query, onlyIds = favorites)
        else SoundLibrary.search(query, category)
    }

    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    fun addToProject(list: List<LibrarySound>) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(project, "sounds")
                    list.forEach { s ->
                        repo.saveReplacing(dir, s.fileName, Wav.encode(SoundLibrary.render(s), com.example.core.Sfxr.SAMPLE_RATE))
                        repo.saveReplacing(File(dir, ".sfx"), "${s.id}.json", s.params.toJson().toByteArray())
                    }
                }
            }.onSuccess { message = if (list.size == 1) "Added sounds/${list[0].fileName}" else "Added ${list.size} sounds to sounds/"; added++ }
                .onFailure { ErrorLog.error("Add library sound failed", it); message = "Couldn't add: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().testTag("sound_library_screen")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sound library", style = MaterialTheme.typography.titleLarge)
            Text(
                "${SoundLibrary.all.size} built-in retro sounds, made on the phone (offline, free to use in your games).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                label = { Text("Search (e.g. laser, explosion, alien, menu)") }, modifier = Modifier.fillMaxWidth().testTag("library_search")
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                FilterChip(selected = category == FAVORITES, onClick = { category = FAVORITES }, label = { Text("$FAVORITES (${favorites.size})") })
                SoundLibrary.categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${results.size} sounds", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (category == FAVORITES && results.isNotEmpty()) TextButton(onClick = { addToProject(results) }) { Text("Add all to project") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(results, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { PcmPlayer.play(withContext(Dispatchers.Default) { SoundLibrary.render(s) }) } }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play ${s.name}")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.name + if (s.fileName in inProject) "  ✓" else "", style = MaterialTheme.typography.bodyLarge)
                        Text("${s.category} · ${s.tags.drop(1).take(3).joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { toggleFavorite(s.id) }) {
                        Icon(if (s.id in favorites) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorite", tint = if (s.id in favorites) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onEdit(s.params, s.id) }) { Text("Tweak") }
                    FilledTonalButton(onClick = { addToProject(listOf(s)) }) { Text("Add") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Voice changer

/** Record (or pick) a voice line and change it: presets plus sliders, saved as WAV in voice/. */
@Composable
fun VoiceFxScreen(project: File, incoming: File? = null) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    val recorder = remember { MicRecorder(context.applicationContext) }
    var source by remember { mutableStateOf<MonoAudio?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var params by remember { mutableStateOf(VoiceFxParams()) }
    var preset by remember { mutableStateOf(VoicePreset.NONE) }
    var recording by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("voice_fx") }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var clipMenu by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.stop(); PcmPlayer.stop() } }

    val clips by produceState(emptyList<File>(), project, refresh) {
        value = withContext(Dispatchers.IO) {
            listOf("voice", "sounds", "music").flatMap { d ->
                File(project, d).walkTopDown().filter { it.isFile && ProjectLayout.kindOf(it.name) == FileKind.AUDIO && !it.path.contains("/.") }.toList()
            }.sortedByDescending { it.lastModified() }.take(100)
        }
    }

    fun load(file: File) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AudioDecoder.decode(file) } }
                .onSuccess { source = it; sourceName = file.name; name = file.nameWithoutExtension + "_fx"; message = null }
                .onFailure { ErrorLog.error("Decode ${file.name} failed", it); message = "Couldn't read ${file.name}: ${it.message}" }
            busy = false
        }
    }

    fun startRecording() {
        recording = true
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { recorder.record(maxSeconds = 60) { lv -> level = lv } }
            }.onSuccess { a -> source = a; sourceName = "Recording"; name = "recording_fx"; message = String.format(Locale.US, "Recorded %.1f s", a.seconds) }
                .onFailure { ErrorLog.error("Recording failed", it); message = "Recording failed: ${it.message}" }
            recording = false
            level = 0f
        }
    }

    LaunchedEffect(incoming) { incoming?.let { load(it) } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message = "Microphone permission is needed to record. You can still change existing clips."
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.importUri(uri, File(project, "voice")) } }
                .onSuccess { refresh++; load(it) }
                .onFailure { message = "Import failed: ${it.message}" }
        }
    }

    fun preview() {
        val src = source ?: return
        busy = true
        scope.launch {
            val out = withContext(Dispatchers.Default) { VoiceFx.process(src, params) }
            PcmPlayer.play(out.toPcm16(), out.sampleRate)
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("voice_fx_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Voice changer", style = MaterialTheme.typography.titleLarge)
        Text("Record yourself or pick a clip, choose an effect, preview, then save it into voice/. Works offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---- source
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recording) {
                Button(onClick = { recorder.stop() }, modifier = Modifier.testTag("voice_fx_stop")) { Text("■ Stop") }
                LinearProgressIndicator(progress = { level }, modifier = Modifier.weight(1f).height(8.dp))
            } else {
                Button(onClick = {
                    if (recorder.hasPermission) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !busy, modifier = Modifier.testTag("voice_fx_record")) { Text("● Record") }
                Box {
                    OutlinedButton(onClick = { clipMenu = true }, enabled = clips.isNotEmpty() && !busy) { Text(if (clips.isEmpty()) "No clips yet" else "Pick clip") }
                    DropdownMenu(expanded = clipMenu, onDismissRequest = { clipMenu = false }) {
                        clips.forEach { f -> DropdownMenuItem(text = { Text(f.relativeTo(project).invariantSeparatorsPath) }, onClick = { clipMenu = false; load(f) }) }
                    }
                }
                TextButton(onClick = { importer.launch(arrayOf("audio/*")) }, enabled = !busy) { Text("Import") }
            }
        }
        val src = source
        if (src == null) {
            Text("No clip yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(Locale.US, "%s · %.1f s", sourceName, src.seconds), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { PcmPlayer.play(src.toPcm16(), src.sampleRate) }) { Text("Play original") }
            }
        }

        // ---- effects
        Text("Effect", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VoicePreset.entries.forEach { p ->
                FilterChip(selected = preset == p, onClick = { preset = p; params = p.params; if (source != null) preview() }, label = { Text(p.label) })
            }
        }
        FxSlider("Pitch", params.pitch, -12f..12f, "%+.0f st") { params = params.copy(pitch = it) }
        FxSlider("Speed", params.speed, 0.5f..2f, "%.2fx") { params = params.copy(speed = it) }
        FxSlider("Robot", params.robot, 0f..1f) { params = params.copy(robot = it) }
        if (params.robot > 0.01f) FxSlider("Robot tone", params.robotFreq, 30f..300f, "%.0f Hz") { params = params.copy(robotFreq = it) }
        FxSlider("Radio / vox", params.radio, 0f..1f) { params = params.copy(radio = it) }
        FxSlider("Distortion", params.distortion, 0f..1f) { params = params.copy(distortion = it) }
        FxSlider("Bitcrush", params.bitcrush, 0f..1f) { params = params.copy(bitcrush = it) }
        FxSlider("Wobble", params.tremolo, 0f..1f) { params = params.copy(tremolo = it) }
        FxSlider("Echo", params.echo, 0f..1f) { params = params.copy(echo = it) }
        FxSlider("Reverb", params.reverb, 0f..1f) { params = params.copy(reverb = it) }
        FxSlider("Demon double", params.doubleLow, 0f..1f) { params = params.copy(doubleLow = it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse", modifier = Modifier.weight(1f))
            Switch(checked = params.reverse, onCheckedChange = { params = params.copy(reverse = it) })
        }

        // ---- output
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { preview() }, enabled = src != null && !busy && !recording, modifier = Modifier.testTag("voice_fx_preview")) { Text("Preview") }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                val s = source ?: return@FilledTonalButton
                busy = true
                scope.launch {
                    runCatching {
                        val out = withContext(Dispatchers.Default) { VoiceFx.process(s, params) }
                        withContext(Dispatchers.IO) {
                            repo.saveNew(File(project, "voice"), (ProjectLayout.safeName(name.trim().ifBlank { "voice_fx" }) ?: "voice_fx") + ".wav", out.toWav())
                        }
                    }.onSuccess { message = "Saved voice/${it.name}"; refresh++ }
                        .onFailure { ErrorLog.error("Voice FX save failed", it); message = "Save failed: ${it.message}" }
                    busy = false
                }
            }, enabled = src != null && !busy && !recording, modifier = Modifier.testTag("voice_fx_save")) { Text("Save with effect") }
            if (sourceName == "Recording" && src != null) OutlinedButton(onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repo.saveNew(File(project, "voice"), "recording.wav", src.toWav()) } }
                        .onSuccess { message = "Saved voice/${it.name}"; refresh++ }
                }
            }) { Text("Save original") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String = "%.0f%%", onChange: (Float) -> Unit) {
    val shown = if (format == "%.0f%%") String.format(Locale.US, format, value * 100) else String.format(Locale.US, format, value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(shown, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp).background(MaterialTheme.colorScheme.background))
    }
}
