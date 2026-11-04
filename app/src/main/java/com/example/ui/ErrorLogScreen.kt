package com.example.ui

import android.os.Build
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CaughtErrorLogEntry
import com.example.model.ClaudeDiagnosticIssue
import com.example.model.RuntimeApkTelemetry

@Composable
fun ErrorLogScreen(
    telemetry: RuntimeApkTelemetry?,
    caughtExceptions: List<CaughtErrorLogEntry>,
    claudeDiagnostics: List<ClaudeDiagnosticIssue>,
    onCopyLog: () -> String,
    onTriggerTestException: () -> Unit,
    onClearLogs: () -> Unit,
    onCopySuccessBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val androidVersionText = if (telemetry != null) {
        "Android ${telemetry.deviceRelease} (API ${telemetry.deviceSdkInt})"
    } else {
        "Android ${Build.VERSION.RELEASE ?: "16"} (API ${Build.VERSION.SDK_INT})"
    }
    val appVersionText = if (telemetry != null) {
        "${telemetry.appLabel} v${telemetry.versionName} (Code ${telemetry.versionCode})"
    } else {
        "PackFlow Studio v1.0 (Code 1)"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("error_log_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header showing Android Version, App Version, and "Copy log" Button
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .testTag("error_log_header_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Error Log",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System & Exception Error Log",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Android Version",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = androidVersionText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("error_log_android_version")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "App Version",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = appVersionText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.testTag("error_log_app_version")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val fullLog = onCopyLog()
                                copyTextToClipboard(context, "PackFlow Error Log", fullLog)
                                onCopySuccessBanner("Copied full error log (with Android & App version + line numbers) to clipboard!")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_log_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy log")
                        }

                        FilledTonalButton(
                            onClick = onTriggerTestException,
                            modifier = Modifier.testTag("trigger_test_exception_button")
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Error")
                        }

                        if (caughtExceptions.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearLogs,
                                modifier = Modifier.testTag("clear_error_log_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        if (caughtExceptions.isEmpty() && claudeDiagnostics.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No Unhandled Exceptions Logged",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Every caught exception will appear here with its exception type, message, plain-English explanation, and the exact file and line number where it occurred. Tap 'Test Error' above to verify.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(
            items = caughtExceptions,
            key = { it.id }
        ) { entry ->
            CaughtExceptionCard(
                entry = entry,
                onCopySingle = {
                    val singleText = """
Android Version: $androidVersionText
App Version: $appVersionText
Error Type: ${entry.errorType}
Location: ${entry.fileName}:line ${entry.lineNumber} (${entry.methodName})
Message: ${entry.message}
Explanation: ${entry.plainEnglishExplanation}
Stack Trace:
${entry.rawStackTraceSnippet}
                    """.trimIndent()
                    copyTextToClipboard(context, "Exception ${entry.errorType}", singleText)
                    onCopySuccessBanner("Copied ${entry.errorType} (line ${entry.lineNumber}) to clipboard!")
                }
            )
        }

        if (claudeDiagnostics.isNotEmpty()) {
            item {
                Text(
                    text = "Static & Claude File Diagnostics (${claudeDiagnostics.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = claudeDiagnostics,
                key = { it.id }
            ) { diag ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "[${diag.severity.label}] ${diag.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "File: ${diag.fileName} • Line: ${diag.lineNumber ?: 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = diag.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Suggested Fix: ${diag.suggestedFix}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaughtExceptionCard(
    entry: CaughtErrorLogEntry,
    onCopySingle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("exception_card_${entry.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = entry.errorType,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${entry.fileName} • Line ${entry.lineNumber} • ${entry.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onCopySingle,
                    modifier = Modifier.testTag("copy_single_exception_${entry.id}")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy error", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
            }

            HorizontalDivider()

            Text(
                text = "Message: ${entry.message}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = entry.plainEnglishExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = entry.rawStackTraceSnippet,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
