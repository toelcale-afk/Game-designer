package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.packflow.xkrv.installer.InstallerSession
import com.example.core.ShellApk

/**
 * Step 3 "Quick build (offline)": HTML/JS apps only. Builds and signs an APK on the phone
 * from the prebuilt WebView shell, without internet.
 */
@Composable
fun QuickBuildCard(viewModel: PackFlowViewModel, onSendToInstall: () -> Unit) {
    val context = LocalContext.current
    val ui by viewModel.quickBuild.state.collectAsStateWithLifecycle()
    val app by viewModel.uiState.collectAsStateWithLifecycle()
    val icons by viewModel.iconMaker.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.quickBuild.refresh(context) }

    val config = app.claudeConfig
    var packageName by rememberSaveable(config.applicationId) {
        mutableStateOf(
            if (ShellApk.isValidPackageName(config.applicationId)) config.applicationId
            else ShellApk.suggestPackageName(config.appName)
        )
    }
    var versionName by rememberSaveable { mutableStateOf(config.versionName.ifBlank { "1.0" }) }
    var versionCode by rememberSaveable { mutableStateOf("1") }
    var fullscreen by rememberSaveable { mutableStateOf(true) }
    var orientation by rememberSaveable { mutableStateOf("sensor") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_build_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Quick build (offline)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "HTML/JS apps: builds and signs the APK on this phone in seconds, no internet needed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!ui.shellBundled) {
                Text(
                    "This copy of PackFlow was built without the app shell (CI adds it to assets/shell/). Use Build on GitHub below, or install a PackFlow build from the GitHub workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it.trim().lowercase() },
                label = { Text("Package name (unique per app)") },
                singleLine = true,
                isError = !ShellApk.isValidPackageName(packageName),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = versionName, onValueChange = { versionName = it.take(20) },
                    label = { Text("Version") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = versionCode, onValueChange = { v -> versionCode = v.filter { it.isDigit() }.take(9) },
                    label = { Text("Version code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "To update an installed copy, keep the package name and raise the version code.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Full screen", modifier = Modifier.weight(1f))
                Switch(checked = fullscreen, onCheckedChange = { fullscreen = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("sensor" to "Any", "landscape" to "Landscape", "portrait" to "Portrait").forEach { (value, label) ->
                    FilterChip(selected = orientation == value, onClick = { orientation = value }, label = { Text(label) })
                }
            }
            Text(
                if (icons.hasIcon) "Icon: from step 2 ✓" else "Icon: PackFlow default (make one in step 2)",
                style = MaterialTheme.typography.labelSmall
            )

            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(ui.progress ?: "Building…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Button(
                    onClick = {
                        viewModel.startQuickBuild(
                            context, packageName, versionName,
                            versionCode.toIntOrNull() ?: 1, fullscreen, orientation
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("quick_build_button")
                ) { Text("Build APK offline") }
                ui.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            ui.apk?.let { apk ->
                Button(
                    onClick = {
                        InstallerSession.openFile(context, apk)
                        onSendToInstall()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_build_install_button")
                ) { Text("Next: 4 Install ${apk.name}") }
            }
            ui.signerFingerprint?.let {
                Text(
                    "Signed with this phone's PackFlow key (SHA-256 ${it.take(17)}…). Apps built elsewhere with another key can't update these.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
