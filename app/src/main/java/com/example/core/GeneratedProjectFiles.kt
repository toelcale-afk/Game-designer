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

    /** Escapes text for use inside a Kotlin string literal in generated code. */
    fun kotlinString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$")

    /**
     * MainActivity for generated WebView apps. Serves app/src/main/assets/ from
     * https://appassets.androidplatform.net/assets/ (so fetch() and modules work), exposes the
     * error-catcher bridge, and optionally the AndroidApkBridge helper.
     */
    fun webHostActivity(packageName: String, appName: String, enableJsBridge: Boolean, fullscreen: Boolean): String =
        WEB_HOST_TEMPLATE
            .replace("__PACKAGE__", packageName)
            .replace("__APP_NAME__", kotlinString(appName))
            .replace("__JS_BRIDGE__", enableJsBridge.toString())
            .replace("__FULLSCREEN__", fullscreen.toString())

    private const val WEB_HOST_TEMPLATE = """package __PACKAGE__

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    serveAsset(request.url.host, request.url.path)
            }
            // PackFlow error catcher: web errors go to the same log as crashes.
            addJavascriptInterface(PackFlowErrorBridge(this@MainActivity), "PackFlowAndroid")
            if (__JS_BRIDGE__) {
                addJavascriptInterface(ClaudeAndroidBridge(), "AndroidApkBridge")
            }
        }
        setContentView(webView)
        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl("https://" + HOST + "/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun serveAsset(host: String?, path: String?): WebResourceResponse? {
        if (host != HOST || path == null || !path.startsWith("/assets/")) return null
        val assetPath = path.removePrefix("/assets/")
        if (assetPath.contains("..")) return null
        val ext = assetPath.substringAfterLast('.', "").lowercase()
        val mime = MIME[ext] ?: "application/octet-stream"
        return try {
            WebResourceResponse(mime, if (mime.startsWith("text/") || ext == "json") "UTF-8" else null, assets.open(assetPath))
        } catch (e: IOException) {
            WebResourceResponse("text/plain", "UTF-8", null).apply { setStatusCodeAndReasonPhrase(404, "Not Found") }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && __FULLSCREEN__) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    inner class ClaudeAndroidBridge {
        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getAppName(): String = "__APP_NAME__"

        @JavascriptInterface
        fun getPackageName(): String = packageName
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private val MIME = mapOf(
            "html" to "text/html", "htm" to "text/html", "js" to "text/javascript", "mjs" to "text/javascript",
            "css" to "text/css", "json" to "application/json", "png" to "image/png", "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml",
            "wav" to "audio/wav", "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
            "txt" to "text/plain", "woff2" to "font/woff2", "ttf" to "font/ttf", "wasm" to "application/wasm"
        )
    }
}
"""
}
