package com.aistudio.packflow.xkrv.installer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class InstallerScreen { Pick, Preview, Log, HtmlPreview }

/**
 * Process-level state so folding/unfolding the Fold (a config change) doesn't
 * lose the parsed APK or cancel work in progress.
 */
object InstallerSession {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var screen by mutableStateOf(InstallerScreen.Pick)
    var apk by mutableStateOf<ApkInfo?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun open(context: Context, uri: Uri) {
        busy = true
        message = null
        InstallEvents.set(InstallState.Idle)
        scope.launch {
            try {
                apk = ApkInspector.inspect(context.applicationContext, uri)
                screen = InstallerScreen.Preview
            } catch (t: Throwable) {
                ErrorLog.error("Could not read the selected file", t)
                message = t.message ?: "Could not read the selected file"
            } finally {
                busy = false
            }
        }
    }

    /** Opens an APK file PackFlow produced itself (pipeline step 3 -> 4). */
    fun openFile(context: Context, file: java.io.File) {
        busy = true
        message = null
        InstallEvents.set(InstallState.Idle)
        scope.launch {
            try {
                apk = ApkInspector.inspectFile(context.applicationContext, file)
                screen = InstallerScreen.Preview
            } catch (t: Throwable) {
                ErrorLog.error("Could not read the built APK", t)
                message = t.message ?: "Could not read the built APK"
                screen = InstallerScreen.Pick
            } finally {
                busy = false
            }
        }
    }

    fun openSample(context: Context) {
        busy = true
        message = null
        InstallEvents.set(InstallState.Idle)
        scope.launch {
            try {
                apk = ApkInspector.inspectSampleApk(context.applicationContext)
                screen = InstallerScreen.Preview
            } catch (t: Throwable) {
                ErrorLog.error("Could not load sample APK", t)
                message = t.message ?: "Could not load sample APK"
            } finally {
                busy = false
            }
        }
    }

    fun cancelAndCleanup(context: Context) {
        ApkInspector.deleteCachedCandidates(context.applicationContext)
        apk = null
        InstallEvents.set(InstallState.Idle)
        screen = InstallerScreen.Pick
        message = "Cancelled preview and deleted temporary copied APK."
    }

    fun install(context: Context) {
        val info = apk ?: return
        val minSdk = info.minSdk
        if (minSdk != null && minSdk > Build.VERSION.SDK_INT) {
            val err = "Cannot install: APK requires minSdk $minSdk, but this device is running API ${Build.VERSION.SDK_INT}."
            ErrorLog.error(err)
            InstallEvents.set(InstallState.Failed(err, -2))
            return
        }
        scope.launch { ApkInstaller.install(context.applicationContext, info.file) }
    }
}

/** Entry point. Call from MainActivity's setContent { ... } or from a tab/route. */
@Composable
fun PackFlowInstaller(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { ErrorLog.init(context); true }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) InstallerSession.open(context, uri)
    }
    val pick = {
        try {
            picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
        } catch (t: Throwable) {
            ErrorLog.error("Could not launch file picker", t)
        }
    }

    BackHandler(enabled = InstallerSession.screen != InstallerScreen.Pick) {
        InstallerSession.screen =
            if ((InstallerSession.screen == InstallerScreen.Log || InstallerSession.screen == InstallerScreen.HtmlPreview) && InstallerSession.apk != null)
                InstallerScreen.Preview else InstallerScreen.Pick
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (InstallerSession.screen) {
            InstallerScreen.Pick -> PickScreen(
                onPick = pick,
                onLoadSample = { InstallerSession.openSample(context) },
                onOpenHtmlPreview = { InstallerSession.screen = InstallerScreen.HtmlPreview }
            )
            InstallerScreen.Preview -> InstallerSession.apk?.let {
                ApkPreviewScreen(
                    info = it,
                    onPickAnother = pick,
                    onCancel = { InstallerSession.cancelAndCleanup(context) }
                )
            } ?: PickScreen(
                onPick = pick,
                onLoadSample = { InstallerSession.openSample(context) },
                onOpenHtmlPreview = { InstallerSession.screen = InstallerScreen.HtmlPreview }
            )
            InstallerScreen.Log -> ErrorLogScreen(onBack = {
                InstallerSession.screen =
                    if (InstallerSession.apk != null) InstallerScreen.Preview else InstallerScreen.Pick
            })
            InstallerScreen.HtmlPreview -> HtmlPreviewScreen(onBack = {
                InstallerSession.screen =
                    if (InstallerSession.apk != null) InstallerScreen.Preview else InstallerScreen.Pick
            })
        }
    }
}

