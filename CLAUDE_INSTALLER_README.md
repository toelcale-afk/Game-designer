# PackFlow Studio: APK Installer add-on

Package: `com.aistudio.packflow.xkrv`. This zip has **new files only**. It doesn't replace anything you already have.

```
app/src/main/java/com/aistudio/packflow/xkrv/installer/
  ApkInspector.kt      reads an APK: name, icon, version, SDKs, permissions, signing certs, SHA-256
  ApkInstaller.kt      installs through the system PackageInstaller and receives the install result
  ErrorLog.kt          app-wide log, saved to filesDir/installer_log.txt
  InstallerScreens.kt  Compose UI: picker, APK preview, error log (with Copy)
MANIFEST_ADDITIONS.xml one permission and one receiver to merge into your manifest
preview/apk-preview.html  standalone preview that runs in any browser, no AI Studio needed
```

No API keys are used or needed anywhere in this add-on.

---

## 1. Check the AI Studio project before adding anything

Last time the project was rebuilt from scratch, so check these first:

- [ ] `app/build.gradle(.kts)` has `namespace = "com.aistudio.packflow.xkrv"` and `applicationId = "com.aistudio.packflow.xkrv"`
- [ ] `app/src/main/java/com/aistudio/packflow/xkrv/MainActivity.kt` exists
- [ ] Your existing ZIP packager, Android blueprint generator and APK/package inspector source files are there (in that same package folder or under it)
- [ ] The app uses Jetpack Compose. `MainActivity` should call `setContent { ... }`. If it uses XML layouts instead, tell me and I'll send a version for that.

If any box fails, stop. Send me the file tree (just the paths) or a zip of `app/src` plus the two `build.gradle` files, **without** `local.properties`, `.env` or any `secrets*` file.

Key check before you zip anything: search the project for `AIza` (Gemini/Google keys start with this). If it shows up in a file you'd send, remove it first.

---

## 2. Add the installer

1. Copy the `installer/` folder into `app/src/main/java/com/aistudio/packflow/xkrv/`.
2. Merge `MANIFEST_ADDITIONS.xml` into `app/src/main/AndroidManifest.xml`. That's one `<uses-permission>` and one `<receiver>`.
3. Show the screen. The quickest way is in `MainActivity`:

```kotlin
import com.aistudio.packflow.xkrv.installer.PackFlowInstaller

setContent {
    YourAppTheme {               // keep your existing theme wrapper
        PackFlowInstaller()      // or put this on its own tab/route
    }
}
```

If the app already has tabs or a nav bar, add an "Installer" tab that calls `PackFlowInstaller()`.

**Dependencies:** it only uses what a Compose app already has: `androidx.activity:activity-compose`, `androidx.compose.material3:material3` and `kotlinx-coroutines-android`. No new libraries.

**How it behaves**
- **File picker:** the system picker (Storage Access Framework), filtered to APKs. No storage permission needed.
- **Preview:** shows the real app name and icon, package, version name and code, min/target SDK, size, file SHA-256, each signing cert's subject with SHA-256 and SHA-1 (copy buttons included), and a permission list with sensitive ones flagged. It warns if the APK is signed with a debug key.
- **Install:** uses the PackageInstaller session API, so Android shows its own confirm dialog. The first time, it sends you to "Install unknown apps" to allow PackFlow.
- **Error log:** every pick, parse, install step and failure goes here with a timestamp. You can copy or clear it. Failures include a plain hint (for example, CONFLICT means a different signing key or a newer version is already installed).
- **Fold:** state survives folding and unfolding. The preview switches to two columns on the inner screen.
- **Not supported yet:** split bundles (`.apks`, `.xapk`, `.apkm`, `.aab`). You get a clear message instead of a crash.

---

## 3. Build and install on the Galaxy Z Fold 8

**On the phone (one time)**
1. Settings → About phone → Software information → tap **Build number** 7 times.
2. Settings → Developer options → turn on **USB debugging**.
3. Settings → Security and privacy → **Auto Blocker**. If it's on, turn it off while you test. It blocks USB commands and installs from outside the Play Store.
4. Connect with a USB-C data cable. When the phone asks "Allow USB debugging?", tick "Always allow" and tap Allow.

**In Android Studio**
1. File → Open and choose the project root (the folder with `settings.gradle` or `settings.gradle.kts`).
2. Wait for Gradle sync. If it complains about Java, go to Settings → Build, Execution, Deployment → Build Tools → Gradle and set **Gradle JDK** to 17 or newer (the bundled JBR is fine).
3. Pick the Fold in the device dropdown on the toolbar. It shows as "samsung SM-F…".
4. Press **Run ▶** (Shift+F10). Android Studio builds the debug APK, installs it and opens it.

**Or build the APK file and install it yourself**
- Menu: **Build → Generate App Bundles or APKs → Generate APKs**. Click "locate" in the popup.
- Or in Android Studio's Terminal: `./gradlew assembleDebug` (on Windows, `gradlew assembleDebug`)
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

**No cable?** Developer options → **Wireless debugging** → on. Then in Android Studio go to Device Manager → **Pair Devices Using Wi-Fi**, and on the phone tap **Pair device with QR code**.

**Test checklist**
- [ ] Pick an APK → the preview shows the right name, icon and version
- [ ] Tap Install → you're sent to "Install unknown apps" the first time → come back → tap Install again → the system dialog appears → installs
- [ ] Cancel the system dialog → the error log shows ABORTED
- [ ] Fold and unfold while on the preview → nothing is lost
- [ ] Error log → Copy → paste somewhere to confirm it copied

If the build fails, copy the first red error from the **Build** tab and send it to me. If an install fails, use the error log's Copy button and send that.

---

## 4. Standalone HTML preview

Open `preview/apk-preview.html` in Chrome on a PC or on the Fold. It runs offline and nothing is uploaded. Choose a real `.apk` and the page reads it right in the browser: name, icon (adaptive icons included), version, SDKs, permissions, v1/v2/v3 signing certs with SHA-256, and the file SHA-256. Its error log has the same Copy button. **Show sample** fills the page with demo data. The Install button is disabled on purpose, because installing only happens in the Android app.
