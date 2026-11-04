package com.example.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PackagerEngine
import com.example.model.AndroidBlueprintSpec

@Composable
fun BlueprintScreen(
    spec: AndroidBlueprintSpec,
    previewTabIndex: Int,
    onUpdateSpec: ((AndroidBlueprintSpec) -> AndroidBlueprintSpec) -> Unit,
    onSelectPreviewTab: (Int) -> Unit,
    onStageAndPackageBlueprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewCode = when (previewTabIndex) {
        0 -> PackagerEngine.generateManifestXml(spec)
        1 -> PackagerEngine.generateBuildGradleKts(spec)
        else -> PackagerEngine.generateMainActivityKt(spec)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("blueprint_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = "Android Blueprint",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.blueprint_header),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = stringResource(R.string.blueprint_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Application Identity",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = spec.appName,
                        onValueChange = { name -> onUpdateSpec { it.copy(appName = name.take(30)) } },
                        label = { Text("App Launcher Title (≤ 30 chars for Play Policy)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blueprint_app_name_input")
                    )

                    OutlinedTextField(
                        value = spec.applicationId,
                        onValueChange = { pkg -> onUpdateSpec { it.copy(applicationId = pkg.trim()) } },
                        label = { Text("Unique Application ID (Package Name)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blueprint_app_id_input")
                    )

                    OutlinedTextField(
                        value = spec.versionName,
                        onValueChange = { ver -> onUpdateSpec { it.copy(versionName = ver.trim()) } },
                        label = { Text("Version Name (SemVer)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blueprint_version_input")
                    )

                    BlueprintSwitchRow(
                        title = "Edge-to-Edge Window Insets",
                        subtitle = "Calls enableEdgeToEdge() with safe Scaffold padding",
                        checked = spec.includeEdgeToEdge,
                        onCheckedChange = { enabled -> onUpdateSpec { it.copy(includeEdgeToEdge = enabled) } },
                        testTag = "switch_edge_to_edge"
                    )

                    BlueprintSwitchRow(
                        title = "Zero-Permission Photo Picker",
                        subtitle = "Play Policy compliant media selection without broad storage permissions",
                        checked = spec.includePhotoPicker,
                        onCheckedChange = { enabled -> onUpdateSpec { it.copy(includePhotoPicker = enabled) } },
                        testTag = "switch_photo_picker"
                    )

                    BlueprintSwitchRow(
                        title = "MVVM StateFlow ViewModel",
                        subtitle = "Generates MainViewModel.kt with reactive StateFlow architecture",
                        checked = spec.includeViewModel,
                        onCheckedChange = { enabled -> onUpdateSpec { it.copy(includeViewModel = enabled) } },
                        testTag = "switch_viewmodel"
                    )

                    BlueprintSwitchRow(
                        title = "Material You Adaptive Icon XML",
                        subtitle = "Includes mipmap-anydpi-v26 adaptive launcher icon configuration",
                        checked = spec.includeAdaptiveIcon,
                        onCheckedChange = { enabled -> onUpdateSpec { it.copy(includeAdaptiveIcon = enabled) } },
                        testTag = "switch_adaptive_icon"
                    )

                    Button(
                        onClick = onStageAndPackageBlueprint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("stage_blueprint_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Stage Blueprint & Package .ZIP")
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Generated Source Preview",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Play Compliant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf("AndroidManifest.xml", "app/build.gradle.kts", "MainActivity.kt")
                        tabs.forEachIndexed { index, title ->
                            FilterChip(
                                selected = previewTabIndex == index,
                                onClick = { onSelectPreviewTab(index) },
                                label = { Text(title) },
                                modifier = Modifier.testTag("preview_tab_$index")
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = previewCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .testTag("blueprint_code_preview")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueprintSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
