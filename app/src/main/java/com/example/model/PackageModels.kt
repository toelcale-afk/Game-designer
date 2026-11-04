package com.example.model

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
