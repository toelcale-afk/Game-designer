package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PackagerEngine
import com.example.model.BuiltArchive
import com.example.model.CompressionProfile
import com.example.model.StagedEntry
import com.example.model.StagedEntryCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BundlerScreen(
    uiState: PackFlowUiState,
    onBundleNameChange: (String) -> Unit,
    onSelectCompression: (CompressionProfile) -> Unit,
    onSelectCategory: (StagedEntryCategory?) -> Unit,
    onOpenNewFileDialog: () -> Unit,
    onPickDeviceFile: () -> Unit,
    onPickPhotoMedia: () -> Unit,
    onEditEntry: (StagedEntry) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onBuildZipPackage: () -> Unit,
    onShareArchive: (BuiltArchive) -> Unit,
    onExportArchive: (BuiltArchive) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("bundler_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroStudioBanner(
                stagedFileCount = uiState.stagedEntries.size,
                totalStagedBytes = uiState.totalStagedBytes,
                builtArchiveCount = uiState.builtArchives.size
            )
        }

        item {
            ArchiveConfigurationCard(
                bundleName = uiState.bundleName,
                compressionProfile = uiState.compressionProfile,
                isPackaging = uiState.isPackaging,
                hasStagedFiles = uiState.stagedEntries.isNotEmpty(),
                onBundleNameChange = onBundleNameChange,
                onSelectCompression = onSelectCompression,
                onBuildZipPackage = onBuildZipPackage
            )
        }

        if (uiState.latestBuiltArchive != null) {
            item {
                BuiltArchiveResultCard(
                    archive = uiState.latestBuiltArchive,
                    onShare = { onShareArchive(uiState.latestBuiltArchive) },
                    onExport = { onExportArchive(uiState.latestBuiltArchive) }
                )
            }
        }

        item {
            StagingToolbarCard(
                selectedCategory = uiState.selectedCategoryFilter,
                onSelectCategory = onSelectCategory,
                onOpenNewFileDialog = onOpenNewFileDialog,
                onPickDeviceFile = onPickDeviceFile,
                onPickPhotoMedia = onPickPhotoMedia
            )
        }

        if (uiState.filteredEntries.isEmpty()) {
            item {
                EmptyStagingCard(onOpenNewFileDialog = onOpenNewFileDialog)
            }
        } else {
            items(
                items = uiState.filteredEntries,
                key = { it.id }
            ) { entry ->
                StagedEntryRowCard(
                    entry = entry,
                    onEdit = { onEditEntry(entry) },
                    onDelete = { onRemoveEntry(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun HeroStudioBanner(
    stagedFileCount: Int,
    totalStagedBytes: Long,
    builtArchiveCount: Int
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_banner_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        )
                    )
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.88f, size.height * 0.22f)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.15f, size.height * 0.85f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "ANDROID ARCHIVE & APK STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.hero_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadgePill(
                            label = "Staged",
                            value = "$stagedFileCount files"
                        )
                        StatBadgePill(
                            label = "Payload",
                            value = PackagerEngine.formatBytes(totalStagedBytes)
                        )
                        StatBadgePill(
                            label = "Built",
                            value = "$builtArchiveCount .zip"
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Image(
                    painter = painterResource(id = R.drawable.ic_hero_bundle_art),
                    contentDescription = "Isometric package cube illustration",
                    modifier = Modifier.size(92.dp)
                )
            }
        }
    }
}

@Composable
private fun StatBadgePill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ArchiveConfigurationCard(
    bundleName: String,
    compressionProfile: CompressionProfile,
    isPackaging: Boolean,
    hasStagedFiles: Boolean,
    onBundleNameChange: (String) -> Unit,
    onSelectCompression: (CompressionProfile) -> Unit,
    onBuildZipPackage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = bundleName,
                onValueChange = onBundleNameChange,
                label = { Text(stringResource(R.string.bundle_name_label)) },
                suffix = { Text(".zip", style = MaterialTheme.typography.labelMedium) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bundle_name_input")
            )

            Text(
                text = "Compression Engine Profile",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompressionProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = compressionProfile == profile,
                        onClick = { onSelectCompression(profile) },
                        label = { Text(profile.label) },
                        modifier = Modifier.testTag("compression_chip_${profile.name.lowercase()}")
                    )
                }
            }

            Button(
                onClick = onBuildZipPackage,
                enabled = hasStagedFiles && !isPackaging,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("build_zip_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = "Package ZIP archive"
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isPackaging) "Packaging Archive..." else stringResource(R.string.build_zip_package),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun BuiltArchiveResultCard(
    archive: BuiltArchive,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("built_archive_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Archive ready",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = archive.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${archive.entryCount} entries • ${PackagerEngine.formatBytes(archive.compressedBytes)} (${archive.compressionRatioPercent}% saved)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "SHA-256: ${archive.sha256Hex.take(32)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_archive_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share archive",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.share_package))
                }

                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_archive_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export archive to device",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.save_to_device))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagingToolbarCard(
    selectedCategory: StagedEntryCategory?,
    onSelectCategory: (StagedEntryCategory?) -> Unit,
    onOpenNewFileDialog: () -> Unit,
    onPickDeviceFile: () -> Unit,
    onPickPhotoMedia: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenNewFileDialog,
                modifier = Modifier
                    .weight(1f)
                    .testTag("new_file_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new text file", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.add_text_entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            FilledTonalButton(
                onClick = onPickDeviceFile,
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_file_button")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import file", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.pick_device_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            FilledTonalButton(
                onClick = onPickPhotoMedia,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_media_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = "Pick media", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.pick_photo_media), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All Files") },
                modifier = Modifier.testTag("filter_chip_all")
            )
            StagedEntryCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.label) },
                    modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun StagedEntryRowCard(
    entry: StagedEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (entry.category) {
        StagedEntryCategory.SOURCE_CODE -> Icons.Default.Code
        StagedEntryCategory.CONFIG_MANIFEST -> Icons.Default.SettingsApplications
        StagedEntryCategory.DOCUMENTATION -> Icons.Default.Description
        StagedEntryCategory.MEDIA_ASSET -> Icons.Default.InsertDriveFile
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.textPreview != null) { onEdit() }
            .testTag("staged_entry_${entry.relativePath}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = entry.category.label,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.relativePath,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.category.label} • ${PackagerEngine.formatBytes(entry.sizeBytes.toLong())} • CRC32 ${entry.crc32Hex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry.textPreview != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_entry_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${entry.relativePath}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_entry_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove ${entry.relativePath}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyStagingCard(onOpenNewFileDialog: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Archive,
                contentDescription = "Empty staging area",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.empty_staging_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.empty_staging_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenNewFileDialog,
                modifier = Modifier.testTag("empty_state_add_file_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create First File")
            }
        }
    }
}

@Composable
fun FileEditorDialog(
    initialPath: String,
    initialContent: String,
    isEditingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirmSave: (path: String, content: String) -> Unit
) {
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditingExisting) "Edit Staged File" else "Add New File to Package",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Relative Archive Path (e.g. src/Notes.md)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_file_path_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("File Content (UTF-8)") },
                    minLines = 6,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_file_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSave(path, content) },
                enabled = path.isNotBlank(),
                modifier = Modifier.testTag("dialog_save_file_button")
            ) {
                Text("Save to Staging")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