@Composable
private fun PickScreen(
    onPick: () -> Unit,
    onLoadSample: () -> Unit,
    onOpenHtmlPreview: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("installer_pick_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("APK Installer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose an .apk to check its name, version, permissions and signature before installing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (InstallerSession.busy) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Reading APK…")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPick,
                    modifier = Modifier.testTag("choose_apk_file_button")
                ) {
                    Text("Choose APK file")
                }
                FilledTonalButton(
                    onClick = onLoadSample,
                    modifier = Modifier.testTag("load_sample_apk_button")
                ) {
                    Text("Load sample APK")
                }
            }
        }
        InstallerSession.message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = { InstallerSession.screen = InstallerScreen.Log },
                modifier = Modifier.testTag("view_error_log_button")
            ) {
                Text("View error log")
            }
            TextButton(
                onClick = onOpenHtmlPreview,
                modifier = Modifier.testTag("open_html_preview_button")
            ) {
                Text("Browser HTML preview")
            }
        }
    }
}

@Composable
private fun ApkPreviewScreen(
    info: ApkInfo,
    onPickAnother: () -> Unit,
    onCancel: () -> Unit
) {
    // Wide layout on the Fold's inner screen, single column on the cover screen.
    BoxWithConstraints(Modifier.fillMaxSize().testTag("apk_preview_screen")) {
        if (maxWidth >= 700.dp) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Header(info)
                    Spacer(Modifier.height(12.dp))
                    Actions(info, onPickAnother, onCancel)
                    Spacer(Modifier.height(12.dp))
                    Details(info)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Signing(info)
                    Spacer(Modifier.height(12.dp))
                    Permissions(info)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Header(info)
                Spacer(Modifier.height(12.dp))
                Actions(info, onPickAnother, onCancel)
                Spacer(Modifier.height(12.dp))
                Details(info)
                Spacer(Modifier.height(12.dp))
                Signing(info)
                Spacer(Modifier.height(12.dp))
                Permissions(info)
            }
        }
    }
}

