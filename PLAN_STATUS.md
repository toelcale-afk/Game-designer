# PackFlow Studio: plan status

Legend: ✅ done · 🟡 partly done · ⏭ next / not started

| Phase | Status | Notes |
|---|---|---|
| 0 Baseline | ✅ | Export imported, dead code removed, CI workflow added |
| 1 Pipeline tabs | ✅ | Preview → Icon → Compile & Check → Install, then Bundler/Inspector/Error Log |
| 2 Error catcher | ✅ | JS + Android catcher injected into every generated APK; standalone copy in reusable/error-catcher |
| 3 Icon maker | ⏭ | |
| 4 Offline quick build | ⏭ | |
| 5 Home + Projects | ⏭ | |
| 6 Asset Generator | ⏭ | |
| 7 Game Designer | ⏭ | |
| 8 Engine + templates | ⏭ | |

## Phase 0: Baseline

**Done**
- Imported the AI Studio export unmodified (first commit), plus the Imperium asset suite under
  `imported/imperium/`.
- `.github/workflows/build-packflow.yml`: runs unit tests and builds the debug APK on every
  push, uploads it as an artifact (`retention-days: 3`). On the first run it creates
  `keystore/debug.keystore` with keytool and commits it, so every build has the same signature.
- Fixed `ExampleRobolectricTest` (it expected "My Application"; the app is "PackFlow Studio").

**Dead code found and removed**
- `ui/InstallApkScreen.kt` (1000 lines, never shown; the Install tab uses
  `com.aistudio.packflow.xkrv.installer.PackFlowInstaller`).
- `ui/BlueprintScreen.kt` and `PackagerEngine.generateBlueprint*` (never shown).
- View-model install-preview simulation state and functions (only used by InstallApkScreen),
  and the startup "load built-in test APK" call that fed it.
- `PackagerEngine.inspectApkFile`, `stageSampleInstalledApk`, `stageInvalidNonApkZipForTest`
  (only used by the above).
- `public/assets/aistudio/*.zip`, `preview/`, `app/src/main/assets/preview.html`,
  `MANIFEST_ADDITIONS.xml`, `CLAUDE_INSTALLER_README.md` (duplicates or already merged).

## Phase 1: Pipeline tabs

**Done**
- A scrollable tab row replaces the bottom bar: `1 Preview`, `2 Icon`, `3 Compile & Check`,
  `4 Install`, then `Bundler`, `Inspector`, `Error Log` (badge with error count). Back goes to
  step 1. Each step has a "Next" button.
- **1 Preview** = the old Claude/ZIP tab (presets, code input, live device frame).
- **3 Compile & Check** = code diagnostics + "Copy error report", "Package project ZIP", and
  the GitHub build panel.
- **GitHub build panel** (new, see QUESTIONS.md #1): owner/repo/branch + token. The token is
  encrypted with an Android Keystore AES-GCM key (`data/GitHubSettings.kt`, `SecretCipher`).
  "Push & build" commits the generated project in one commit (Git Data API, keeps files it
  doesn't send, such as the CI keystore), waits for the Actions run, shows progress, and on
  failure shows and copies the error lines of the failed job log. On success it downloads the
  APK artifact and "Next: 4 Install" opens it in the installer.
- Generated projects now build on GitHub: root `build.gradle.kts`, `gradle.properties`
  (`android.useAndroidX=true`), JDK 17 toolchain, stable debug key (created once by the
  workflow and committed), workflow on every push, APK artifact kept 1 day.
- Unit tests: `JsonTest`, `B64Test`, `GitHubApiTest`, `GeneratedProjectFilesTest`.

## Phase 2: Error catcher

**Done**
- `reusable/error-catcher/error-catcher.js`: window errors (plus failed resource loads),
  unhandled rejections and `console.error`, saved with message/file/line/column/stack/time,
  floating "Copy errors (n)" button. Tested in headless Chromium (all four kinds captured,
  persisted across reload).
- `reusable/error-catcher/android/PackFlowCrashCatcher.kt`: uncaught exception handler that
  writes `last_crash.txt`, `PackFlowErrorBridge` (`window.PackFlowAndroid`) so web errors go
  to the same log, and a "Copy last crash" dialog on the next launch.
- Injected automatically by `ClaudeApkConverter`: the JS goes first in `<head>` of the
  generated `index.html` (and PackFlow's own live preview), the Kotlin file is added to the
  project, `PackFlowApplication` is registered in the manifest and the WebView host exposes
  the bridge. PackFlow's preview forwards web errors into PackFlow's Error Log.
- Kotlin sources now keep their own `package` (the manifest uses the full class name), which
  fixes Compose projects whose package differs from the application ID.
- README with drop-in steps for any HTML or Android app.
- Tests: `ErrorCatcherTest` (embedded copies match the reusable files, injection position,
  idempotence, package rewrite), manifest tests.

**Build environment note:** the cloud session I worked in can't reach Google's Maven repo, so
I couldn't run the Android build locally. Every phase was compiled and tested by the GitHub
Actions workflow, and pure-logic code was also unit-tested locally on the JVM.
