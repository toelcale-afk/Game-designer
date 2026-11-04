package com.example.model

import android.graphics.drawable.Drawable
import java.io.File

enum class StagedEntryCategory(val label: String) {
    SOURCE_CODE("Code & Kotlin"),
    CONFIG_MANIFEST("Manifest & Gradle"),
    DOCUMENTATION("Docs & Notes"),
    MEDIA_ASSET("Media & Binary")
}

enum class CompressionProfile(val label: String, val zipLevel: Int, val description: String) {
    BALANCED("Balanced (Deflate 6)", 6, "Standard APK/ZIP deflate balance"),
    MAXIMUM("Maximum (Deflate 9)", 9, "Smallest archive footprint"),
    FAST("Fast (Deflate 1)", 1, "Rapid bundling for large media")
}

data class StagedEntry(
    val id: String,
    val relativePath: String,
    val category: StagedEntryCategory,
    val bytes: ByteArray,
    val textPreview: String?,
    val crc32Hex: String,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val sizeBytes: Int get() = bytes.size
}

data class BuiltArchive(
    val id: String,
    val fileName: String,
    val file: File,
    val entryCount: Int,
    val uncompressedBytes: Long,
    val compressedBytes: Long,
    val sha256Hex: String,
    val compressionProfile: CompressionProfile,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val compressionRatioPercent: Int
        get() = if (uncompressedBytes > 0L) {
            (((uncompressedBytes - compressedBytes).toDouble() / uncompressedBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 99)
        } else {
            0
        }
}

enum class ClaudeArtifactType(val label: String, val badge: String) {
    HTML_WEB_APP("HTML5 / JS / CSS Artifact", "WebView APK"),
    REACT_JSX_ARTIFACT("React JSX / TSX Component", "React WebAPK"),
    KOTLIN_COMPOSE("Kotlin Jetpack Compose", "Native Compose APK")
}

data class ClaudeSourceFile(
    val id: String,
    val fileName: String,
    val language: String,
    val content: String
)

data class ClaudeApkConfig(
    val appName: String = "Claude Pulse App",
    val applicationId: String = "com.claude.pulseapp",
    val versionName: String = "1.0.0",
    val artifactType: ClaudeArtifactType = ClaudeArtifactType.HTML_WEB_APP,
    val fullscreenMode: Boolean = false,
    val enableJsBridge: Boolean = true,
    val includeGithubActionsApkBuilder: Boolean = true
)

enum class DiagnosticSeverity(val label: String) {
    ERROR("ERROR"),
    WARNING("WARNING"),
    RUNTIME_ERROR("RUNTIME CRASH")
}

data class ClaudeDiagnosticIssue(
    val id: String,
    val severity: DiagnosticSeverity,
    val fileName: String,
    val lineNumber: Int?,
    val title: String,
    val description: String,
    val suggestedFix: String,
    val codeSnippet: String? = null
)

data class ConsoleLogMessage(
    val id: String,
    val level: String,
    val message: String,
    val lineNumber: Int,
    val timestampLabel: String
)

data class ApkPermissionItem(
    val name: String,
    val isDangerous: Boolean,
    val protectionLabel: String
)

data class PickedApkInfo(
    val appName: String,
    val appIcon: Drawable?,
    val fileName: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sha256Hex: String,
    val signingCertSha256: String,
    val requestedPermissions: List<ApkPermissionItem>,
    val matchesSelfPackage: Boolean,
    val isMinSdkCompatible: Boolean,
    val hostDeviceSdk: Int,
    val cachedFile: File?,
    val isValidApkArchive: Boolean,
    val previewLoadedWithoutErrors: Boolean,
    val sourceDescription: String
)

enum class InstallOutcomeType {
    SUCCESS,
    CANCELLED,
    FAILED
}

data class InstallResultStatus(
    val outcome: InstallOutcomeType,
    val statusCode: Int,
    val title: String,
    val details: String,
    val tempFileDeleted: Boolean
)

enum class InstallPreviewStage(val stepIndex: Int, val title: String, val description: String) {
    IDLE(0, "Ready to Preview Install Flow", "Select an .apk file or load the built-in test APK to inspect metadata and run the installer."),
    VERIFYING_PACKAGE(1, "Step 1: ZIP & AndroidManifest.xml Check", "Verified valid ZIP archive containing binary AndroidManifest.xml, SHA-256 hash, and signing certificate."),
    SYSTEM_PROMPT(2, "Step 2: System Package Installer Prompt", "Launching system package installer via FileProvider URI (ACTION_INSTALL_PACKAGE) for user confirmation."),
    INSTALLING_PROGRESS(3, "Step 3: Verifying & Staging APK", "System package installer verifying minimum SDK compatibility and package signature."),
    COMPLETED(4, "Step 4: Install Flow Finished & Temp File Cleaned", "Completed installer flow and deleted temporary copied APK from cache.")
}

data class CaughtErrorLogEntry(
    val id: String,
    val errorType: String,
    val message: String,
    val fileName: String,
    val lineNumber: Int,
    val methodName: String,
    val plainEnglishExplanation: String,
    val timestamp: String,
    val rawStackTraceSnippet: String
)

data class AndroidBlueprintSpec(
    val appName: String = "NovaPulse",
    val applicationId: String = "com.aistudio.novapulse",
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val versionName: String = "1.0.0",
    val includeEdgeToEdge: Boolean = true,
    val includePhotoPicker: Boolean = true,
    val includeViewModel: Boolean = true,
    val includeAdaptiveIcon: Boolean = true
)

data class RuntimeApkTelemetry(
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val deviceSdkInt: Int,
    val deviceRelease: String,
    val deviceModel: String,
    val primaryAbi: String,
    val apkSizeBytes: Long,
    val sourceDir: String,
    val dataDir: String,
    val signingCertSha256: String,
    val declaredPermissions: List<String>,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val isDebuggable: Boolean
)

enum class PackFlowTab {
    INSTALL_APK,
    CLAUDE_STUDIO,
    BUNDLER,
    INSPECTOR,
    ERROR_LOG
}
