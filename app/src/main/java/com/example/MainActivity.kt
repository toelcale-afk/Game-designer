package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.ui.ClaudeStudioScreen
import com.example.ui.ErrorLogScreen
import com.example.ui.FileEditorDialog
import com.example.ui.InspectorScreen
import com.example.ui.PackFlowViewModel
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

private data class NavTabInfo(
    val tab: PackFlowTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackFlowApp(
    viewModel: PackFlowViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingExportArchive by remember { mutableStateOf<BuiltArchive?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshRuntimeTelemetry(context)
        viewModel.refreshUnknownAppsPermissionState(context)
        // Load the built-in valid sample APK on startup so the Preview APK screen is immediately ready to test
        viewModel.loadBuiltInTestApk(context, "external_valid")
    }

    // Re-check canRequestPackageInstalls() whenever returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUnknownAppsPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(
        enabled = uiState.showNewFileDialog ||
            uiState.editingEntry != null ||
            uiState.isLivePreviewFullScreen ||
            uiState.activeTab != PackFlowTab.INSTALL_APK
    ) {
        when {
            uiState.showNewFileDialog -> viewModel.dismissNewFileDialog()
            uiState.editingEntry != null -> viewModel.dismissEditEntry()
            uiState.isLivePreviewFullScreen -> viewModel.toggleLivePreviewFullScreen(false)
            uiState.activeTab != PackFlowTab.INSTALL_APK -> viewModel.selectTab(PackFlowTab.INSTALL_APK)
        }
    }

    // 1. System File Picker for .apk using ACTION_OPEN_DOCUMENT (ActivityResultContracts.OpenDocument)
    val apkOpenDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.pickApkFromUri(context, uri)
        }
    }

    // 2. Unknown Apps Settings Launcher (ACTION_MANAGE_UNKNOWN_APP_SOURCES)
    val unknownAppsSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshUnknownAppsPermissionState(context)
    }

    // 3. System Package Installer Launcher (ACTION_INSTALL_PACKAGE with EXTRA_RETURN_RESULT)
    val systemPackageInstallerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val extraStatus = activityResult.data?.getIntExtra("android.intent.extra.INSTALL_RESULT", activityResult.resultCode)
        viewModel.onSystemInstallerActivityResult(
            resultCode = activityResult.resultCode,
            dataIntentMessage = extraStatus?.let { "INSTALL_RESULT=$it" }
        )
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

    val navTabs = listOf(
        NavTabInfo(
            tab = PackFlowTab.INSTALL_APK,
            labelRes = R.string.nav_install_apk,
            selectedIcon = Icons.Filled.InstallMobile,
            unselectedIcon = Icons.Outlined.InstallMobile,
            testTag = "nav_tab_install_apk"
        ),
        NavTabInfo(
            tab = PackFlowTab.CLAUDE_STUDIO,
            labelRes = R.string.nav_claude_studio,
            selectedIcon = Icons.Filled.Code,
            unselectedIcon = Icons.Outlined.Code,
            testTag = "nav_tab_claude_studio"
        ),
        NavTabInfo(
            tab = PackFlowTab.BUNDLER,
            labelRes = R.string.nav_bundler,
            selectedIcon = Icons.Filled.FolderZip,
            unselectedIcon = Icons.Outlined.FolderZip,
            testTag = "nav_tab_bundler"
        ),
        NavTabInfo(
            tab = PackFlowTab.INSPECTOR,
            labelRes = R.string.nav_inspector,
            selectedIcon = Icons.Filled.Memory,
            unselectedIcon = Icons.Outlined.Memory,
            testTag = "nav_tab_inspector"
        ),
        NavTabInfo(
            tab = PackFlowTab.ERROR_LOG,
            labelRes = R.string.nav_error_log,
            selectedIcon = Icons.Filled.BugReport,
            unselectedIcon = Icons.Outlined.BugReport,
            testTag = "nav_tab_error_log"
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!uiState.isLivePreviewFullScreen && uiState.activeTab != PackFlowTab.INSTALL_APK) {
                    TopAppBar(
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
                }
            },
            bottomBar = {
                if (!isExpandedScreen && !uiState.isLivePreviewFullScreen) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        navTabs.forEach { item ->
                            val selected = uiState.activeTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (item.tab == PackFlowTab.INSTALL_APK && uiState.activeTab == PackFlowTab.INSTALL_APK) {
                                        InstallerSession.screen = InstallerScreen.Pick
                                    }
                                    viewModel.selectTab(item.tab)
                                },
                                icon = {
                                    if (item.tab == PackFlowTab.ERROR_LOG && uiState.totalErrorCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(uiState.totalErrorCount.toString()) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen && !uiState.isLivePreviewFullScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        navTabs.forEach { item ->
                            val selected = uiState.activeTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (item.tab == PackFlowTab.INSTALL_APK && uiState.activeTab == PackFlowTab.INSTALL_APK) {
                                        InstallerSession.screen = InstallerScreen.Pick
                                    }
                                    viewModel.selectTab(item.tab)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(visible = uiState.statusBannerMessage != null && !uiState.isLivePreviewFullScreen && uiState.activeTab != PackFlowTab.INSTALL_APK) {
                        uiState.statusBannerMessage?.let { msg ->
                            StatusFeedbackBanner(
                                message = msg,
                                onDismiss = { viewModel.dismissBanner() }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.activeTab) {
                            PackFlowTab.INSTALL_APK -> PackFlowInstaller()

                            PackFlowTab.CLAUDE_STUDIO -> ClaudeStudioScreen(
                                rawInput = uiState.claudeRawInput,
                                config = uiState.claudeConfig,
                                extractedFiles = uiState.claudeExtractedFiles,
                                diagnostics = uiState.claudeDiagnostics,
                                runtimeErrors = uiState.claudeRuntimeErrors,
                                consoleLogs = uiState.claudeConsoleLogs,
                                isFullScreenPreview = uiState.isLivePreviewFullScreen,
                                onRawInputChange = viewModel::updateClaudeRawInput,
                                onSelectPreset = viewModel::selectClaudePreset,
                                onUpdateConfig = viewModel::updateClaudeConfig,
                                onImportClaudeFile = { claudeFilePickerLauncher.launch("*/*") },
                                onPackageToApkZip = { viewModel.packageClaudeFilesIntoApkZip(context) },
                                onAppendConsoleLog = viewModel::appendWebViewConsoleMessage,
                                onClearConsole = viewModel::clearConsoleLogs,
                                onToggleFullScreenPreview = viewModel::toggleLivePreviewFullScreen,
                                onCopyPromptSuccess = { }
                            )

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
