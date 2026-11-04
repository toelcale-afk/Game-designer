package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.ErrorCatcher
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.ClaudeApkConverter
import com.example.data.ClaudePresetTemplate
import com.example.model.ClaudeApkConfig
import com.example.model.ClaudeDiagnosticIssue
import com.example.model.ClaudeSourceFile
import com.example.model.ConsoleLogMessage
import com.example.model.DiagnosticSeverity

/** Pipeline step 1: paste/import code (or load a project) and run it in the live device frame. */
@Composable
fun PreviewScreen(
    rawInput: String,
    config: ClaudeApkConfig,
    extractedFiles: List<ClaudeSourceFile>,
    diagnostics: List<ClaudeDiagnosticIssue>,
    consoleLogs: List<ConsoleLogMessage>,
    isFullScreenPreview: Boolean,
    onRawInputChange: (String) -> Unit,
    onSelectPreset: (ClaudePresetTemplate) -> Unit,
    onUpdateConfig: ((ClaudeApkConfig) -> ClaudeApkConfig) -> Unit,
    onImportClaudeFile: () -> Unit,
    onNext: () -> Unit,
    onAppendConsoleLog: (String, String, Int) -> Unit,
    onClearConsole: () -> Unit,
    onToggleFullScreenPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var reloadNonce by remember { mutableIntStateOf(0) }

    val previewHtml = remember(config, extractedFiles, reloadNonce) {
        ClaudeApkConverter.buildStandaloneWebApkHtml(config, extractedFiles)
    }

    if (isFullScreenPreview) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${config.appName} • Live APK Preview",
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    IconButton(onClick = { reloadNonce++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload preview")
                    }
                    IconButton(onClick = { onToggleFullScreenPreview(false) }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit full screen")
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                key(reloadNonce, previewHtml) {
                    EmbeddedClaudeWebView(
                        htmlContent = previewHtml,
                        onConsoleMessage = onAppendConsoleLog,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("claude_studio_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.claude_header_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.claude_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClaudeApkConverter.presets.forEach { preset ->
                            FilterChip(
                                selected = config.appName == preset.config.appName,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.title) },
                                modifier = Modifier.testTag("claude_preset_${preset.id}")
                            )
                        }
                    }
                }
            }
        }

        // Live Emulator Phone Frame Preview ("a preview like this as well")
        item {
            LiveEmulatorPreviewCard(
                config = config,
                previewHtml = previewHtml,
                reloadNonce = reloadNonce,
                consoleLogs = consoleLogs,
                onReload = { reloadNonce++ },
                onExpandFullScreen = { onToggleFullScreenPreview(true) },
                onAppendConsoleLog = onAppendConsoleLog,
                onClearConsole = onClearConsole
            )
        }

        // Code input card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Claude Source Files (${extractedFiles.size} detected)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Mode: ${config.artifactType.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(
                            onClick = onImportClaudeFile,
                            modifier = Modifier.testTag("import_claude_file_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import File")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = config.appName,
                            onValueChange = { name -> onUpdateConfig { it.copy(appName = name.take(30)) } },
                            label = { Text("APK App Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("claude_app_name_input")
                        )
                        OutlinedTextField(
                            value = config.applicationId,
                            onValueChange = { pkg -> onUpdateConfig { it.copy(applicationId = pkg.trim()) } },
                            label = { Text("Package ID") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("claude_package_id_input")
                        )
                    }

                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = onRawInputChange,
                        label = { Text("Paste Claude Code or Multi-Block Response (HTML, JSX, TSX, CSS, Kotlin)") },
                        minLines = 7,
                        maxLines = 14,
                        textStyle = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("claude_raw_code_input")
                    )

                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("preview_next_button")
                    ) {
                        Text(
                            if (diagnostics.isEmpty()) "Next: 2 Icon"
                            else "Next: 2 Icon (${diagnostics.size} issue(s) to check in step 3)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEmulatorPreviewCard(
    config: ClaudeApkConfig,
    previewHtml: String,
    reloadNonce: Int,
    consoleLogs: List<ConsoleLogMessage>,
    onReload: () -> Unit,
    onExpandFullScreen: () -> Unit,
    onAppendConsoleLog: (String, String, Int) -> Unit,
    onClearConsole: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_emulator_preview_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Live Android APK Device Preview",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${config.appName} (${config.applicationId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier.testTag("reload_preview_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload live preview")
                    }
                    IconButton(
                        onClick = onExpandFullScreen,
                        modifier = Modifier.testTag("fullscreen_preview_button")
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen preview")
                    }
                }
            }

            // Realistic Android Phone Frame Bezel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF070B14))
                    .border(
                        width = 4.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0B1120))
                ) {
                    // Simulated Phone Status Bar + Camera Punch-Hole
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "9:41",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                        Text(
                            text = "5G • 100%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34D399)
                        )
                    }

                    // Interactive WebView Runtime
                    Box(modifier = Modifier.weight(1f)) {
                        key(reloadNonce, previewHtml) {
                            EmbeddedClaudeWebView(
                                htmlContent = previewHtml,
                                onConsoleMessage = onAppendConsoleLog,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Simulated Android Bottom Gesture Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(108.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.45f))
                        )
                    }
                }
            }

            // Live Console Stream Footer
            if (consoleLogs.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Preview Console (${consoleLogs.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(2.dp)
                            )
                        }
                        consoleLogs.take(3).forEach { log ->
                            val isErr = log.level.equals("ERROR", ignoreCase = true)
                            Text(
                                text = "[${log.timestampLabel}] L${log.lineNumber}: ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClaudeDiagnosticsCard(
    config: ClaudeApkConfig,
    files: List<ClaudeSourceFile>,
    diagnostics: List<ClaudeDiagnosticIssue>,
    runtimeErrors: List<String>,
    onCopyReport: () -> Unit
) {
    val hasIssues = diagnostics.isNotEmpty() || runtimeErrors.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasIssues) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("claude_diagnostics_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (hasIssues) Icons.Default.BugReport else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (hasIssues) {
                                "${diagnostics.size + runtimeErrors.size} Error(s) / Issue(s) Described"
                            } else {
                                "0 Errors — Ready for APK Packaging"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Copyable plain-English error report to paste back into Claude",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            diagnostics.forEach { issue ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (issue.severity == DiagnosticSeverity.ERROR) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[${issue.severity.label}] ${issue.title} (${issue.fileName}${issue.lineNumber?.let { ":Line $it" } ?: ""})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = issue.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fix: ${issue.suggestedFix}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            runtimeErrors.forEach { runtimeErr ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Runtime Exception: $runtimeErr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = onCopyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_claude_error_report_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Error Description & Fix Prompt for Claude")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedClaudeWebView(
    htmlContent: String,
    onConsoleMessage: (level: String, message: String, lineNumber: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.testTag("embedded_claude_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(PreviewErrorBridge(ctx.applicationContext), ErrorCatcher.BRIDGE_NAME)
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null) {
                            onConsoleMessage(
                                consoleMessage.messageLevel()?.name ?: "LOG",
                                consoleMessage.message() ?: "",
                                consoleMessage.lineNumber()
                            )
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

/** window.PackFlowAndroid inside PackFlow's own preview: web errors go to PackFlow's Error Log. */
class PreviewErrorBridge(private val context: Context) {
    @JavascriptInterface
    fun logError(json: String) {
        ErrorLog.error("Preview: " + json.take(600))
    }

    @JavascriptInterface
    fun copyText(text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            copyTextToClipboard(context, "Preview errors", text)
        }
    }

    @JavascriptInterface
    fun clearErrors() {
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