@Composable
private fun Header(info: ApkInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = info.icon
        if (icon != null) {
            Image(
                icon.asImageBitmap(),
                contentDescription = "App icon",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
            )
        } else {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("APK") }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(info.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(info.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("Version ${info.versionName ?: "?"} (${info.versionCode})", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Actions(
    info: ApkInfo,
    onPickAnother: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by InstallEvents.state.collectAsState()
    var allowed by remember { mutableStateOf(ApkInstaller.canInstall(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = ApkInstaller.canInstall(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val matchesSelfPackage = info.packageName == context.packageName
    val minSdkIncompatible = info.minSdk != null && info.minSdk > Build.VERSION.SDK_INT

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (matchesSelfPackage) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Warning: This APK has the same package name as PackFlow (${info.packageName}). Installing will update or replace PackFlow itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (minSdkIncompatible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Incompatible Android version: This APK requires API ${info.minSdk}, but this device runs API ${Build.VERSION.SDK_INT}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (!allowed) {
                Text(
                    "PackFlow needs permission to install apps. Turn on \"Allow from this source\", then come back.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        try {
                            context.startActivity(ApkInstaller.unknownSourcesSettings(context))
                        } catch (t: Throwable) {
                            ErrorLog.error("Could not open unknown sources settings", t)
                        }
                    },
                    modifier = Modifier.testTag("open_install_permission_button")
                ) {
                    Text("Open install permission")
                }
            } else {
                val working = state is InstallState.Writing || state is InstallState.WaitingForUser
                Button(
                    onClick = { InstallerSession.install(context) },
                    enabled = !working && !minSdkIncompatible && info.signers.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("install_apk_button")
                ) {
                    Text("Install ${info.label}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickAnother,
                    modifier = Modifier.testTag("choose_another_button")
                ) {
                    Text("Choose another")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_cleanup_button")
                ) {
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick = { InstallerSession.screen = InstallerScreen.Log },
                    modifier = Modifier.testTag("preview_error_log_button")
                ) {
                    Text("Error log")
                }
            }
            val (text, isError) = when (val s = state) {
                InstallState.Idle -> null to false
                InstallState.Writing -> "Preparing install…" to false
                InstallState.WaitingForUser -> "Confirm in the system dialog." to false
                is InstallState.Success -> "Installed (status code ${s.statusCode}). Temp APK cleaned." to false
                is InstallState.Failed -> s.reason to true
            }
            if (text != null) {
                Spacer(Modifier.height(8.dp))
                Text(text, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Details(info: ApkInfo) {
    Section("Details") {
        Field("File", info.fileName)
        Field("Size", "%.2f MB (%,d bytes)".format(info.sizeBytes / 1_048_576.0, info.sizeBytes))
        Field("Min / target SDK", "${info.minSdk ?: "?"} / ${info.targetSdk ?: "?"} (Device: API ${Build.VERSION.SDK_INT})")
        Field("File SHA-256", info.fileSha256, mono = true, copyable = true)
    }
}

@Composable
private fun Signing(info: ApkInfo) {
    Section("Signing") {
        if (info.signers.isEmpty()) {
            Text(
                "No signing certificate found. Android will refuse to install this APK.",
                color = MaterialTheme.colorScheme.error
            )
        }
        info.signers.forEachIndexed { i, s ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Field("Subject", s.subject)
            Field("Cert SHA-256", s.sha256, mono = true, copyable = true)
            Field("Cert SHA-1", s.sha1, mono = true, copyable = true)
            if (s.isDebugKey) Text(
                "Debug key: fine for testing, not for release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun Permissions(info: ApkInfo) {
    val sensitive = info.permissions.count { it.sensitive }
    Section("Permissions (${info.permissions.size}, $sensitive sensitive)") {
        if (info.permissions.isEmpty()) Text("No permissions requested.")
        info.permissions.forEach { p ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name.removePrefix("android.permission."),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (p.sensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (p.sensitive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (p.sensitive) Text("sensitive / dangerous", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false, copyable: Boolean = false) {
    val context = LocalContext.current
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (copyable) TextButton(onClick = { copy(context, label, value) }) { Text("Copy") }
        }
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

@Composable
fun ErrorLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("claude_error_log_screen")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Error log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = ErrorLog.systemHeader,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { copy(context, "PackFlow installer log", ErrorLog.asText()) },
                modifier = Modifier.testTag("copy_log_button")
            ) {
                Text("Copy log")
            }
            OutlinedButton(
                onClick = { ErrorLog.clear() },
                modifier = Modifier.testTag("clear_log_button")
            ) {
                Text("Clear")
            }
            OutlinedButton(
                onClick = {
                    try {
                        throw ApkInspectException("Sample test error logged with exact line number")
                    } catch (t: Throwable) {
                        ErrorLog.error("Diagnostic test exception triggered", t)
                    }
                },
                modifier = Modifier.testTag("test_error_log_button")
            ) {
                Text("Log test error")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ErrorLog.entries.isEmpty()) {
            Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ErrorLog.entries.reversed()) { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (" ERROR " in line) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "preview/apk-preview.html (Offline Browser View)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("Back to Native Installer") }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("file:///android_asset/apk-preview.html")
                }
            }
        )
    }
}

private fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
