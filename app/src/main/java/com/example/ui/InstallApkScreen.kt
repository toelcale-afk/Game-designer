package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PackagerEngine
import com.example.model.ApkPermissionItem
import com.example.model.InstallOutcomeType
import com.example.model.InstallPreviewStage
import com.example.model.InstallResultStatus
import com.example.model.PickedApkInfo

@Composable
fun InstallApkScreen(
    canInstallUnknownApps: Boolean,
    pickedApkInfo: PickedApkInfo?,
    apkPreviewError: String?,
    lastInstallResult: InstallResultStatus?,
    installPreviewStage: InstallPreviewStage,
    isPreviewSimulating: Boolean,
    onPickApkDocument: () -> Unit,
    onOpenUnknownAppsSettings: () -> Unit,
    onLoadTestScenario: (String) -> Unit,
    onLaunchSystemInstaller: (PickedApkInfo) -> Unit,
    onCancelAndDeleteApk: () -> Unit,
    onSimulateInstallOutcome: (InstallOutcomeType, Int) -> Unit,
    onRunInstallPreview: () -> Unit,
    onSelectStage: (InstallPreviewStage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("install_apk_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Unknown Apps Permission Check Banner (REQUEST_INSTALL_PACKAGES)
        if (!canInstallUnknownApps) {
            item {
                UnknownAppsPermissionBanner(
                    onOpenSettings = onOpenUnknownAppsSettings
                )
            }
        }

        // 2. Pick APK (ACTION_OPEN_DOCUMENT) & Built-in Test APKs Card
        item {
            ApkDocumentPickerAndTestCard(
                canInstallUnknownApps = canInstallUnknownApps,
                onPickApkDocument = onPickApkDocument,
                onOpenUnknownAppsSettings = onOpenUnknownAppsSettings,
                onLoadTestScenario = onLoadTestScenario
            )
        }

        // 3. Install Outcome Status Banner (Success / Cancelled / Failed with Status Code & Temp File Cleanup)
        if (lastInstallResult != null) {
            item {
                InstallResultStatusCard(result = lastInstallResult)
            }
        }

        // 4. Error Banner if picked file failed ZIP + AndroidManifest.xml verification
        if (apkPreviewError != null) {
            item {
                ApkVerificationFailedCard(
                    errorMessage = apkPreviewError,
                    onCancel = onCancelAndDeleteApk
                )
            }
        }

        // 5. Full "Preview APK" Screen Card
        if (pickedApkInfo != null) {
            item {
                PreviewApkDetailCard(
                    apkInfo = pickedApkInfo,
                    canInstallUnknownApps = canInstallUnknownApps,
                    onInstallClick = { onLaunchSystemInstaller(pickedApkInfo) },
                    onCancelClick = onCancelAndDeleteApk,
                    onOpenUnknownAppsSettings = onOpenUnknownAppsSettings
                )
            }
        }

        // 6. AI Studio Interactive Install Flow Preview & Status Simulator
        item {
            InteractiveInstallFlowPreviewCard(
                pickedApkInfo = pickedApkInfo,
                currentStage = installPreviewStage,
                isSimulating = isPreviewSimulating,
                onRunPreview = onRunInstallPreview,
                onSelectStage = onSelectStage,
                onSimulateInstallOutcome = onSimulateInstallOutcome
            )
        }
    }
}

@Composable
private fun UnknownAppsPermissionBanner(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.tertiary,
                shape = MaterialTheme.shapes.medium
            )
            .testTag("unknown_apps_permission_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permission required",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "\"Install unknown apps\" Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "PackFlow Studio declares REQUEST_INSTALL_PACKAGES, but Android requires you to allow 'Install unknown apps' for this app before launching the system package installer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_unknown_apps_settings_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow \"Install unknown apps\" in Settings")
            }
        }
    }
}

