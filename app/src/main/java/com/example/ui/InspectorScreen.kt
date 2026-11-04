package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PackagerEngine
import com.example.model.RuntimeApkTelemetry

@Composable
fun InspectorScreen(
    telemetry: RuntimeApkTelemetry?,
    onRefresh: () -> Unit,
    onStageReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("inspector_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "APK Inspector",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.inspector_header),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        FilledTonalButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("refresh_telemetry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh telemetry")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                    Text(
                        text = stringResource(R.string.inspector_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (telemetry != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Installed APK Container Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TelemetryRow("Application Label", telemetry.appLabel)
                        TelemetryRow("Package Name", telemetry.packageName)
                        TelemetryRow("Version", "${telemetry.versionName} (Code ${telemetry.versionCode})")
                        TelemetryRow("Target / Min SDK", "API ${telemetry.targetSdk} / Min API ${telemetry.minSdk}")
                        TelemetryRow("Host Android OS", "Android ${telemetry.deviceRelease} (API ${telemetry.deviceSdkInt})")
                        TelemetryRow("Device / ABI", "${telemetry.deviceModel} • ${telemetry.primaryAbi}")
                        TelemetryRow(
                            "Compiled APK Size",
                            if (telemetry.apkSizeBytes > 0) PackagerEngine.formatBytes(telemetry.apkSizeBytes) else "Packaged in Split/Container"
                        )
                        TelemetryRow("Build Mode", if (telemetry.isDebuggable) "Debug Signed" else "Release Signed")
                        TelemetryRow("Signing SHA-256", telemetry.signingCertSha256)

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onStageReport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stage_telemetry_report_button")
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stage JSON Telemetry Report into Bundler")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Play Creator Program Audit",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        AuditCheckItem("Zero Prohibited Permissions (No SMS/CallLog/Accessibility)")
                        AuditCheckItem("Zero-Permission Photo Picker & SAF Document Contracts")
                        AuditCheckItem("Custom Adaptive Icon (Foreground + Background + Round PNGs)")
                        AuditCheckItem("No Dynamic Code Loading (.dex / .so / .jar)")

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Declared Manifest Permissions (${telemetry.declaredPermissions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (telemetry.declaredPermissions.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Zero runtime or install-time permissions requested (Least-Privilege Sandbox)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            telemetry.declaredPermissions.forEach { perm ->
                                Text(
                                    text = "• $perm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun AuditCheckItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Passed",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
