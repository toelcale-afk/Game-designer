# PackFlow Studio

An Android app (Kotlin + Jetpack Compose) for making 2D HTML5 games on your phone and
turning them into installable APKs. Exported from Google AI Studio
(`applicationId com.aistudio.packflow.xkrv`).

| Section | What it does |
|---|---|
| **Asset Generator** | Sprites and Tiles (the Imperium suite, Gemini image models), tile-grid **Levels**, offline retro **Sound** effects (WAV), **Music** (Lyria, or import from the Gemini app), **Voice** (Android offline TTS or Gemini TTS) |
| **Game Designer** | Units, factions, campaign/missions, menus/HUD, sound hookups, save settings (JSON in `code/data/`) |
| **PackFlow** | 1 Preview → 2 Icon → 3 Compile & Check (diagnostics, project ZIP, offline quick build, GitHub build) → 4 Install, plus Bundler, Inspector, Error Log |
| **Projects** | File browser for each game: preview/edit, import/export, Send to PackFlow |

API keys and tokens are typed into the app and encrypted with the Android Keystore
(`data/GitHubSettings.kt`, `data/GeminiSettings.kt`). Nothing secret is stored in the repo.

## Repository layout

```
app/                     PackFlow Studio (com.example.* sources; installer in com.aistudio...)
  src/main/java/com/example/core/   pure Kotlin (unit-tested on the JVM)
  src/main/assets/asset-suite/      built Asset Generator page (tools/build_asset_suite.py)
shell/                   prebuilt WebView shell for the offline quick build (CI builds it)
engine/                  PackFlow 2D HTML5 engine (canonical copy) + tactics mode
reusable/error-catcher/  drop-in error catcher for any HTML or Android app
imported/imperium/       original Imperium asset suite (unmodified)
tools/                   build_asset_suite.py, gen_kotlin_assets.py
.github/workflows/       build-packflow.yml (tests + debug APK, retention 3 days)
PLAN_STATUS.md           what's done / next
QUESTIONS.md             decisions that need your review
```

## Building

CI (`.github/workflows/build-packflow.yml`) builds the shell, runs unit tests, checks a
quick-built APK with `aapt2` and `apksigner`, builds the debug APK and uploads it.

Locally: copy `keystore/debug.keystore` (created by the first CI run) to the project root,
then build the shell first so the offline quick build is included:

```
gradle :shell:assembleRelease
mkdir -p app/src/main/assets/shell
cp shell/build/outputs/apk/release/*.apk app/src/main/assets/shell/webshell.apk
gradle testDebugUnitTest assembleDebug
```

After editing `engine/*.js` or `reusable/error-catcher/*`, run
`python3 tools/gen_kotlin_assets.py`. After updating the Imperium suite, run
`python3 tools/build_asset_suite.py`.

## AI Studio

View the original app in AI Studio: https://ai.studio/apps/3e681876-fd88-4f20-9913-40f3051c7a6b.
Don't change `signingConfigs` in `app/build.gradle.kts`; AI Studio needs them.