@Composable
private fun ApkDocumentPickerAndTestCard(
    canInstallUnknownApps: Boolean,
    onPickApkDocument: () -> Unit,
    onOpenUnknownAppsSettings: () -> Unit,
    onLoadTestScenario: (String) -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Install & Preview APK",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Target SDK 36 (Android 16) • Host API ${Build.VERSION.SDK_INT} • ACTION_OPEN_DOCUMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickApkDocument,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("pick_apk_open_document_button")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick .APK from Storage")
                }

                OutlinedButton(
                    onClick = onOpenUnknownAppsSettings,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("unknown_apps_settings_shortcut_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Install unknown apps settings", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (canInstallUnknownApps) "Unknown Apps: ON" else "Allow Install")
                }
            }

            HorizontalDivider()

            Text(
                text = "Built-in Test APKs (Verify Preview, Warnings & Install Flow)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { onLoadTestScenario("external_valid") },
                    label = { Text("Test 1: Sample APK (Valid)") },
                    modifier = Modifier.testTag("test_apk_valid_chip")
                )
                FilterChip(
                    selected = false,
                    onClick = { onLoadTestScenario("self_match") },
                    label = { Text("Test 2: Same Package as PackFlow") },
                    modifier = Modifier.testTag("test_apk_self_match_chip")
                )
                FilterChip(
                    selected = false,
                    onClick = { onLoadTestScenario("high_min_sdk") },
                    label = { Text("Test 3: MinSDK > Phone (Blocked)") },
                    modifier = Modifier.testTag("test_apk_high_minsdk_chip")
                )
                FilterChip(
                    selected = false,
                    onClick = { onLoadTestScenario("corrupt_zip") },
                    label = { Text("Test 4: Invalid ZIP (No Manifest)") },
                    modifier = Modifier.testTag("test_apk_corrupt_chip")
                )
            }
        }
    }
}

@Composable
private fun InstallResultStatusCard(result: InstallResultStatus) {
    val borderColor = when (result.outcome) {
        InstallOutcomeType.SUCCESS -> MaterialTheme.colorScheme.primary
        InstallOutcomeType.CANCELLED -> MaterialTheme.colorScheme.tertiary
        InstallOutcomeType.FAILED -> MaterialTheme.colorScheme.error
    }
    val icon = when (result.outcome) {
        InstallOutcomeType.SUCCESS -> Icons.Default.CheckCircle
        InstallOutcomeType.CANCELLED -> Icons.Default.Cancel
        InstallOutcomeType.FAILED -> Icons.Default.ErrorOutline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = MaterialTheme.shapes.medium)
            .testTag("install_result_status_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = borderColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = borderColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = result.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Status Code: ${result.statusCode} • Temp APK Cache Cleaned: ${if (result.tempFileDeleted) "Yes (Deleted)" else "Pending"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ApkVerificationFailedCard(
    errorMessage: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.medium)
            .testTag("apk_verification_failed_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APK Verification Failed (Install Disabled)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel & Clear")
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("disabled_install_button_on_error")
                ) {
                    Text("Install (Disabled)")
                }
            }
        }
    }
}

