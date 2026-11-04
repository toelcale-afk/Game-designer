package com.example.core

import com.example.model.ClaudeApkConfig
import com.example.model.ClaudeArtifactType

/**
 * Build files for the Android projects PackFlow generates (GitHub build path).
 * Versions here are a known-good set for generated apps and are independent of
 * PackFlow's own build.
 */
object GeneratedProjectFiles {

    const val AGP_VERSION = "8.7.2"
    const val KOTLIN_VERSION = "2.0.21"
    const val GRADLE_VERSION = "8.11.1"
    const val COMPOSE_BOM = "2024.10.01"
    const val ACTIVITY_COMPOSE = "1.9.3"

    const val ROOT_BUILD_GRADLE = """plugins {
    id("com.android.application") version "$AGP_VERSION" apply false
    id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "$KOTLIN_VERSION" apply false
}
"""

    const val GRADLE_PROPERTIES = """org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
"""

    fun appBuildGradle(config: ClaudeApkConfig): String {
        val compose = config.artifactType == ClaudeArtifactType.KOTLIN_COMPOSE
        return """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "${config.applicationId}"
    compileSdk = 35

    defaultConfig {
        applicationId = "${config.applicationId}"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "${config.versionName}"
    }

    // keystore/debug.keystore is created once by the GitHub workflow and committed, so
    // every build has the same signature and installs update the app in place.
    val sharedKeystore = rootProject.file("keystore/debug.keystore")
    signingConfigs {
        create("shared") {
            storeFile = sharedKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            if (sharedKeystore.exists()) signingConfig = signingConfigs.getByName("shared")
        }
    }

    buildFeatures {
        compose = ${if (compose) "true" else "false"}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:$COMPOSE_BOM"))
    implementation("androidx.activity:activity-compose:$ACTIVITY_COMPOSE")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
"""
    }

    fun workflowYaml(config: ClaudeApkConfig): String {
        val artifact = config.appName.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "app" }
        return """name: Build Android APK

on:
  push:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build-apk:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Create shared debug keystore (first build only)
        run: |
          if [ ! -f keystore/debug.keystore ]; then
            mkdir -p keystore
            keytool -genkeypair -keystore keystore/debug.keystore -storepass android -keypass android \
              -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
            git config user.name "github-actions[bot]"
            git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
            git add -f keystore/debug.keystore
            git commit -m "Add shared debug keystore"
            git push origin "HEAD:${'$'}{GITHUB_REF_NAME}" || true
          fi

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '$GRADLE_VERSION'

      - name: Compile debug APK
        run: gradle assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: $artifact-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 1
"""
    }

    private val PACKAGE_LINE = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)")

    fun kotlinPackageOf(source: String): String? = PACKAGE_LINE.find(source)?.groupValues?.get(1)

    fun xmlEscape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "\\'")

    fun manifestXml(appName: String, activityClass: String, applicationClass: String, hasIcon: Boolean): String {
        val icon = if (hasIcon) {
            "\n        android:icon=\"@mipmap/ic_launcher\"\n        android:roundIcon=\"@mipmap/ic_launcher_round\""
        } else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name="$applicationClass"
        android:allowBackup="true"
        android:label="${xmlEscape(appName)}"$icon
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity
            android:name="$activityClass"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
    }
}
