package com.example.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.BuiltArchive
import com.example.model.CaughtErrorLogEntry
import com.example.model.CompressionProfile
import com.example.model.RuntimeApkTelemetry
import com.example.model.StagedEntry
import com.example.model.StagedEntryCategory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackagerEngine {

    fun computeCrc32Hex(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return String.format(Locale.US, "%08X", crc.value)
    }

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun computeFileSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun androidVersionLabel(sdkInt: Int): String {
        val releaseName = when (sdkInt) {
            24 -> "Android 7.0"
            25 -> "Android 7.1"
            26 -> "Android 8.0"
            27 -> "Android 8.1"
            28 -> "Android 9"
            29 -> "Android 10"
            30 -> "Android 11"
            31 -> "Android 12"
            32 -> "Android 12L"
            33 -> "Android 13"
            34 -> "Android 14"
            35 -> "Android 15"
            36 -> "Android 16"
            else -> if (sdkInt > 36) "Android Next" else "Android API $sdkInt"
        }
        return "API $sdkInt ($releaseName)"
    }

    fun toCaughtErrorLogEntry(
        throwable: Throwable,
        contextHint: String = "App Operation"
    ): CaughtErrorLogEntry {
        val frames = throwable.stackTrace
        val appFrame = frames.firstOrNull { it.className.startsWith("com.example") }
            ?: frames.firstOrNull()

        val fileName = appFrame?.fileName ?: "UnknownSource.kt"
        val lineNumber = appFrame?.lineNumber ?: 1
        val methodName = if (appFrame != null) {
            "${appFrame.className.substringAfterLast('.')}.${appFrame.methodName}()"
        } else {
            contextHint
        }
        val errorType = throwable::class.java.simpleName.ifBlank { throwable.javaClass.name }
        val message = throwable.localizedMessage ?: throwable.message ?: "No detailed message provided"

        val explanation = buildPlainEnglishExplanation(errorType, message, fileName, lineNumber, contextHint)
        val topFrames = frames.take(6).joinToString("\n") { frame ->
            "  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
        }
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

        return CaughtErrorLogEntry(
            id = UUID.randomUUID().toString(),
            errorType = errorType,
            message = message,
            fileName = fileName,
            lineNumber = lineNumber,
            methodName = methodName,
            plainEnglishExplanation = explanation,
            timestamp = timestamp,
            rawStackTraceSnippet = "$errorType: $message\n$topFrames"
        )
    }

    private fun buildPlainEnglishExplanation(
        errorType: String,
        message: String,
        fileName: String,
        lineNumber: Int,
        contextHint: String
    ): String {
        return when {
            errorType.contains("ZipException", ignoreCase = true) ->
                "During '$contextHint' at $fileName:$lineNumber, the file could not be opened as a valid ZIP/APK archive ($message). Ensure the file is a valid compressed .apk or .zip binary and not corrupted."
            errorType.contains("IllegalArgumentException", ignoreCase = true) ->
                "Validation error during '$contextHint' at $fileName:$lineNumber: $message"
            errorType.contains("IllegalStateException", ignoreCase = true) ->
                "State check failed during '$contextHint' at $fileName:$lineNumber: $message"
            errorType.contains("FileNotFoundException", ignoreCase = true) ->
                "The requested file or URI could not be found at $fileName:$lineNumber ($message)."
            errorType.contains("ActivityNotFoundException", ignoreCase = true) ->
                "The Android system activity for '$contextHint' could not be launched at $fileName:$lineNumber ($message)."
            else ->
                "Caught $errorType during '$contextHint' in $fileName at line $lineNumber: $message"
        }
    }

    fun formatFullErrorLogForClipboard(
        telemetry: RuntimeApkTelemetry?,
        caughtExceptions: List<CaughtErrorLogEntry>,
        claudeDiagnosticsReport: String?
    ): String {
        val sb = StringBuilder()
        val androidVer = if (telemetry != null) {
            "Android ${telemetry.deviceRelease} (API ${telemetry.deviceSdkInt}) • ${telemetry.deviceModel}"
        } else {
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }
        val appVer = if (telemetry != null) {
            "${telemetry.appLabel} v${telemetry.versionName} (VersionCode ${telemetry.versionCode}) [${telemetry.packageName}]"
        } else {
            "PackFlow Studio v1.0 (VersionCode 1) [com.aistudio.packflow.xkrv]"
        }

        sb.appendLine("====================================================")
        sb.appendLine("PACKFLOW STUDIO — DIAGNOSTIC & EXCEPTION LOG")
        sb.appendLine("Android Version : $androidVer")
        sb.appendLine("App Version     : $appVer")
        sb.appendLine("Target SDK      : API 36 (Android 16)")
        sb.appendLine("Generated At    : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("====================================================")
        sb.appendLine()

        if (caughtExceptions.isEmpty()) {
            sb.appendLine("[CAUGHT EXCEPTIONS]: 0 runtime exceptions recorded.")
        } else {
            sb.appendLine("[CAUGHT EXCEPTIONS (${caughtExceptions.size})]:")
            caughtExceptions.forEachIndexed { idx, entry ->
                sb.appendLine("${idx + 1}. [${entry.timestamp}] ${entry.errorType} at ${entry.fileName}:line ${entry.lineNumber} (${entry.methodName})")
                sb.appendLine("   Message     : ${entry.message}")
                sb.appendLine("   Explanation : ${entry.plainEnglishExplanation}")
                sb.appendLine("   Stack Trace :")
                sb.appendLine(entry.rawStackTraceSnippet.prependIndent("     "))
                sb.appendLine()
            }
        }

        if (!claudeDiagnosticsReport.isNullOrBlank()) {
            sb.appendLine("----------------------------------------------------")
            sb.appendLine("[CLAUDE ARTIFACT & CODE DIAGNOSTICS]:")
            sb.appendLine(claudeDiagnosticsReport)
        }

        return sb.toString().trim()
    }

    fun createTextEntry(
        relativePath: String,
        content: String,
        category: StagedEntryCategory = inferCategory(relativePath)
    ): StagedEntry {
        val cleanPath = sanitizeRelativePath(relativePath)
        val bytes = content.toByteArray(Charsets.UTF_8)
        return StagedEntry(
            id = UUID.randomUUID().toString(),
            relativePath = cleanPath,
            category = category,
            bytes = bytes,
            textPreview = content,
            crc32Hex = computeCrc32Hex(bytes)
        )
    }

    fun createBinaryEntry(
        relativePath: String,
        bytes: ByteArray,
        category: StagedEntryCategory = inferCategory(relativePath)
    ): StagedEntry {
        val cleanPath = sanitizeRelativePath(relativePath)
        val isText = cleanPath.endsWith(".kt") ||
            cleanPath.endsWith(".xml") ||
            cleanPath.endsWith(".json") ||
            cleanPath.endsWith(".md") ||
            cleanPath.endsWith(".txt") ||
            cleanPath.endsWith(".gradle") ||
            cleanPath.endsWith(".kts")
        val preview = if (isText && bytes.size <= 32_768) {
            runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
        return StagedEntry(
            id = UUID.randomUUID().toString(),
            relativePath = cleanPath,
            category = category,
            bytes = bytes,
            textPreview = preview,
            crc32Hex = computeCrc32Hex(bytes)
        )
    }

    fun inferCategory(path: String): StagedEntryCategory {
        val lower = path.lowercase(Locale.US)
        return when {
            lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".js") ||
                lower.endsWith(".jsx") || lower.endsWith(".tsx") || lower.endsWith(".html") ||
                lower.endsWith(".css") -> StagedEntryCategory.SOURCE_CODE
            lower.endsWith(".xml") || lower.endsWith(".kts") || lower.endsWith(".gradle") ||
                lower.endsWith(".json") || lower.endsWith(".properties") || lower.endsWith(".yml") -> StagedEntryCategory.CONFIG_MANIFEST
            lower.endsWith(".md") || lower.endsWith(".txt") -> StagedEntryCategory.DOCUMENTATION
            else -> StagedEntryCategory.MEDIA_ASSET
        }
    }

    fun sanitizeRelativePath(rawPath: String): String {
        return rawPath.trim()
            .replace("\\", "/")
            .trimStart('/')
            .replace("..", "_")
            .ifBlank { "untitled_file.txt" }
    }

    fun buildZipArchive(
        outputDirectory: File,
        archiveBaseName: String,
        entries: List<StagedEntry>,
        compressionProfile: CompressionProfile
    ): BuiltArchive {
        if (entries.isEmpty()) {
            throw IllegalStateException("Cannot build ZIP archive: staging area has 0 files.")
        }
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val safeBase = archiveBaseName
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .removeSuffix(".zip")
            .ifBlank { "packflow_bundle" }

        val fileName = "${safeBase}.zip"
        val targetFile = File(outputDirectory, fileName)

        val manifestJson = buildPackageManifestJson(safeBase, entries, compressionProfile)
        val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)

        var totalUncompressed = 0L
        val usedPaths = mutableSetOf<String>()

        FileOutputStream(targetFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                ZipOutputStream(bos).use { zos ->
                    zos.setLevel(compressionProfile.zipLevel)

                    for (entry in entries) {
                        var entryPath = sanitizeRelativePath(entry.relativePath)
                        if (!usedPaths.add(entryPath)) {
                            entryPath = "${entryPath.substringBeforeLast(".")}_${entry.id.take(4)}.${entryPath.substringAfterLast(".", "bin")}"
                            usedPaths.add(entryPath)
                        }
                        val zipEntry = ZipEntry(entryPath)
                        zipEntry.time = entry.addedTimestamp
                        zos.putNextEntry(zipEntry)
                        zos.write(entry.bytes)
                        zos.closeEntry()
                        totalUncompressed += entry.bytes.size
                    }

                    if ("PACKAGE_MANIFEST.json" !in usedPaths) {
                        val manifestEntry = ZipEntry("PACKAGE_MANIFEST.json")
                        zos.putNextEntry(manifestEntry)
                        zos.write(manifestBytes)
                        zos.closeEntry()
                        totalUncompressed += manifestBytes.size
                    }
                }
            }
        }

        val archiveBytes = targetFile.readBytes()
        val sha256 = computeSha256Hex(archiveBytes)

        return BuiltArchive(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            file = targetFile,
            entryCount = entries.size + 1,
            uncompressedBytes = totalUncompressed,
            compressedBytes = targetFile.length(),
            sha256Hex = sha256,
            compressionProfile = compressionProfile
        )
    }

    private fun buildPackageManifestJson(
        bundleName: String,
        entries: List<StagedEntry>,
        profile: CompressionProfile
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val itemsJson = entries.joinToString(",\n    ") { item ->
            """{"path": "${item.relativePath}", "category": "${item.category.name}", "sizeBytes": ${item.sizeBytes}, "crc32": "${item.crc32Hex}"}"""
        }
        return """
{
  "packageBundle": "$bundleName",
  "createdBy": "PackFlow Studio Android",
  "createdAt": "$timestamp",
  "compressionProfile": "${profile.label}",
  "totalFiles": ${entries.size},
  "files": [
    $itemsJson
  ]
}
        """.trimIndent()
    }

    @Suppress("DEPRECATION")
    fun inspectRuntimeApk(context: Context): RuntimeApkTelemetry {
        val pm = context.packageManager
        val pkgName = context.packageName
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkgName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES).toLong()
                )
            )
        } else {
            pm.getPackageInfo(
                pkgName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
            )
        }

        val appInfo = pkgInfo.applicationInfo
        val appLabel = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "PackFlow Studio"
        val sourceDir = appInfo?.sourceDir ?: ""
        val dataDir = appInfo?.dataDir ?: context.filesDir.absolutePath
        val apkSize = if (sourceDir.isNotEmpty()) {
            runCatching { File(sourceDir).length() }.getOrDefault(0L)
        } else {
            0L
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            pkgInfo.versionCode.toLong()
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 24
        } else {
            24
        }
        val targetSdk = appInfo?.targetSdkVersion ?: 36

        val certBytes: ByteArray? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                val sigs = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    null
                }
                sigs?.firstOrNull()?.toByteArray()
            } else {
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }
        }.getOrNull()

        val certFingerprint = if (certBytes != null) {
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString(":") { String.format(Locale.US, "%02X", it) }
        } else {
            "DEBUG-KEYSTORE-SHA256"
        }

        val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        val isDebuggable = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        return RuntimeApkTelemetry(
            appLabel = appLabel,
            packageName = pkgName,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            deviceSdkInt = Build.VERSION.SDK_INT,
            deviceRelease = Build.VERSION.RELEASE ?: "Android",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            primaryAbi = primaryAbi,
            apkSizeBytes = apkSize,
            sourceDir = sourceDir,
            dataDir = dataDir,
            signingCertSha256 = certFingerprint,
            declaredPermissions = permissions,
            firstInstallTimeMillis = pkgInfo.firstInstallTime,
            lastUpdateTimeMillis = pkgInfo.lastUpdateTime,
            isDebuggable = isDebuggable
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