@Composable
private fun PreviewApkDetailCard(
    apkInfo: PickedApkInfo,
    canInstallUnknownApps: Boolean,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onOpenUnknownAppsSettings: () -> Unit
) {
    val iconBitmap = remember(apkInfo.appIcon) {
        apkInfo.appIcon?.let { drawableToImageBitmap(it) }
    }
    val dangerousCount = apkInfo.requestedPermissions.count { it.isDangerous }
    val isInstallEnabled = apkInfo.previewLoadedWithoutErrors &&
        apkInfo.isValidApkArchive &&
        apkInfo.isMinSdkCompatible

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("preview_apk_detail_card"),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preview APK",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = "ZIP + AndroidManifest.xml OK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Warning if package name matches PackFlow itself
            if (apkInfo.matchesSelfPackage) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
                        .testTag("self_package_match_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Self package warning",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Warning: Package Matches PackFlow Studio Itself",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "This APK's package name (${apkInfo.packageName}) is identical to PackFlow Studio. Proceeding will install an update over the currently running app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Blocking Error if Minimum Android Version > Phone's Android Version
            if (!apkInfo.isMinSdkCompatible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                        .testTag("min_sdk_incompatible_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Incompatible Android version",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Incompatible Minimum Android Version (Install Blocked)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "This APK requires ${PackagerEngine.androidVersionLabel(apkInfo.minSdk)}, which is newer than this phone's OS (${PackagerEngine.androidVersionLabel(apkInfo.hostDeviceSdk)}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // App Icon + App Name + Package Name Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = "${apkInfo.appName} icon",
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.appName,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag("apk_meta_appname")
                        )
                        Text(
                            text = apkInfo.packageName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("apk_meta_packagename")
                        )
                        Text(
                            text = "File: ${apkInfo.fileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("apk_meta_filename")
                        )
                    }
                }
            }

            HorizontalDivider()

            // Metadata Rows
            ApkMetaRow(
                label = "Version Name & Code",
                value = "${apkInfo.versionName} (VersionCode ${apkInfo.versionCode})",
                testTag = "apk_meta_version"
            )
            ApkMetaRow(
                label = "Minimum Android Version",
                value = PackagerEngine.androidVersionLabel(apkInfo.minSdk),
                testTag = "apk_meta_min_sdk"
            )
            ApkMetaRow(
                label = "Target Android Version",
                value = PackagerEngine.androidVersionLabel(apkInfo.targetSdk),
                testTag = "apk_meta_target_sdk"
            )
            ApkMetaRow(
                label = "File Size",
                value = "${PackagerEngine.formatBytes(apkInfo.fileSizeBytes)} (${apkInfo.fileSizeBytes} bytes)",
                testTag = "apk_meta_filesize"
            )
            ApkMetaRow(
                label = "SHA-256 Hash",
                value = apkInfo.sha256Hex,
                testTag = "apk_meta_sha256"
            )
            ApkMetaRow(
                label = "Signing Cert (SHA-256)",
                value = apkInfo.signingCertSha256,
                testTag = "apk_meta_signing_cert"
            )

            HorizontalDivider()

            // Requested Permissions (Dangerous Highlighted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (dangerousCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Requested Permissions (${apkInfo.requestedPermissions.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                if (dangerousCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            text = "$dangerousCount DANGEROUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (apkInfo.requestedPermissions.isEmpty()) {
                Text(
                    text = "No permissions requested in AndroidManifest.xml.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    apkInfo.requestedPermissions.forEach { perm ->
                        PermissionItemRow(perm = perm)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Install & Cancel Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_apk_preview_button")
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (!canInstallUnknownApps) {
                            onOpenUnknownAppsSettings()
                        } else {
                            onInstallClick()
                        }
                    },
                    enabled = isInstallEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("install_apk_system_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install")
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(perm: ApkPermissionItem) {
    val bgColor = if (perm.isDangerous) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = if (perm.isDangerous) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = perm.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (perm.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (perm.isDangerous) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = perm.protectionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (perm.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (perm.isDangerous) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Dangerous permission",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ApkMetaRow(
    label: String,
    value: String,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun InteractiveInstallFlowPreviewCard(
    pickedApkInfo: PickedApkInfo?,
    currentStage: InstallPreviewStage,
    isSimulating: Boolean,
    onRunPreview: () -> Unit,
    onSelectStage: (InstallPreviewStage) -> Unit,
    onSimulateInstallOutcome: (InstallOutcomeType, Int) -> Unit
) {
    val activePackage = pickedApkInfo?.packageName ?: "com.claude.pulsesample"
    val activeFileName = pickedApkInfo?.fileName ?: "claude_pulse_sample.apk"
    val activeSize = pickedApkInfo?.let { PackagerEngine.formatBytes(it.fileSizeBytes) } ?: "4.18 MB"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("install_flow_preview_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
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
                        text = "AI Studio Install Flow Preview & Status Simulator",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Note: In the AI Studio cloud preview, system package installation is restricted by container policies. Use this preview to test Success (Code 0), Cancelled (Code 3), or Failed (Code 4) flows and verify temp APK deletion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InstallPreviewStage.entries.filter { it != InstallPreviewStage.IDLE }.forEach { stage ->
                    val isCurrentOrDone = currentStage.stepIndex >= stage.stepIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (isCurrentOrDone) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$activeFileName ($activePackage • $activeSize)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = currentStage.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currentStage.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = isSimulating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onSimulateInstallOutcome(InstallOutcomeType.SUCCESS, 0) },
                            modifier = Modifier.testTag("sim_status_success_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Success (Code 0)")
                        }
                        OutlinedButton(
                            onClick = { onSimulateInstallOutcome(InstallOutcomeType.CANCELLED, 3) },
                            modifier = Modifier.testTag("sim_status_cancelled_btn")
                        ) {
                            Text("Cancelled (Code 3)")
                        }
                        OutlinedButton(
                            onClick = { onSimulateInstallOutcome(InstallOutcomeType.FAILED, 4) },
                            modifier = Modifier.testTag("sim_status_failed_btn")
                        ) {
                            Text("Failed (Code 4)")
                        }
                    }
                }
            }
        }
    }
}

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? {
    return runCatching {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(96)
        val height = drawable.intrinsicHeight.coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
}
