package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.aistudio.packflow.xkrv.installer.InstallerScreen
import com.aistudio.packflow.xkrv.installer.InstallerSession
import com.aistudio.packflow.xkrv.installer.PackFlowInstaller
import com.example.data.PackagerEngine
import com.example.model.BuiltArchive
import com.example.model.PackFlowTab
import com.example.ui.BundlerScreen
import com.example.ui.CompileCheckScreen
import com.example.ui.ErrorLogScreen
import com.example.ui.FileEditorDialog
import com.example.ui.IconStepScreen
import com.example.ui.InspectorScreen
import com.example.ui.PackFlowViewModel
import com.example.ui.PreviewScreen
import com.example.ui.QuickBuildCard
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PackFlowApp()
            }
        }
    }
}

/**
 * PackFlow: the build pipeline. Main flow 1 Preview -> 2 Icon -> 3 Compile & Check -> 4 Install,
 * then the extras (Bundler, Inspector, Error Log).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackFlowApp(
    viewModel: PackFlowViewModel = viewModel(),
    onExit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gitHubState by viewModel.gitHub.state.collectAsStateWithLifecycle()
    var pendingExportArchive by remember { mutableStateOf<BuiltArchive?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshRuntimeTelemetry(context)
        viewModel.gitHub.load(context)
    }

    BackHandler(
        enabled = uiState.showNewFileDialog ||
            uiState.editingEntry != null ||
            uiState.isLivePreviewFullScreen ||
            uiState.activeTab != PackFlowTab.PREVIEW ||
            onExit != null
    ) {
        when {
            uiState.showNewFileDialog -> viewModel.dismissNewFileDialog()
            uiState.editingEntry != null -> viewModel.dismissEditEntry()
            uiState.isLivePreviewFullScreen -> viewModel.toggleLivePreviewFullScreen(false)
            uiState.activeTab != PackFlowTab.PREVIEW -> viewModel.selectTab(PackFlowTab.PREVIEW)
            else -> onExit?.invoke()
        }
    }

    // 4. Claude source file picker
    val claudeFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importClaudeFileFromUri(context, uri)
        }
    }

    // 5. General file picker for ZIP Bundler
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importUriToStaging(context, uri, defaultSubfolder = "imported")
        }
    }

    // 6. Zero-permission Android Photo Picker for ZIP Bundler
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importUriToStaging(context, uri, defaultSubfolder = "assets/media")
        }
    }

    // 7. SAF Save ZIP Archive launcher
    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destinationUri ->
        val archive = pendingExportArchive
        if (destinationUri != null && archive != null) {
            viewModel.exportArchiveToUri(context, archive, destinationUri)
        }
        pendingExportArchive = null
    }

    val goTo: (PackFlowTab) -> Unit = { tab ->
        if (tab == PackFlowTab.INSTALL && uiState.activeTab == PackFlowTab.INSTALL) {
            InstallerSession.screen = InstallerScreen.Pick
        }
        viewModel.selectTab(tab)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!uiState.isLivePreviewFullScreen) {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            if (onExit != null) {
                                IconButton(onClick = onExit) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home")
                                }
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.InstallMobile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    PipelineTabRow(
                        active = uiState.activeTab,
                        errorCount = uiState.totalErrorCount,
                        onSelect = goTo
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = uiState.statusBannerMessage != null && !uiState.isLivePreviewFullScreen && uiState.activeTab != PackFlowTab.INSTALL) {
                uiState.statusBannerMessage?.let { msg ->
                    StatusFeedbackBanner(
                        message = msg,
                        onDismiss = { viewModel.dismissBanner() }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    PackFlowTab.PREVIEW -> PreviewScreen(
                        rawInput = uiState.claudeRawInput,
                        config = uiState.claudeConfig,
                        extractedFiles = uiState.claudeExtractedFiles,
                        diagnostics = uiState.claudeDiagnostics,
                        consoleLogs = uiState.claudeConsoleLogs,
                        isFullScreenPreview = uiState.isLivePreviewFullScreen,
                        onRawInputChange = viewModel::updateClaudeRawInput,
                        onSelectPreset = viewModel::selectClaudePreset,
                        onUpdateConfig = viewModel::updateClaudeConfig,
                        onImportClaudeFile = { claudeFilePickerLauncher.launch("*/*") },
                        onNext = { goTo(PackFlowTab.ICON) },
                        onAppendConsoleLog = viewModel::appendWebViewConsoleMessage,
                        onClearConsole = viewModel::clearConsoleLogs,
                        onToggleFullScreenPreview = viewModel::toggleLivePreviewFullScreen
                    )

                    PackFlowTab.ICON -> IconStepScreen(
                        viewModel = viewModel,
                        onNext = { goTo(PackFlowTab.COMPILE) }
                    )

                    PackFlowTab.COMPILE -> CompileCheckScreen(
                        config = uiState.claudeConfig,
                        files = uiState.claudeExtractedFiles,
                        diagnostics = uiState.claudeDiagnostics,
                        runtimeErrors = uiState.claudeRuntimeErrors,
                        gitHub = gitHubState,
                        quickBuild = { QuickBuildCard(viewModel = viewModel, onSendToInstall = { goTo(PackFlowTab.INSTALL) }) },
                        onPackageZip = { viewModel.packageClaudeFilesIntoApkZip(context) },
                        onSaveGitHub = { target, token -> viewModel.gitHub.save(context, target, token) },
                        onClearToken = { viewModel.gitHub.clearToken(context) },
                        onPushAndBuild = { viewModel.pushAndBuildOnGitHub(context) },
                        onCancelBuild = { viewModel.gitHub.cancel() },
                        onSendToInstall = {
                            gitHubState.apkFile?.let { apk ->
                                InstallerSession.openFile(context, apk)
                                viewModel.selectTab(PackFlowTab.INSTALL)
                            }
                        }
                    )

                    PackFlowTab.INSTALL -> PackFlowInstaller()

                    PackFlowTab.BUNDLER -> BundlerScreen(
                        uiState = uiState,
                        onBundleNameChange = viewModel::updateBundleName,
                        onSelectCompression = viewModel::selectCompressionProfile,
                        onSelectCategory = viewModel::selectCategoryFilter,
                        onOpenNewFileDialog = viewModel::openNewFileDialog,
                        onPickDeviceFile = { filePickerLauncher.launch("*/*") },
                        onPickPhotoMedia = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onEditEntry = viewModel::openEditEntry,
                        onRemoveEntry = viewModel::removeStagedEntry,
                        onBuildZipPackage = { viewModel.packageCurrentStagingToZip(context) },
                        onShareArchive = { archive ->
                            shareBuiltArchive(context, archive, viewModel::recordException)
                        },
                        onExportArchive = { archive ->
                            pendingExportArchive = archive
                            saveArchiveLauncher.launch(archive.fileName)
                        }
                    )

                    PackFlowTab.INSPECTOR -> InspectorScreen(
                        telemetry = uiState.runtimeTelemetry,
                        onRefresh = { viewModel.refreshRuntimeTelemetry(context) },
                        onStageReport = viewModel::stageTelemetryReport
                    )

                    PackFlowTab.ERROR_LOG -> ErrorLogScreen(
                        telemetry = uiState.runtimeTelemetry,
                        caughtExceptions = uiState.caughtExceptions,
                        claudeDiagnostics = uiState.claudeDiagnostics,
                        onCopyLog = viewModel::buildFormattedErrorLog,
                        onTriggerTestException = viewModel::triggerSampleExceptionForTesting,
                        onClearLogs = viewModel::clearCaughtExceptions,
                        onCopySuccessBanner = { }
                    )
                }
            }
        }
    }

    if (uiState.showNewFileDialog) {
        FileEditorDialog(
            initialPath = "notes/release_notes.md",
            initialContent = "# Release Package Notes\n\n- Bundled with PackFlow Studio\n- Ready for distribution\n",
            isEditingExisting = false,
            onDismiss = viewModel::dismissNewFileDialog,
            onConfirmSave = { path, content ->
                viewModel.addOrUpdateTextFile(path, content, existingId = null)
            }
        )
    }

    uiState.editingEntry?.let { editing ->
        FileEditorDialog(
            initialPath = editing.relativePath,
            initialContent = editing.textPreview ?: "",
            isEditingExisting = true,
            onDismiss = viewModel::dismissEditEntry,
            onConfirmSave = { path, content ->
                viewModel.addOrUpdateTextFile(path, content, existingId = editing.id)
            }
        )
    }
}

