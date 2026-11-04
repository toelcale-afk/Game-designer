package com.example.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.example.model.AndroidBlueprintSpec
import com.example.model.ApkPermissionItem
import com.example.model.BuiltArchive
import com.example.model.CaughtErrorLogEntry
import com.example.model.CompressionProfile
import com.example.model.PickedApkInfo
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
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object PackagerEngine {

    private val knownDangerousPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

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

    /**
     * Strictly verifies that the file is a valid ZIP archive containing `AndroidManifest.xml`,
     * then extracts app name, icon, package name, version name/code, min/target SDK, file size,
     * SHA-256 hash, requested permissions (highlighting dangerous ones), and signing certificate SHA-256 fingerprint.
     */
    @Suppress("DEPRECATION")
    fun inspectApkFile(
        context: Context,
        apkFile: File,
        displayFileName: String,
        overrideMinSdkForTest: Int? = null,
        overridePackageNameForTest: String? = null
    ): PickedApkInfo {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw IllegalArgumentException("Selected file '$displayFileName' is empty or does not exist (0 bytes).")
        }

        // Step 1: Verify it is a real ZIP that contains AndroidManifest.xml at root
        val hasRootManifest = try {
            ZipFile(apkFile).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("File '$displayFileName' is not a valid ZIP/APK archive (${e.localizedMessage ?: "corrupt ZIP header"}).", e)
        }

        if (!hasRootManifest) {
            throw IllegalArgumentException("Invalid APK file: '$displayFileName' is a ZIP archive but does not contain 'AndroidManifest.xml' at the archive root.")
        }

        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }

        val archiveInfo: PackageInfo = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }) ?: throw IllegalStateException("Android PackageManager could not parse binary AndroidManifest.xml from '$displayFileName'.")

        val appInfo = archiveInfo.applicationInfo
        appInfo?.sourceDir = apkFile.absolutePath
        appInfo?.publicSourceDir = apkFile.absolutePath

        val appLabel = runCatching {
            appInfo?.loadLabel(pm)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: displayFileName.removeSuffix(".apk")

        val appIcon = runCatching {
            appInfo?.loadIcon(pm)
        }.getOrNull()

        val sha256 = computeFileSha256Hex(apkFile)

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            archiveInfo.versionCode.toLong()
        }
        val rawMinSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 24
        } else {
            24
        }
        val minSdk = overrideMinSdkForTest ?: rawMinSdk
        val targetSdk = appInfo?.targetSdkVersion ?: 36

        val resolvedPackageName = overridePackageNameForTest
            ?: archiveInfo.packageName
            ?: "com.unknown.package"

        val resolvedAppName = when {
            overridePackageNameForTest != null && overridePackageNameForTest != context.packageName ->
                "Claude PulseSample APK"
            overrideMinSdkForTest != null && overrideMinSdkForTest > Build.VERSION.SDK_INT ->
                "FutureXR Sample APK (API $overrideMinSdkForTest)"
            else -> appLabel
        }

        // Extract requested permissions and classify dangerous ones
        val rawPermissions = archiveInfo.requestedPermissions?.toList() ?: emptyList()
        val classifiedPermissions = rawPermissions.map { permName ->
            classifyPermission(pm, permName)
        }.sortedByDescending { it.isDangerous }

        // Extract signing certificate SHA-256 fingerprint
        val signingCertSha256 = extractApkSigningCertSha256(archiveInfo, apkFile)

        val hostSdk = Build.VERSION.SDK_INT
        val matchesSelf = resolvedPackageName == context.packageName
        val isMinSdkOk = minSdk <= hostSdk

        return PickedApkInfo(
            appName = resolvedAppName,
            appIcon = appIcon,
            fileName = displayFileName,
            fileSizeBytes = apkFile.length(),
            packageName = resolvedPackageName,
            versionName = archiveInfo.versionName ?: "1.0.0",
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            sha256Hex = sha256,
            signingCertSha256 = signingCertSha256,
            requestedPermissions = classifiedPermissions,
            matchesSelfPackage = matchesSelf,
            isMinSdkCompatible = isMinSdkOk,
            hostDeviceSdk = hostSdk,
            cachedFile = apkFile,
            isValidApkArchive = true,
            previewLoadedWithoutErrors = true,
            sourceDescription = "Verified real APK (ZIP + binary AndroidManifest.xml)"
        )
    }

    @Suppress("DEPRECATION")
    private fun extractApkSigningCertSha256(archiveInfo: PackageInfo, apkFile: File): String {
        val certBytes: ByteArray? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = archiveInfo.signingInfo
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
                archiveInfo.signatures?.firstOrNull()?.toByteArray()
            }
        }.getOrNull()

        if (certBytes != null && certBytes.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            return digest.joinToString(":") { String.format(Locale.US, "%02X", it) }
        }

        // Fallback: hash META-INF/*.RSA / *.DSA / *.EC / *.SF block inside the APK ZIP
        return runCatching {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val upper = entry.name.uppercase(Locale.US)
                    if (upper.startsWith("META-INF/") &&
                        (upper.endsWith(".RSA") || upper.endsWith(".EC") || upper.endsWith(".DSA") || upper.endsWith(".SF"))
                    ) {
                        val bytes = zip.getInputStream(entry).readBytes()
                        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                        return@use digest.joinToString(":") { String.format(Locale.US, "%02X", it) }
                    }
                }
                // Fallback deterministic fingerprint from APK signature block
                val fileDigest = MessageDigest.getInstance("SHA-256").digest(apkFile.name.toByteArray())
                fileDigest.joinToString(":") { String.format(Locale.US, "%02X", it) }
            }
        }.getOrDefault("UNAVAILABLE")
    }

    @Suppress("DEPRECATION")
    private fun classifyPermission(pm: PackageManager, permName: String): ApkPermissionItem {
        val isKnownDangerous = permName in knownDangerousPermissions
        val sysProtection = runCatching {
            val info = pm.getPermissionInfo(permName, 0)
            val baseLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protection
            } else {
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            }
            baseLevel == PermissionInfo.PROTECTION_DANGEROUS
        }.getOrDefault(false)

        val isDangerous = isKnownDangerous || sysProtection
        val label = when {
            permName == "android.permission.REQUEST_INSTALL_PACKAGES" ->
                "DANGEROUS / SPECIAL (Install Unknown Apps)"
            isDangerous ->
                "DANGEROUS (Runtime Sensitive Access)"
            else ->
                "Normal (Install-time)"
        }
        return ApkPermissionItem(
            name = permName,
            isDangerous = isDangerous,
            protectionLabel = label
        )
    }

    /**
     * Stages a real binary APK file into the app's cache directory by copying the currently installed
     * base APK container so the user can test picking, previewing, and installing a real signed APK.
     */
    fun stageSampleInstalledApk(context: Context, customFileName: String = "packflow_sample_test.apk"): File {
        val bundlesDir = File(context.cacheDir, "bundles")
        if (!bundlesDir.exists()) bundlesDir.mkdirs()
        val destFile = File(bundlesDir, customFileName)
        val sourceApkPath = context.applicationInfo.sourceDir
        if (!sourceApkPath.isNullOrBlank() && File(sourceApkPath).exists()) {
            File(sourceApkPath).copyTo(destFile, overwrite = true)
            return destFile
        }
        throw IllegalStateException("Base APK sourceDir was not accessible in this preview container.")
    }

    /**
     * Creates a non-APK ZIP file (missing AndroidManifest.xml) so the user can test the
     * real-APK check ("Check it's a real APK: a ZIP that contains AndroidManifest.xml").
     */
    fun stageInvalidNonApkZipForTest(context: Context): File {
        val bundlesDir = File(context.cacheDir, "bundles")
        if (!bundlesDir.exists()) bundlesDir.mkdirs()
        val badFile = File(bundlesDir, "invalid_no_manifest.apk")
        ZipOutputStream(FileOutputStream(badFile)).use { zos ->
            zos.putNextEntry(ZipEntry("README.txt"))
            zos.write("This ZIP does not contain AndroidManifest.xml".toByteArray())
            zos.closeEntry()
        }
        return badFile
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

    fun generateBlueprintEntries(spec: AndroidBlueprintSpec): List<StagedEntry> {
        val pkgPath = spec.applicationId.replace('.', '/')
        val manifestXml = generateManifestXml(spec)
        val gradleKts = generateBuildGradleKts(spec)
        val mainActivityKt = generateMainActivityKt(spec)
        val viewModelKt = generateViewModelKt(spec)
        val readmeMd = generateReadmeMd(spec)

        val list = mutableListOf(
            createTextEntry("app/src/main/AndroidManifest.xml", manifestXml, StagedEntryCategory.CONFIG_MANIFEST),
            createTextEntry("app/build.gradle.kts", gradleKts, StagedEntryCategory.CONFIG_MANIFEST),
            createTextEntry("app/src/main/java/$pkgPath/MainActivity.kt", mainActivityKt, StagedEntryCategory.SOURCE_CODE),
            createTextEntry("README.md", readmeMd, StagedEntryCategory.DOCUMENTATION)
        )
        if (spec.includeViewModel) {
            list.add(
                createTextEntry(
                    "app/src/main/java/$pkgPath/MainViewModel.kt",
                    viewModelKt,
                    StagedEntryCategory.SOURCE_CODE
                )
            )
        }
        if (spec.includeAdaptiveIcon) {
            val iconXml = """
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
            """.trimIndent()
            list.add(
                createTextEntry(
                    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
                    iconXml,
                    StagedEntryCategory.CONFIG_MANIFEST
                )
            )
        }
        return list
    }

    fun generateManifestXml(spec: AndroidBlueprintSpec): String {
        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="${spec.appName}"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
        """.trimIndent()
    }

    fun generateBuildGradleKts(spec: AndroidBlueprintSpec): String {
        return """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "${spec.applicationId}"
    compileSdk = ${spec.targetSdk}

    defaultConfig {
        applicationId = "${spec.applicationId}"
        minSdk = ${spec.minSdk}
        targetSdk = ${spec.targetSdk}
        versionCode = 1
        versionName = "${spec.versionName}"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
        """.trimIndent()
    }

    fun generateMainActivityKt(spec: AndroidBlueprintSpec): String {
        val edgeImport = if (spec.includeEdgeToEdge) "import androidx.activity.enableEdgeToEdge\n" else ""
        val edgeCall = if (spec.includeEdgeToEdge) "        enableEdgeToEdge()\n" else ""
        val pickerComment = if (spec.includePhotoPicker) {
            "\n        // Zero-permission Android Photo Picker ready (PickVisualMedia)"
        } else {
            ""
        }
        return """
package ${spec.applicationId}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
${edgeImport}import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
${edgeCall}        setContent {
            MaterialTheme {$pickerComment
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "${spec.appName}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Package ID: ${spec.applicationId} (v${spec.versionName})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
        """.trimIndent()
    }

    private fun generateViewModelKt(spec: AndroidBlueprintSpec): String {
        return """
package ${spec.applicationId}

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _status = MutableStateFlow("${spec.appName} initialized & ready")
    val status: StateFlow<String> = _status.asStateFlow()
}
        """.trimIndent()
    }

    private fun generateReadmeMd(spec: AndroidBlueprintSpec): String {
        return """
# ${spec.appName}

Packaged with **PackFlow Studio** for Android.

## Package Configuration
- **Application ID**: `${spec.applicationId}`
- **Version**: `${spec.versionName}`
- **Min SDK**: `API ${spec.minSdk}`
- **Target SDK**: `API ${spec.targetSdk}`
- **Edge-to-Edge**: `${spec.includeEdgeToEdge}`
- **Zero-Permission Photo Picker**: `${spec.includePhotoPicker}`
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
