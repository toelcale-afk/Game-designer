package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.B64
import com.example.core.ErrorCatcher
import com.example.core.ProjectLayout
import com.example.data.GeminiSettings
import com.example.data.ProjectRepository
import java.io.File

private val ASSET_TABS = listOf("Sprites", "Tiles", "Levels", "Sound", "Music", "Voice")

/** Asset Generator: Sprites, Tiles (the Imperium suite), Levels, Sound, Music, Voice. */
@Composable
fun AssetGeneratorScreen(project: File?, onOpenProjects: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showKey by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(0) }
    val repo = remember { ProjectRepository(context) }

    Column(Modifier.fillMaxSize().testTag("asset_generator_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home") }
            Column(Modifier.weight(1f)) {
                Text("Asset Generator", style = MaterialTheme.typography.titleLarge)
                Text(
                    project?.let { "Saving into ${repo.readMeta(it).name}" } ?: "No project open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showKey = true }) { Icon(Icons.Default.Key, contentDescription = "Gemini API key") }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
            ASSET_TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        if (project == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Open or create a project first. Everything you make here is saved into the current project.")
                Button(onClick = onOpenProjects) { Text("Go to Projects") }
            }
            return@Column
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0, 1 -> SuiteWebView(project = project, suiteTab = if (tab == 0) "gen" else "tiles", keyVersion = keyVersion)
                2 -> LevelEditorScreen(project)
                3 -> SoundScreen(project)
                4 -> MusicScreen(project, onNeedKey = { showKey = true })
                else -> VoiceScreen(project, onNeedKey = { showKey = true })
            }
        }
    }

    if (showKey) {
        GeminiKeyDialog(onDismiss = { showKey = false; keyVersion++ })
    }
}

/** Enter/replace/remove the Gemini key. It's encrypted with the Android Keystore. */
@Composable
fun GeminiKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { GeminiSettings(context) }
    var key by remember { mutableStateOf("") }
    var has by remember { mutableStateOf(settings.hasKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (has) "A key is saved (encrypted on this phone). Type a new one to replace it."
                    else "Paste your key from Google AI Studio. It's encrypted with the Android Keystore and only sent to generativelanguage.googleapis.com.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim() }, singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (has) TextButton(onClick = { settings.clearKey(); has = false }) { Text("Remove saved key") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (key.isNotBlank()) settings.saveKey(key)
                onDismiss()
            }) { Text(if (key.isNotBlank()) "Save" else "Close") }
        }
    )
}

/** window.PackFlowKeys for the suite: reads/writes the Keystore-encrypted Gemini key. */
class SuiteKeysBridge(context: Context) {
    private val settings = GeminiSettings(context.applicationContext)

    @JavascriptInterface
    fun geminiKey(): String = settings.key() ?: ""

    @JavascriptInterface
    fun setGeminiKey(key: String) {
        if (key.isBlank()) return // the suite sends "" on load; removing is done in the app's key dialog
        if (key != settings.key()) settings.saveKey(key)
    }
}

/** window.PackFlowProject: lets the suite write into the current project's asset folders. */
class ProjectFilesBridge(context: Context, private val project: File) {
    private val repo = ProjectRepository(context.applicationContext)
    private val allowedTops = ProjectLayout.ASSET_DIRS.toSet() + ProjectLayout.BUILDS

    @JavascriptInterface
    fun saveFile(path: String, base64: String): String = try {
        val clean = path.replace('\\', '/').trimStart('/')
        require(ProjectLayout.isSafeRelative(clean) && clean.substringBefore('/') in allowedTops) { "Not allowed: $path" }
        val target = File(project, clean)
        val saved = repo.saveReplacing(target.parentFile!!, target.name, B64.decode(base64))
        saved.relativeTo(project).invariantSeparatorsPath
    } catch (t: Throwable) {
        ErrorLog.error("Asset suite couldn't save $path", t)
        ""
    }
}

private const val SUITE_HOST = "appassets.androidplatform.net"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SuiteWebView(project: File, suiteTab: String, keyVersion: Int) {
    val context = LocalContext.current
    var pendingChooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingChooser?.onReceiveValue(uris.toTypedArray())
        pendingChooser = null
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(suiteTab, webView) {
        webView?.evaluateJavascript("if(window.switchTab)switchTab('$suiteTab')", null)
    }
    LaunchedEffect(keyVersion, webView) {
        // Pick up a key entered in the app's key dialog.
        webView?.evaluateJavascript(
            "if(window.PackFlowKeys&&typeof cfg!=='undefined'){cfg.key=PackFlowKeys.geminiKey()||'';var k=document.getElementById('key');if(k)k.value=cfg.key}",
            null
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().testTag("asset_suite_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                addJavascriptInterface(SuiteKeysBridge(ctx), "PackFlowKeys")
                addJavascriptInterface(ProjectFilesBridge(ctx, project), "PackFlowProject")
                addJavascriptInterface(PreviewErrorBridge(ctx.applicationContext), ErrorCatcher.BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        if (url.host != SUITE_HOST) return null
                        val path = url.path?.removePrefix("/assets/") ?: return null
                        if (path.contains("..")) return null
                        return runCatching {
                            WebResourceResponse(ProjectLayout.mimeType(path), "UTF-8", ctx.assets.open(path))
                        }.getOrNull()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.host == SUITE_HOST) return false
                        // Links to other sites open in the browser, never inside the key-holding page.
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        view: WebView,
                        callback: ValueCallback<Array<Uri>>,
                        params: FileChooserParams
                    ): Boolean {
                        pendingChooser?.onReceiveValue(null)
                        pendingChooser = callback
                        val types = params.acceptTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
                        return try {
                            chooser.launch(types.toTypedArray())
                            true
                        } catch (t: Throwable) {
                            pendingChooser = null
                            false
                        }
                    }

                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            ErrorLog.error("Asset suite: ${message.message()} (line ${message.lineNumber()})")
                        }
                        return true
                    }
                }
                loadUrl("https://$SUITE_HOST/assets/asset-suite/index.html#tab=$suiteTab")
                webView = this
            }
        }
    )
}
