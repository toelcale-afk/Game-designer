package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.GitHubTarget
import com.example.data.ClaudeApkConverter
import com.example.data.GitHubBuildUi
import com.example.model.ClaudeApkConfig
import com.example.model.ClaudeArtifactType
import com.example.model.ClaudeDiagnosticIssue
import com.example.model.ClaudeSourceFile

/**
 * Pipeline step 3: code diagnostics, project ZIP packaging, the offline quick build
 * (HTML/JS apps) and the GitHub Actions build.
 */
@Composable
fun CompileCheckScreen(
    config: ClaudeApkConfig,
    files: List<ClaudeSourceFile>,
    diagnostics: List<ClaudeDiagnosticIssue>,
    runtimeErrors: List<String>,
    gitHub: GitHubBuildUi,
    quickBuild: @Composable () -> Unit,
    onPackageZip: () -> Unit,
    onSaveGitHub: (GitHubTarget, String?) -> Unit,
    onClearToken: () -> Unit,
    onPushAndBuild: () -> Unit,
    onCancelBuild: () -> Unit,
    onSendToInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("compile_check_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ClaudeDiagnosticsCard(
                config = config,
                files = files,
                diagnostics = diagnostics,
                runtimeErrors = runtimeErrors,
                onCopyReport = {
                    val report = ClaudeApkConverter.buildCopyableErrorReportForClaude(config, files, diagnostics, runtimeErrors)
                    copyTextToClipboard(context, "Error report", report)
                }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Android project ZIP", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Packages ${config.appName} as a complete Gradle project (sources, icon, error catcher, GitHub workflow). Opens the Bundler to share or save it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPackageZip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("package_project_zip_button")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Package project ZIP")
                    }
                }
            }
        }

        if (config.artifactType != ClaudeArtifactType.KOTLIN_COMPOSE) {
            item { quickBuild() }
        }

        item {
            GitHubBuildPanel(
                state = gitHub,
                appName = config.appName,
                onSave = onSaveGitHub,
                onClearToken = onClearToken,
                onPushAndBuild = onPushAndBuild,
                onCancel = onCancelBuild,
                onOpenRun = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                onCopyLog = { copyTextToClipboard(context, "Build log", it) },
                onSendToInstall = onSendToInstall
            )
        }
    }
}

@Composable
private fun GitHubBuildPanel(
    state: GitHubBuildUi,
    appName: String,
    onSave: (GitHubTarget, String?) -> Unit,
    onClearToken: () -> Unit,
    onPushAndBuild: () -> Unit,
    onCancel: () -> Unit,
    onOpenRun: (String) -> Unit,
    onCopyLog: (String) -> Unit,
    onSendToInstall: () -> Unit
) {
    var owner by rememberSaveable(state.target.owner) { mutableStateOf(state.target.owner) }
    var repo by rememberSaveable(state.target.repo) { mutableStateOf(state.target.repo) }
    var branch by rememberSaveable(state.target.branch) { mutableStateOf(state.target.branch) }
    // The token field is never saved in UI state; it's encrypted as soon as Save is tapped.
    var token by remember { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(!state.target.isComplete || !state.hasToken) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_build_panel"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Build on GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.target.isComplete) "${state.target.owner}/${state.target.repo} @ ${state.target.branch}"
                        else "Works for every app type, including Kotlin/Compose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "Hide settings" else "Settings")
                }
            }

            if (showSettings) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = owner, onValueChange = { owner = it.trim() },
                        label = { Text("Owner") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repo, onValueChange = { repo = it.trim() },
                        label = { Text("Repository") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = branch, onValueChange = { branch = it.trim() },
                    label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (state.hasToken) "Token saved (type to replace)" else "GitHub token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input")
                )
                Text(
                    "Fine-grained token for this one repository with Contents: Read and write, Actions: Read and Workflows: Read and write. It's encrypted with the Android Keystore and never leaves this phone except to api.github.com.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        onSave(GitHubTarget(owner, repo, branch.ifBlank { "main" }), token.ifBlank { null })
                        token = ""
                        showSettings = false
                    }) { Text("Save") }
                    if (state.hasToken) {
                        OutlinedButton(onClick = onClearToken) { Text("Remove token") }
                    }
                }
            }

            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(state.progress ?: "Working…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Stop") }
                }
            } else {
                state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = onPushAndBuild,
                    enabled = state.target.isComplete && state.hasToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("github_push_build_button")
                ) { Text("Push $appName & build APK") }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.run?.let { run ->
                TextButton(onClick = { onOpenRun(run.htmlUrl) }) { Text("Open build #${run.id} on GitHub") }
            }
            state.logExcerpt?.let { log ->
                Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Build log excerpt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onCopyLog(log) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }
                        SelectionContainer {
                            Text(
                                log.lines().take(80).joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            state.apkFile?.let { apk ->
                Button(
                    onClick = onSendToInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_send_to_install_button")
                ) { Text("Next: 4 Install ${apk.name}") }
            }
        }
    }
}
