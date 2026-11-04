// Prebuilt WebView "shell" for PackFlow's offline quick build.
// CI builds it (assembleRelease, unsigned) and copies the APK into
// app/src/main/assets/shell/webshell.apk. On the phone PackFlow swaps in the game's
// web files and icon, renames the package and label in the binary manifest, and signs it.
// The placeholders below must match com.example.core.ShellApk.
plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.packflow.shell"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.packflow.shell.placeholder"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.0.0-packflow"
  }

  buildTypes {
    release {
      // R8 strips the Kotlin stdlib AGP adds by default; the shell is plain Java.
      isMinifyEnabled = true
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // No signingConfig: the APK is signed on the phone with the user's own key.
    }
  }
  buildFeatures { buildConfig = false }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}
