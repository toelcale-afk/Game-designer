package com.example.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClaudeApkConverter
import com.example.data.ClaudePresetTemplate
import com.example.data.PackagerEngine
import com.example.model.AndroidBlueprintSpec
import com.example.model.BuiltArchive
import com.example.model.CaughtErrorLogEntry
import com.example.model.ClaudeApkConfig
import com.example.model.ClaudeDiagnosticIssue
import com.example.model.ClaudeSourceFile
import com.example.model.CompressionProfile
import com.example.model.ConsoleLogMessage
import com.example.model.InstallOutcomeType
import com.example.model.InstallPreviewStage
import com.example.model.InstallResultStatus
import com.example.model.PackFlowTab
import com.example.model.PickedApkInfo
import com.example.model.RuntimeApkTelemetry
import com.example.model.StagedEntry
import com.example.model.StagedEntryCategory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PackFlowUiState(
    val activeTab: PackFlowTab = PackFlowTab.INSTALL_APK,
    // Install APK & Preview APK state
    val canInstallUnknownApps: Boolean = true,
    val pickedApkInfo: PickedApkInfo? = null,
    val isLoadingApkPreview: Boolean = false,
    val apkPreviewError: String? = null,
    val lastInstallResult: InstallResultStatus? = null,
    val installPreviewStage: InstallPreviewStage = InstallPreviewStage.IDLE,
    val isPreviewSimulating: Boolean = false,
    // Claude-to-APK & Live Device Preview state
    val claudeRawInput: String = "",
    val claudeConfig: ClaudeApkConfig = ClaudeApkConfig(),
    val claudeExtractedFiles: List<ClaudeSourceFile> = emptyList(),
    val claudeDiagnostics: List<ClaudeDiagnosticIssue> = emptyList(),
    val claudeRuntimeErrors: List<String> = emptyList(),
    val claudeConsoleLogs: List<ConsoleLogMessage> = emptyList(),
    val isLivePreviewFullScreen: Boolean = false,
    // Error Log state
    val caughtExceptions: List<CaughtErrorLogEntry> = emptyList(),
    // Bundler & Inspector state
    val bundleName: String = "packflow_android_bundle",
    val compressionProfile: CompressionProfile = CompressionProfile.BALANCED,
    val selectedCategoryFilter: StagedEntryCategory? = null,
    val stagedEntries: List<StagedEntry> = emptyList(),
    val builtArchives: List<BuiltArchive> = emptyList(),
    val latestBuiltArchive: BuiltArchive? = null,
    val blueprintSpec: AndroidBlueprintSpec = AndroidBlueprintSpec(),
    val blueprintPreviewTab: Int = 0,
    val runtimeTelemetry: RuntimeApkTelemetry? = null,
    val isPackaging: Boolean = false,
    val statusBannerMessage: String? = null,
    val editingEntry: StagedEntry? = null,
    val showNewFileDialog: Boolean = false
) {
    val totalStagedBytes: Long
        get() = stagedEntries.sumOf { it.sizeBytes.toLong() }

    val filteredEntries: List<StagedEntry>
        get() = if (selectedCategoryFilter == null) {
            stagedEntries
        } else {
            stagedEntries.filter { it.category == selectedCategoryFilter }
        }

    val totalErrorCount: Int
        get() = caughtExceptions.size + claudeDiagnostics.size + claudeRuntimeErrors.size
}

class PackFlowViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PackFlowUiState())
    val uiState: StateFlow<PackFlowUiState> = _uiState.asStateFlow()

    init {
        val defaultPreset = ClaudeApkConverter.presets.first()
        val (detectedType, parsedFiles) = ClaudeApkConverter.parseClaudeInput(defaultPreset.rawInput)
        val updatedConfig = defaultPreset.config.copy(artifactType = detectedType)
        val diagnostics = ClaudeApkConverter.analyzeDiagnostics(updatedConfig, parsedFiles)
        val initialEntries = ClaudeApkConverter.generateApkProjectEntries(updatedConfig, parsedFiles)

        _uiState.update {
            it.copy(
                claudeRawInput = defaultPreset.rawInput,
                claudeConfig = updatedConfig,
                claudeExtractedFiles = parsedFiles,
                claudeDiagnostics = diagnostics,
                stagedEntries = initialEntries,
                statusBannerMessage = "Ready: Pick an .apk file (ACTION_OPEN_DOCUMENT) or load a built-in Test APK."
            )
        }
    }

    fun selectTab(tab: PackFlowTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun refreshUnknownAppsPermissionState(context: Context) {
        try {
            val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
            _uiState.update { it.copy(canInstallUnknownApps = allowed) }
        } catch (e: Throwable) {
            recordException(e, "Check canRequestPackageInstalls")
        }
    }

    fun recordException(throwable: Throwable, contextHint: String) {
        val entry = PackagerEngine.toCaughtErrorLogEntry(throwable, contextHint)
        _uiState.update { state ->
            state.copy(
                caughtExceptions = listOf(entry) + state.caughtExceptions,
                statusBannerMessage = "Caught ${entry.errorType} at ${entry.fileName}:${entry.lineNumber} — logged to Error Log"
            )
        }
    }

    // ========================================================================
    // 1. INSTALL APK & PREVIEW APK SCREEN
    // ========================================================================

    fun pickApkFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingApkPreview = true,
                    apkPreviewError = null,
                    lastInstallResult = null,
                    pickedApkInfo = null
                )
            }
            try {
                val info = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var displayName = "selected_package.apk"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                val name = cursor.getString(idx)
                                if (!name.isNullOrBlank()) displayName = name
                            }
                        }
                    }

                    val bundlesDir = File(context.cacheDir, "bundles")
                    if (!bundlesDir.exists()) bundlesDir.mkdirs()
                    val tempFile = File(bundlesDir, "picked_${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")

                    resolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalArgumentException("Could not open input stream for selected URI: $uri")

                    try {
                        PackagerEngine.inspectApkFile(context, tempFile, displayName)
                    } catch (inspectErr: Throwable) {
                        // Clean up invalid copied file immediately
                        runCatching { tempFile.delete() }
                        throw inspectErr
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoadingApkPreview = false,
                        pickedApkInfo = info,
                        apkPreviewError = null,
                        installPreviewStage = InstallPreviewStage.VERIFYING_PACKAGE,
                        statusBannerMessage = "Verified real APK: ${info.appName} (${info.packageName})"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingApkPreview = false,
                        pickedApkInfo = null,
                        apkPreviewError = "${e::class.java.simpleName}: ${e.localizedMessage ?: "Invalid APK file"}"
                    )
                }
                recordException(e, "Verify & Preview Picked APK")
            }
        }
    }

    /**
     * Stages one of the built-in Test APKs so the user can test:
     * 1) Standard external package APK (`com.claude.pulsesample`)
     * 2) Self-matching package warning (`com.aistudio.packflow.xkrv`)
     * 3) Incompatible higher minSdk (`API 37 > Build.VERSION.SDK_INT`)
     * 4) Invalid non-APK ZIP missing `AndroidManifest.xml`
     */
    fun loadBuiltInTestApk(
        context: Context,
        scenario: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingApkPreview = true,
                    apkPreviewError = null,
                    lastInstallResult = null,
                    pickedApkInfo = null
                )
            }
            try {
                val info = withContext(Dispatchers.IO) {
                    when (scenario) {
                        "external_valid" -> {
                            val fileName = "claude_pulse_sample.apk"
                            val staged = PackagerEngine.stageSampleInstalledApk(context, fileName)
                            PackagerEngine.inspectApkFile(
                                context = context,
                                apkFile = staged,
                                displayFileName = fileName,
                                overridePackageNameForTest = "com.claude.pulsesample"
                            )
                        }
                        "self_match" -> {
                            val fileName = "packflow_self_update_test.apk"
                            val staged = PackagerEngine.stageSampleInstalledApk(context, fileName)
                            PackagerEngine.inspectApkFile(
                                context = context,
                                apkFile = staged,
                                displayFileName = fileName
                            )
                        }
                        "high_min_sdk" -> {
                            val fileName = "future_xr_api37_test.apk"
                            val staged = PackagerEngine.stageSampleInstalledApk(context, fileName)
                            PackagerEngine.inspectApkFile(
                                context = context,
                                apkFile = staged,
                                displayFileName = fileName,
                                overrideMinSdkForTest = Build.VERSION.SDK_INT + 2,
                                overridePackageNameForTest = "com.claude.futurexr"
                            )
                        }
                        else -> {
                            val badZip = PackagerEngine.stageInvalidNonApkZipForTest(context)
                            try {
                                PackagerEngine.inspectApkFile(context, badZip, badZip.name)
                            } finally {
                                runCatching { badZip.delete() }
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoadingApkPreview = false,
                        pickedApkInfo = info,
                        apkPreviewError = null,
                        installPreviewStage = InstallPreviewStage.VERIFYING_PACKAGE,
                        statusBannerMessage = "Loaded test APK: ${info.fileName} (${info.packageName})"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingApkPreview = false,
                        pickedApkInfo = null,
                        apkPreviewError = "${e::class.java.simpleName}: ${e.localizedMessage}"
                    )
                }
                recordException(e, "Test APK Verification ($scenario)")
            }
        }
    }

    /**
     * Deletes the copied temporary APK file when the user taps Cancel or after installation completes.
     */
    fun cancelAndDeleteCopiedApk() {
        val currentApk = _uiState.value.pickedApkInfo
        val cachedFile = currentApk?.cachedFile
        val deleted = if (cachedFile != null && cachedFile.exists()) {
            runCatching { cachedFile.delete() }.getOrDefault(false)
        } else {
            true
        }

        _uiState.update {
            it.copy(
                pickedApkInfo = null,
                apkPreviewError = null,
                installPreviewStage = InstallPreviewStage.IDLE,
                lastInstallResult = InstallResultStatus(
                    outcome = InstallOutcomeType.CANCELLED,
                    statusCode = Activity.RESULT_CANCELED,
                    title = "Installation Cancelled (Status Code: 0 / RESULT_CANCELED)",
                    details = "Cancelled APK preview for '${currentApk?.fileName ?: "package.apk"}'. Temporary copied APK file deleted from cache ($deleted).",
                    tempFileDeleted = deleted
                ),
                statusBannerMessage = "Cancelled preview & deleted temporary copied APK file."
            )
        }
    }

    fun onSystemInstallerActivityResult(resultCode: Int, dataIntentMessage: String?) {
        val currentApk = _uiState.value.pickedApkInfo
        val cachedFile = currentApk?.cachedFile
        val deleted = if (cachedFile != null && cachedFile.exists()) {
            runCatching { cachedFile.delete() }.getOrDefault(false)
        } else {
            true
        }

        val resultStatus = when (resultCode) {
            Activity.RESULT_OK -> InstallResultStatus(
                outcome = InstallOutcomeType.SUCCESS,
                statusCode = 0, // PackageInstaller.STATUS_SUCCESS
                title = "Install Succeeded (Status Code: 0 / STATUS_SUCCESS)",
                details = "System installer completed installation for '${currentApk?.packageName ?: "package"}'. Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
            Activity.RESULT_CANCELED -> InstallResultStatus(
                outcome = InstallOutcomeType.CANCELLED,
                statusCode = 3, // PackageInstaller.STATUS_FAILURE_ABORTED
                title = "Install Cancelled (Status Code: 3 / STATUS_FAILURE_ABORTED)",
                details = "The system installer prompt was cancelled or closed (${dataIntentMessage ?: "RESULT_CANCELED"}). Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
            else -> InstallResultStatus(
                outcome = InstallOutcomeType.FAILED,
                statusCode = resultCode,
                title = "Install Failed (Status Code: $resultCode)",
                details = "System package installer returned failure code $resultCode (${dataIntentMessage ?: "INSTALL_FAILED"}). Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
        }

        _uiState.update {
            it.copy(
                pickedApkInfo = it.pickedApkInfo?.copy(cachedFile = null),
                installPreviewStage = InstallPreviewStage.COMPLETED,
                lastInstallResult = resultStatus,
                statusBannerMessage = "${resultStatus.title} — Temp APK cleaned up."
            )
        }
    }

    fun simulatePreviewInstallOutcome(outcome: InstallOutcomeType, statusCode: Int) {
        val currentApk = _uiState.value.pickedApkInfo
        val cachedFile = currentApk?.cachedFile
        val deleted = if (cachedFile != null && cachedFile.exists()) {
            runCatching { cachedFile.delete() }.getOrDefault(false)
        } else {
            true
        }

        val status = when (outcome) {
            InstallOutcomeType.SUCCESS -> InstallResultStatus(
                outcome = InstallOutcomeType.SUCCESS,
                statusCode = statusCode,
                title = "Preview Install Succeeded (Status Code: $statusCode / STATUS_SUCCESS)",
                details = "Simulated system installer completion for '${currentApk?.packageName ?: "com.claude.pulsesample"}'. Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
            InstallOutcomeType.CANCELLED -> InstallResultStatus(
                outcome = InstallOutcomeType.CANCELLED,
                statusCode = statusCode,
                title = "Preview Install Cancelled (Status Code: $statusCode / STATUS_FAILURE_ABORTED)",
                details = "User cancelled the system installer confirmation dialog. Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
            InstallOutcomeType.FAILED -> InstallResultStatus(
                outcome = InstallOutcomeType.FAILED,
                statusCode = statusCode,
                title = "Preview Install Failed (Status Code: $statusCode / STATUS_FAILURE_INVALID)",
                details = "Package installer reported failure code $statusCode. Temporary copied APK deleted from cache.",
                tempFileDeleted = deleted
            )
        }

        _uiState.update {
            it.copy(
                pickedApkInfo = it.pickedApkInfo?.copy(cachedFile = null),
                installPreviewStage = InstallPreviewStage.COMPLETED,
                lastInstallResult = status,
                statusBannerMessage = status.title
            )
        }
    }

    fun runInstallFlowPreview() {
        val currentApk = _uiState.value.pickedApkInfo
        if (currentApk == null) {
            try {
                throw IllegalStateException("No .apk file loaded. Pick an .apk file or tap a Test APK button before running the preview.")
            } catch (e: Throwable) {
                recordException(e, "Start Install Flow Preview")
            }
            return
        }
        if (!currentApk.isMinSdkCompatible) {
            try {
                throw IllegalStateException("Cannot install '${currentApk.packageName}': APK requires minimum SDK ${currentApk.minSdk}, but this phone runs SDK ${currentApk.hostDeviceSdk}.")
            } catch (e: Throwable) {
                recordException(e, "Minimum Android Version Check")
            }
            return
        }
        if (_uiState.value.isPreviewSimulating) return

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isPreviewSimulating = true,
                        installPreviewStage = InstallPreviewStage.VERIFYING_PACKAGE
                    )
                }
                delay(400)
                _uiState.update { it.copy(installPreviewStage = InstallPreviewStage.SYSTEM_PROMPT) }
                delay(550)
                _uiState.update { it.copy(installPreviewStage = InstallPreviewStage.INSTALLING_PROGRESS) }
                delay(550)
                simulatePreviewInstallOutcome(InstallOutcomeType.SUCCESS, 0)
                _uiState.update { it.copy(isPreviewSimulating = false) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isPreviewSimulating = false) }
                recordException(e, "Install Flow Preview")
            }
        }
    }

    fun setInstallPreviewStage(stage: InstallPreviewStage) {
        _uiState.update { it.copy(installPreviewStage = stage) }
    }

    // ========================================================================
    // 2. CLAUDE ARTIFACT TO APK & LIVE PREVIEW
    // ========================================================================

    fun updateClaudeRawInput(raw: String) {
        try {
            val (detectedType, files) = ClaudeApkConverter.parseClaudeInput(raw)
            val updatedConfig = _uiState.value.claudeConfig.copy(artifactType = detectedType)
            val diagnostics = ClaudeApkConverter.analyzeDiagnostics(updatedConfig, files)
            _uiState.update {
                it.copy(
                    claudeRawInput = raw,
                    claudeConfig = updatedConfig,
                    claudeExtractedFiles = files,
                    claudeDiagnostics = diagnostics
                )
            }
        } catch (e: Throwable) {
            recordException(e, "Parse Claude Input")
        }
    }

    fun selectClaudePreset(preset: ClaudePresetTemplate) {
        try {
            val (detectedType, files) = ClaudeApkConverter.parseClaudeInput(preset.rawInput)
            val cfg = preset.config.copy(artifactType = detectedType)
            val diagnostics = ClaudeApkConverter.analyzeDiagnostics(cfg, files)
            _uiState.update {
                it.copy(
                    claudeRawInput = preset.rawInput,
                    claudeConfig = cfg,
                    claudeExtractedFiles = files,
                    claudeDiagnostics = diagnostics,
                    claudeRuntimeErrors = emptyList(),
                    statusBannerMessage = "Loaded preset: ${preset.title} (${files.size} file(s), ${diagnostics.size} issue(s))"
                )
            }
        } catch (e: Throwable) {
            recordException(e, "Load Claude Preset")
        }
    }

    fun updateClaudeConfig(transform: (ClaudeApkConfig) -> ClaudeApkConfig) {
        try {
            val nextConfig = transform(_uiState.value.claudeConfig)
            val diagnostics = ClaudeApkConverter.analyzeDiagnostics(nextConfig, _uiState.value.claudeExtractedFiles)
            _uiState.update {
                it.copy(
                    claudeConfig = nextConfig,
                    claudeDiagnostics = diagnostics
                )
            }
        } catch (e: Throwable) {
            recordException(e, "Update Claude APK Config")
        }
    }

    fun importClaudeFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (fileName, content) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var name = "claude_artifact.html"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                val resolved = cursor.getString(idx)
                                if (!resolved.isNullOrBlank()) name = resolved
                            }
                        }
                    }
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("Unable to read text from selected Claude file.")
                    name to text
                }

                val ext = fileName.substringAfterLast('.', "html")
                val wrapped = "```$ext\n// filename: $fileName\n$content\n```"
                updateClaudeRawInput(wrapped)
                _uiState.update {
                    it.copy(statusBannerMessage = "Imported Claude file: $fileName")
                }
            } catch (e: Throwable) {
                recordException(e, "Import Claude File")
            }
        }
    }

    fun appendWebViewConsoleMessage(level: String, message: String, lineNumber: Int) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val logItem = ConsoleLogMessage(
            id = UUID.randomUUID().toString(),
            level = level,
            message = message,
            lineNumber = lineNumber,
            timestampLabel = timestamp
        )
        val isError = level.equals("ERROR", ignoreCase = true) ||
            message.contains("[RUNTIME_EXCEPTION]") ||
            message.contains("[BABEL_JSX_COMPILE_ERROR]") ||
            message.contains("Uncaught")

        _uiState.update { state ->
            val updatedRuntimeErrors = if (isError) {
                (listOf("Line $lineNumber: $message") + state.claudeRuntimeErrors).distinct().take(20)
            } else {
                state.claudeRuntimeErrors
            }
            state.copy(
                claudeConsoleLogs = (listOf(logItem) + state.claudeConsoleLogs).take(40),
                claudeRuntimeErrors = updatedRuntimeErrors
            )
        }

        if (isError) {
            recordException(
                RuntimeException("WebView Preview Error at line $lineNumber: $message"),
                "Claude Live Preview"
            )
        }
    }

    fun clearConsoleLogs() {
        _uiState.update {
            it.copy(claudeConsoleLogs = emptyList(), claudeRuntimeErrors = emptyList())
        }
    }

    fun toggleLivePreviewFullScreen(fullScreen: Boolean) {
        _uiState.update { it.copy(isLivePreviewFullScreen = fullScreen) }
    }

    fun packageClaudeFilesIntoApkZip(context: Context) {
        try {
            val state = _uiState.value
            val entries = ClaudeApkConverter.generateApkProjectEntries(
                config = state.claudeConfig,
                claudeFiles = state.claudeExtractedFiles
            )
            val slug = state.claudeConfig.appName.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "claude_apk_project" }

            _uiState.update {
                it.copy(
                    bundleName = "${slug}_apk_project",
                    stagedEntries = entries,
                    activeTab = PackFlowTab.BUNDLER
                )
            }
            packageCurrentStagingToZip(context)
        } catch (e: Throwable) {
            recordException(e, "Package Claude Files to APK Project")
        }
    }

    // ========================================================================
    // 3. ERROR LOG & DIAGNOSTICS
    // ========================================================================

    fun triggerSampleExceptionForTesting() {
        try {
            throw IllegalStateException("Simulated APK verification failure: AndroidManifest.xml missing required <application> tag or invalid package signature.")
        } catch (e: Throwable) {
            recordException(e, "Diagnostic Self-Test")
        }
    }

    fun clearCaughtExceptions() {
        _uiState.update {
            it.copy(
                caughtExceptions = emptyList(),
                claudeRuntimeErrors = emptyList(),
                statusBannerMessage = "Cleared error log history"
            )
        }
    }

    fun buildFormattedErrorLog(): String {
        val state = _uiState.value
        val claudeReport = ClaudeApkConverter.buildCopyableErrorReportForClaude(
            config = state.claudeConfig,
            files = state.claudeExtractedFiles,
            diagnostics = state.claudeDiagnostics,
            runtimeErrors = state.claudeRuntimeErrors
        )
        return PackagerEngine.formatFullErrorLogForClipboard(
            telemetry = state.runtimeTelemetry,
            caughtExceptions = state.caughtExceptions,
            claudeDiagnosticsReport = claudeReport
        )
    }

    // ========================================================================
    // 4. ZIP BUNDLER & RUNTIME APK INSPECTOR
    // ========================================================================

    fun updateBundleName(name: String) {
        _uiState.update { it.copy(bundleName = name) }
    }

    fun selectCompressionProfile(profile: CompressionProfile) {
        _uiState.update { it.copy(compressionProfile = profile) }
    }

    fun selectCategoryFilter(category: StagedEntryCategory?) {
        _uiState.update {
            val next = if (it.selectedCategoryFilter == category) null else category
            it.copy(selectedCategoryFilter = next)
        }
    }

    fun openNewFileDialog() {
        _uiState.update { it.copy(showNewFileDialog = true) }
    }

    fun dismissNewFileDialog() {
        _uiState.update { it.copy(showNewFileDialog = false) }
    }

    fun openEditEntry(entry: StagedEntry) {
        _uiState.update { it.copy(editingEntry = entry) }
    }

    fun dismissEditEntry() {
        _uiState.update { it.copy(editingEntry = null) }
    }

    fun addOrUpdateTextFile(relativePath: String, content: String, existingId: String? = null) {
        try {
            val newEntry = PackagerEngine.createTextEntry(relativePath, content)
            _uiState.update { state ->
                val updatedList = if (existingId != null) {
                    state.stagedEntries.map { if (it.id == existingId) newEntry.copy(id = existingId) else it }
                } else {
                    listOf(newEntry) + state.stagedEntries
                }
                state.copy(
                    stagedEntries = updatedList,
                    showNewFileDialog = false,
                    editingEntry = null,
                    statusBannerMessage = "Saved ${newEntry.relativePath} (${PackagerEngine.formatBytes(newEntry.sizeBytes.toLong())})"
                )
            }
        } catch (e: Throwable) {
            recordException(e, "Save Staged File")
        }
    }

    fun removeStagedEntry(entryId: String) {
        _uiState.update { state ->
            val target = state.stagedEntries.find { it.id == entryId }
            state.copy(
                stagedEntries = state.stagedEntries.filterNot { it.id == entryId },
                statusBannerMessage = target?.let { "Removed ${it.relativePath}" }
            )
        }
    }

    fun importUriToStaging(context: Context, uri: Uri, defaultSubfolder: String) {
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var displayName = "imported_item_${System.currentTimeMillis()}"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                val name = cursor.getString(idx)
                                if (!name.isNullOrBlank()) displayName = name
                            }
                        }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Unable to read stream from selected URI")
                    val path = if (defaultSubfolder.isNotBlank()) {
                        "$defaultSubfolder/$displayName"
                    } else {
                        displayName
                    }
                    PackagerEngine.createBinaryEntry(path, bytes)
                }
                _uiState.update { state ->
                    state.copy(
                        stagedEntries = listOf(entry) + state.stagedEntries,
                        statusBannerMessage = "Imported ${entry.relativePath} (${PackagerEngine.formatBytes(entry.sizeBytes.toLong())})"
                    )
                }
            } catch (e: Throwable) {
                recordException(e, "Import URI to Staging")
            }
        }
    }

    fun packageCurrentStagingToZip(context: Context) {
        val currentState = _uiState.value
        if (currentState.isPackaging) return

        _uiState.update { it.copy(isPackaging = true, statusBannerMessage = "Compressing & packaging archive...") }

        viewModelScope.launch {
            try {
                val archive = withContext(Dispatchers.IO) {
                    val bundlesDir = File(context.cacheDir, "bundles")
                    PackagerEngine.buildZipArchive(
                        outputDirectory = bundlesDir,
                        archiveBaseName = currentState.bundleName,
                        entries = currentState.stagedEntries,
                        compressionProfile = currentState.compressionProfile
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        isPackaging = false,
                        latestBuiltArchive = archive,
                        builtArchives = listOf(archive) + state.builtArchives.filterNot { it.fileName == archive.fileName },
                        statusBannerMessage = "Built ${archive.fileName} (${PackagerEngine.formatBytes(archive.compressedBytes)}, saved ${archive.compressionRatioPercent}%)"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isPackaging = false) }
                recordException(e, "Build ZIP Archive")
            }
        }
    }

    fun exportArchiveToUri(context: Context, archive: BuiltArchive, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        archive.file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Could not open destination output stream")
                }
                _uiState.update {
                    it.copy(statusBannerMessage = "Exported ${archive.fileName} to device storage")
                }
            } catch (e: Throwable) {
                recordException(e, "Export ZIP Archive")
            }
        }
    }

    fun refreshRuntimeTelemetry(context: Context) {
        viewModelScope.launch {
            try {
                val telemetry = withContext(Dispatchers.IO) {
                    PackagerEngine.inspectRuntimeApk(context)
                }
                _uiState.update { it.copy(runtimeTelemetry = telemetry) }
            } catch (e: Throwable) {
                recordException(e, "Refresh Runtime APK Telemetry")
            }
        }
    }

    fun stageTelemetryReport() {
        try {
            val telemetry = _uiState.value.runtimeTelemetry
                ?: throw IllegalStateException("Runtime telemetry not yet initialized.")
            val reportJson = """
{
  "appLabel": "${telemetry.appLabel}",
  "packageName": "${telemetry.packageName}",
  "versionName": "${telemetry.versionName}",
  "versionCode": ${telemetry.versionCode},
  "minSdk": ${telemetry.minSdk},
  "targetSdk": ${telemetry.targetSdk},
  "deviceSdkInt": ${telemetry.deviceSdkInt},
  "deviceModel": "${telemetry.deviceModel}",
  "primaryAbi": "${telemetry.primaryAbi}",
  "apkSizeBytes": ${telemetry.apkSizeBytes},
  "signingCertSha256": "${telemetry.signingCertSha256}",
  "isDebuggable": ${telemetry.isDebuggable}
}
            """.trimIndent()
            val entry = PackagerEngine.createTextEntry(
                relativePath = "diagnostics/apk_runtime_telemetry.json",
                content = reportJson,
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
            _uiState.update { state ->
                state.copy(
                    stagedEntries = listOf(entry) + state.stagedEntries.filterNot { it.relativePath == entry.relativePath },
                    activeTab = PackFlowTab.BUNDLER,
                    statusBannerMessage = "Staged diagnostics/apk_runtime_telemetry.json into bundler"
                )
            }
        } catch (e: Throwable) {
            recordException(e, "Stage Telemetry Report")
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
