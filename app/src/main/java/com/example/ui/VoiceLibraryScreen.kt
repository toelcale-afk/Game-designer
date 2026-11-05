package com.example.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.FreesoundApi
import com.example.core.OnlineSound
import com.example.core.SoundLicense
import com.example.data.FreesoundClient
import com.example.data.FreesoundSettings
import com.example.data.ProjectRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice library: searches the online Freesound database for voice clips (battle cries,
 * grunts, commands, laughs…), plays previews and pulls them into the project's voice/ folder
 * with their credits. Uses the user's own free Freesound API key.
 */
@Composable
fun VoiceLibraryScreen(project: File, onChangeVoice: (File) -> Unit) {
    val context = LocalContext.current
    val settings = remember { FreesoundSettings(context) }
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(settings.hasKey) }
    var keyInput by remember { mutableStateOf("") }
    var query by remember { mutableStateOf(FreesoundApi.VOICE_CATEGORIES.first().query) }
    var licenses by remember { mutableStateOf(setOf(SoundLicense.CC0)) }
    var maxSeconds by remember { mutableFloatStateOf(6f) }
    var results by remember { mutableStateOf<List<OnlineSound>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var added by remember { mutableStateOf(setOf<Long>()) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stopPreview() {
        player[0]?.let { runCatching { it.stop() }; it.release() }
        player[0] = null
        playingId = null
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    fun play(s: OnlineSound) {
        stopPreview()
        val url = s.previewUrl ?: run { message = "This sound has no preview."; return }
        playingId = s.id
        player[0] = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stopPreview() }
            setOnErrorListener { _, _, _ -> message = "Couldn't play the preview (check the connection)."; stopPreview(); true }
            runCatching { setDataSource(url); prepareAsync() }.onFailure { stopPreview() }
        }
    }

    fun search(newPage: Int) {
        val key = settings.key() ?: run { hasKey = false; return }
        busy = true
        message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { FreesoundClient.search(key, FreesoundApi.searchUrl(query, licenses, maxSeconds.toDouble(), newPage)) }
            }.onSuccess { p ->
                results = if (newPage == 1) p.results else results + p.results
                total = p.count
                page = newPage
                hasNext = p.hasNext
                if (p.results.isEmpty() && newPage == 1) message = "No matches. Try other words or allow more licenses."
            }.onFailure { ErrorLog.error("Freesound search failed", it); message = it.message ?: "Search failed (no connection?)" }
            busy = false
        }
    }

    fun add(s: OnlineSound, thenChange: Boolean) {
        val url = s.previewUrl ?: return
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(project, "voice/freesound")
                    val file = FreesoundClient.download(url, File(dir, s.fileName))
                    val credits = File(project, "voice/CREDITS.md")
                    repo.writeText(credits, FreesoundApi.withCredit(if (credits.exists()) credits.readText() else "", s))
                    file
                }
            }.onSuccess { f ->
                added = added + s.id
                message = "Saved voice/freesound/${f.name}" + if (s.license.needsCredit) " (credit added to voice/CREDITS.md)" else ""
                if (thenChange) onChangeVoice(f)
            }.onFailure { ErrorLog.error("Freesound download failed", it); message = "Download failed: ${it.message}" }
            busy = false
        }
    }

    if (!hasKey) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice library (online)", style = MaterialTheme.typography.titleLarge)
            Text(
                "Search Freesound, a free online database with thousands of voice clips: battle cries, grunts, commands, laughs, screams, robot and monster voices. Clips you add go into voice/ and can be changed in Voice FX.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "It needs a free Freesound API key: make an account on freesound.org, open the API page, choose \"create new API credentials\" and copy the Client secret/API key. It's stored encrypted on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FreesoundApi.APPLY_URL))) } }) { Text("Open freesound.org API page") }
            OutlinedTextField(
                value = keyInput, onValueChange = { keyInput = it.trim() }, singleLine = true, label = { Text("Freesound API key") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("freesound_key")
            )
            Button(onClick = { settings.saveKey(keyInput); keyInput = ""; hasKey = settings.hasKey }, enabled = keyInput.length >= 8) { Text("Save key") }
        }
        return
    }

    Column(Modifier.fillMaxSize().testTag("voice_library_screen")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice library (Freesound)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { settings.clearKey(); hasKey = false }) { Text("Remove key") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FreesoundApi.VOICE_CATEGORIES.forEach { c ->
                    FilterChip(selected = query == c.query, onClick = { query = c.query; search(1) }, label = { Text(c.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it.take(60) }, singleLine = true, label = { Text("Search voices") }, modifier = Modifier.weight(1f))
                Button(onClick = { search(1) }, enabled = !busy && query.isNotBlank(), modifier = Modifier.testTag("freesound_search")) { Text("Search") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(SoundLicense.CC0, SoundLicense.BY, SoundLicense.BY_NC).forEach { l ->
                    FilterChip(
                        selected = l in licenses,
                        onClick = { licenses = if (l in licenses) (licenses - l).ifEmpty { setOf(SoundLicense.CC0) } else licenses + l },
                        label = { Text(l.label) }
                    )
                }
            }
            if (SoundLicense.BY_NC in licenses) {
                Text("Non-commercial clips can't be used in a game you sell or show ads in.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.US, "Max length %.0f s", maxSeconds), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
                Slider(value = maxSeconds, onValueChange = { maxSeconds = it }, valueRange = 1f..30f, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (total > 0) "$total matches" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (playingId == s.id) stopPreview() else play(s) }) {
                                Icon(if (playingId == s.id) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Preview ${s.name}")
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.name + if (s.id in added) "  ✓" else "", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    String.format(Locale.US, "%.1f s · by %s · %s", s.duration, s.username, s.license.label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (s.license == SoundLicense.CC0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 48.dp)) {
                            FilledTonalButton(onClick = { add(s, thenChange = false) }, enabled = !busy && s.previewUrl != null) { Text("Add to voice/") }
                            TextButton(onClick = { add(s, thenChange = true) }, enabled = !busy && s.previewUrl != null) { Text("Add + change voice") }
                        }
                    }
                }
            }
            if (hasNext) item {
                TextButton(onClick = { search(page + 1) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
            }
            item {
                Text(
                    "Sounds come from freesound.org and keep their own licenses. CC0 needs no credit; for CC BY you must credit the author (PackFlow keeps voice/CREDITS.md for you).",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
