# PackFlow Studio: plan status

Legend: ✅ done · 🟡 partly done · ⏭ next / not started

| Phase | Status | Notes |
|---|---|---|
| 0 Baseline | ✅ | Export imported, dead code removed, CI workflow added |
| 1 Pipeline tabs | ⏭ | |
| 2 Error catcher | ⏭ | |
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

**Build environment note:** the cloud session I worked in can't reach Google's Maven repo, so
I couldn't run the Android build locally. Every phase was compiled and tested by the GitHub
Actions workflow, and pure-logic code was also unit-tested locally on the JVM.