@Composable
private fun PipelineTabRow(
    active: PackFlowTab,
    errorCount: Int,
    onSelect: (PackFlowTab) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = active.ordinal,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("pipeline_tab_row")
    ) {
        PackFlowTab.entries.forEach { tab ->
            Tab(
                selected = tab == active,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("pipeline_tab_${tab.name.lowercase()}"),
                text = {
                    if (tab == PackFlowTab.ERROR_LOG && errorCount > 0) {
                        BadgedBox(badge = { Badge { Text(errorCount.toString()) } }) {
                            Text(tab.title)
                        }
                    } else {
                        Text(
                            text = tab.title,
                            textAlign = TextAlign.Center,
                            color = if (tab.step == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusFeedbackBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("status_feedback_banner"),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_banner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notification"
                )
            }
        }
    }
}

private fun shareBuiltArchive(
    context: Context,
    archive: BuiltArchive,
    onException: (Throwable, String) -> Unit
) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, archive.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, archive.fileName)
            putExtra(
                Intent.EXTRA_TEXT,
                "Shared ${archive.fileName} (${PackagerEngine.formatBytes(archive.compressedBytes)}, SHA-256: ${archive.sha256Hex})"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${archive.fileName}"))
    } catch (e: Throwable) {
        onException(e, "Share Archive via FileProvider")
    }
}
