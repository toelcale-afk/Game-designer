# PackFlow Studio: plan status

Legend: ✅ done · 🟡 partly done · ⏭ next / not started

| Phase | Status | Notes |
|---|---|---|
| 0 Baseline | ✅ | Export imported, dead code removed, CI workflow added |
| 1 Pipeline tabs | ✅ | Preview → Icon → Compile & Check → Install, then Bundler/Inspector/Error Log |
| 2 Error catcher | ✅ | JS + Android catcher injected into every generated APK; standalone copy in reusable/error-catcher |
| 3 Icon maker | ✅ | Crop/zoom/background, 5 shape previews, all densities + adaptive icon |
| 4 Offline quick build | ✅ | Shell module + on-device manifest rewrite + apksig signing; verified on CI with aapt2/apksigner |
| 5 Home + Projects | ✅ | Home with 4 entries, project file browser, Send to PackFlow, builds saved per project |
| 6 Asset Generator | ✅ | Imperium suite as Sprites/Tiles (blue drift fixed), Levels, sfxr Sound, Lyria Music, Gemini + Android Voice |
| 7 Game Designer | ✅ | Units, campaign/missions, menus/HUD, sound hookups, saves; JSON in code/data/ |
| 8 Engine + templates | ✅ | HTML5 canvas engine + grid-tactics template (2 missions), real-time hook |
| + Sound library & voice changer | ✅ | 464 built-in sounds; record or pick a clip, 17 voice presets |

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

## Phase 3: Icon maker

**Done**
- Step **2 Icon**: pick an image from the phone (photo picker) or from the current project
  (list of project images, wired up in Phase 5). Drag to move, pinch or slider to zoom, reset.
- Background: 8 preset colors, hex field, or a background image (cover-fit).
- Live preview in the selected shape plus small previews of circle, squircle, rounded,
  square and teardrop launchers.
- "Generate" renders: `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` (rounded square) and
  `ic_launcher_round.png` (48 dp), adaptive `ic_launcher_foreground.png` /
  `ic_launcher_background.png` (108 dp, image inside the 72 dp safe area),
  `mipmap-anydpi-v26/ic_launcher(_round).xml`, and a 512 px `icon/ic_launcher-playstore.png`.
  All go into the generated project and the manifest gets `android:icon`/`roundIcon`.
- The design (`icon.json`) and source images are saved, so it survives restarts.
- Tests: `IconSpecTest` (sizes, fit math, safe zone, colors, PNG header, JSON round trip);
  `IconRendererTest` (Robolectric native graphics: every file has the right size, round icon
  corners are transparent) runs on CI only.

## Phase 4: Offline quick build

**Done**
- `shell/`: a framework-only WebView app (Java, ~no dependencies, R8-minified). It serves
  `assets/www/` from `https://appassets.androidplatform.net/assets/...` (so `fetch`, modules
  and `localStorage` work), reads `assets/packflow_shell.json` (full screen, orientation),
  handles back (incl. Android 13+ back callback), and has the Phase 2 crash catcher + JS bridge.
  Its manifest uses literal placeholders: package `com.packflow.shell.placeholder`, label
  `PackFlowShellAppName`, version `0.0.0-packflow`. Icon is PNG-only.
- CI builds `:shell:assembleRelease` (unsigned) and copies it to
  `app/src/main/assets/shell/webshell.apk` before building PackFlow (git-ignored).
- On the phone (Compile & Check → "Quick build (offline)", HTML/JS apps only):
  1. copy the shell, 2. rewrite the binary manifest (`core/Axml.kt`: full string-pool
  re-encode, so any name length works; package, label, versionName, versionCode),
  3. drop the old signature, put the game into `assets/www/`, swap launcher PNGs by size,
  4. write a zip-aligned APK (`core/ShellApk.kt`), 5. sign v2+v3 with apksig using this
  phone's PackFlow key, then "Next: 4 Install".
- Signing key: RSA 2048 generated on first use; self-signed cert built with a tiny DER encoder
  (`core/SelfSignedCert.kt`); private key stored in `filesDir/quickbuild/` encrypted with an
  Android Keystore AES key. The fingerprint is shown in the card.
- Generated-project workflow artifact: `retention-days: 1` (done in Phase 1).
- Tests: `AxmlTest` (UTF-8/UTF-16 pools, styles, long strings, versionCode), `ShellApkTest`
  (manifest/web/icons/signature removal, 4-byte alignment verified by reading local headers,
  validation, path traversal), `SelfSignedCertTest`. `QuickBuildShellTest` patches the real CI
  shell, signs it and checks it with apksig's `ApkVerifier`; the workflow then runs
  `aapt2 dump badging` and `apksigner verify` on the result.

**Kotlin apps** still use the GitHub build (the quick build card only shows for HTML/JS/React).

## Phase 5: Home screen + Projects

**Done**
- Launch screen with four big entries: **Asset Generator**, **Game Designer**, **PackFlow**,
  **Projects** (two columns on wide screens/unfolded). Shows the current project.
- **Projects** (built-in file browser, `filesDir/projects/<slug>/`): each game gets `code/`,
  `sprites/`, `tiles/`, `levels/`, `sounds/`, `music/`, `voice/`, `icon/`, `builds/` and a
  `project.json` (name, app name, package, version).
  - New project (name + template), import a project zip (zip-slip safe, 500 MB cap),
    rename, export zip, delete (with confirmation).
  - Inside a project: browse folders, tap to preview images (crisp pixel scaling), play
    audio, view/edit code and text files; per-file Rename / Move (folder picker) / Delete;
    Import here (multi-select from phone storage); New folder; Export zip.
  - **Send to PackFlow** loads the whole project into pipeline step 1.
- **Project mode in PackFlow**: the Preview WebView serves the project's real files (code/
  is the web root, `sprites/…`, `levels/…`, `sounds/…` keep their folder names) with the
  error catcher injected into `index.html`. Diagnostics run on `code/`. The icon maker uses
  the project's `icon/` folder and can pick images from the project. Quick build, GitHub
  build and project ZIP all ship the project's web root. Every APK/ZIP built is also copied
  into the project's `builds/`.
- The current project is remembered and shared by all sections; PackFlow reloads it when
  you enter it after switching projects.
- Generated WebView apps (GitHub path) now serve assets from
  `https://appassets.androidplatform.net/assets/` like the quick-build shell, so `fetch()`
  of level/unit JSON works in both build paths.
- Tests: `ProjectLayoutTest` (web mapping both ways, unsafe paths, names, meta, templates),
  `ProjectRepositoryTest` (Robolectric, CI): folders, rename/move/delete, refusing paths
  outside projects, zip export without builds/.

## Phase 6: Asset Generator

Tabs: **Sprites, Tiles, Levels, Sound, Music, Voice**. Everything saves into the current project.

**Done**
- **Sprites / Tiles**: `imported/imperium/` exists, so the Imperium suite is used, renamed
  "Asset Generator". `tools/build_asset_suite.py` builds
  `app/src/main/assets/asset-suite/index.html` from the untouched original (re-run it after
  updating the suite). It opens on the generation tab (Sprites) or the tile slicer (Tiles)
  in one WebView, with file pickers working.
  - Gemini key: read from/written to Android (Keystore-encrypted) through
    `window.PackFlowKeys`; never stored in localStorage, never baked into a file ("Save key
    into a copy" is hidden). Links to other sites open in the browser, not in the key page.
  - Saves also write into the project: `sprites/<faction>/<subfaction>/<asset>/…`,
    tile atlases to `tiles/<faction>/`, downloads to `sprites/downloads/`, zips to `builds/`.
  - Image models updated (`gemini-3.1-flash-image` is deprecated): Nano Banana 2.1 /
    Gemini 3 Pro Image, still editable.
  - **Blue drift fix** (three causes, see the script header): (1) each sprite's neutral tint
    (Lab a*/b* of low-chroma pixels) is pulled to the tone anchor's tint, or to the palette's
    neutrals, so a slight blue cast can't compound from one generation to the next;
    saturated colors (team colors, gold) aren't touched; checkbox "Neutral colors (fix blue
    tint)", on by default. (2) Palette snap now weighs hue mismatch, so skin/warm colors stop
    snapping to the steel-blue entries. (3) Tone match no longer clips single channels.
    Verified in headless Chromium: a blue-tinted gray moves to the palette's tint, gold and
    saturated blue stay exact, no page errors.
- **Levels**: tile-grid builder. Tools Paint / Fill / Unit / Object / Spawn / Erase, drag to
  paint, Move/zoom mode (pinch), teams (Player, Enemy, Neutral, Ally), unit picker reads
  the Game Designer's `code/data/units.json`, tiles can use images from `tiles/`, resize.
  Saved as `levels/<name>.json` (`format: packflow-level`).
- **Sound**: offline sfxr-style generator (Kotlin port of the public-domain sfxr): Laser,
  Explosion, Hit, Pickup, Footstep, Jump, Power-up, Blip, Random, plus Mutate and 14
  sliders. Saved as 16-bit 44.1 kHz WAV in `sounds/` (settings kept in `sounds/.sfx/`).
- **Music**: Lyria through `generateContent` with the user's key (MP3 + lyrics/structure
  text saved to `music/`). Model ID editable (default `lyria-3-clip-preview`). Always offers
  **Import a track made in the Gemini app**. See QUESTIONS.md #18 about the free tier.
- **Voice**: Android's built-in offline TextToSpeech (voice, pitch, speed, preview, saved as
  WAV) or Gemini TTS (voice, "how to say it", model ID, PCM wrapped as WAV). Saved in `voice/`.
- Tests: `SfxrTest` (all presets audible and bounded, deterministic, footsteps short, JSON,
  WAV header), `LevelTest` (paint/fill/resize/units/spawns/JSON), `GeminiApiTest`
  (TTS body shape, PCM→WAV, MP3 + lyrics, errors, model IDs).

## Phase 7: Game Designer

**Done** (tabs: Units, Campaign, Menus & HUD, Sounds, Saves; Save button with unsaved marker)
- **Units**: factions (name + color; Space Marines, Orks, Tyranids preset) and units with
  name, ID, faction, sprite (picked from the project's sprites/tiles with a preview),
  health, movement, attack, range, defense, cost.
- **Campaign**: campaign name; missions with name, briefing text, level, player/enemy
  faction, win condition (destroy all, survive N turns, reach tile x,y, kill a unit type),
  lose condition (all units lost, turn limit, unit type lost) and the next mission. New
  missions are linked to the end of the chain; deleting one relinks the chain; ▶ marks the
  start mission.
- **Menus & HUD**: title, subtitle, background image, accent color, Continue/Settings/Credits
  buttons, credits text; HUD turn counter, unit panel, end-turn button, objective, top or
  bottom, size.
- **Sounds**: each game action (select, move, attack, hit, death, turn start, victory, defeat,
  menu button, menu music, battle music) → a clip from sounds/, voice/ or music/, with play.
- **Saves**: on/off, 1-10 slots, autosave each turn, storage key.
- Saved as `code/data/units.json`, `campaign.json`, `ui.json`, `sounds.json`, `save.json`,
  plus `game-data.js` (`window.GAME_DATA`, including the missions' levels) so the engine also
  runs where `fetch()` of local files isn't allowed.
- A "things to check" card lists validation problems (duplicate IDs, missing faction/sprite/
  level files, bad win values, a mission chain that loops).
- The Levels editor's unit picker uses these unit IDs.
- Tests: `GameDesignTest` (round trip through the files, `game-data.js` is valid JSON,
  chain order, validation finds each kind of problem, IDs).

## Phase 8: 2D engine + templates

**Done**
- `engine/packflow-engine.js` (canonical copy; embedded into projects): canvas renderer with
  DPR scaling, scenes (menu, settings, credits, briefing, battle, pause, result), canvas
  buttons, tap/drag input (drag pans big maps), sound hookups (effects + looping menu/battle
  music, unlocked on first tap), image cache for sprites/tiles/backgrounds, save slots +
  autosave + Continue (localStorage), campaign flow (next mission on victory, retry on
  defeat). Loads `data/*.json` and `levels/*.json` with `fetch`, falling back to
  `window.GAME_DATA` from `data/game-data.js`.
- `engine/tactics.js` registers the `turnbased` mode: tap a unit → movement range (terrain
  costs, impassable tiles, blocking objects, enemies block, allies pass through) and targets
  in range; move with animation; attack (attack − defense − tile cover ± 1, minimum 1);
  floating damage; HP bars; End turn → enemy AI (attacks the weakest target in range,
  otherwise advances to the best cell toward the nearest foe, then attacks); win/lose from
  the mission (destroy all, survive N turns, reach tile, kill unit type / all lost, turn
  limit, unit lost). Teams with spawn points but no placed units get their faction's units.
  Units draw their sprite if set, otherwise a faction-colored token.
- **Real-time hook:** missions carry `"mode"` (default `turnbased`); modes are registered with
  `PF.registerMode(name, factory)` and get the same engine/battle objects. A placeholder
  `realtime` mode shows a "not built yet" banner.
- **Template "Grid tactics (turn-based)"** in Projects → New project: Space Marines vs Orks
  (Landfall: destroy all) then vs Tyranids pouring from spawn points (Hold the Gate: survive
  6 turns), units/menus/saves preset in the Game Designer files, 8 sound effects generated
  with the sfxr port and hooked up, README. It's a normal project, so it plays in step 1
  Preview and builds with the quick build and the GitHub build.
- `tools/gen_kotlin_assets.py` regenerates the embedded copies of the engine and error
  catcher; tests fail if they drift.
- Verified in headless Chromium: menu → briefing → battle → select/move → enemy turn (AI
  moved, turn 2, autosave) → victory → mission 2 with 8 spawned Tyranids → defeat →
  reload + Continue from slot 1. No page errors. `TacticsTemplateTest`: engine copies match,
  the design validates against its own files, every file the game loads is shipped,
  levels sane.

## Extra: sound library + voice changer (after the 8 phases)

Asset Generator now has 8 tabs: Sprites, Tiles, Levels, Sound, **Library**, Music, Voice, **Voice FX**.

- **Library**: a built-in database of 464 retro sounds in 8 categories (Weapons, Explosions,
  Impacts, UI, Pickups, Movement, Creatures, Sci-fi). They're sfxr recipes with fixed seeds
  (`core/SoundLibrary.kt`), made on the phone when played, so the APK doesn't grow and they're
  free to use. Search by name/tag, filter by category, ★ favorites (saved on the phone),
  play, **Add** to the project's `sounds/`, "Add all" favorites, or **Tweak** to open one in
  the Sound tab's sliders.
- **Voice FX** (voice changer, offline): **Record** with the mic (permission asked on first
  tap; level meter; trims silence; up to 60 s), pick any clip in voice/, sounds/ or music/, or
  import a file (WAV directly; MP3/M4A/OGG decoded with MediaCodec). 17 presets (Deep, Giant,
  Monster, Demon, Gruff warrior, Armored helmet vox, Radio, Robot, Machine servant, Alien,
  Chipmunk, Ghost, Cathedral, Canyon echo, Slow motion, Reverse) plus sliders for pitch
  (keeps timing), speed, robot (ring mod + tone), radio/vox, distortion, bitcrush, wobble,
  echo, reverb, demon double and reverse. Preview, then save to `voice/` as WAV.
- DSP is pure Kotlin (`core/VoiceFx.kt`): two-head crossfaded pitch shifter, resampler,
  RBJ biquads, ring mod, Schroeder reverb, echo, normalization; plus a WAV reader for
  8/16/24/32-bit and float files.
- Tests: `SoundLibraryTest` (≥400 unique sounds, every one audible and 0.01-6 s long,
  deterministic, search), `VoiceFxTest` (±12 semitones lands within tolerance of the right
  pitch and keeps length, speed changes length, every preset is clean and normalized,
  reverb/echo tails, WAV formats).
- New permission: `RECORD_AUDIO` (only used when you tap Record).

## How this was verified

- **CI is green** (`build-packflow.yml` run #2): the shell builds, all unit tests pass
  (including Robolectric `IconRendererTest`, `ProjectRepositoryTest`, and
  `QuickBuildShellTest`, which patches the real shell, signs it and verifies the signature
  with apksig), `aapt2 dump badging` and `apksigner verify` accept a quick-built APK, and the
  debug APK is uploaded as `packflow-studio-debug-apk` (kept 3 days).
- Run #1 also created and committed `keystore/debug.keystore`.
- CI runs on JDK 21: Robolectric's Android 16 (API 36) sandbox needs it.
- Earlier, in the session (no Google Maven there): JVM unit tests, a full type-check of the app
  against Compose desktop + `android-all`, and the engine/error catcher/asset suite in
  headless Chromium.
- **Not tested yet:** anything on a real phone.

## Next

1. Install the debug APK from the latest run's artifacts on your phone.
2. On a phone: create a "Grid tactics" project → Send to PackFlow → Preview → Icon →
   Quick build → Install. Then try the GitHub build with a token.
3. Check the Gemini model IDs (QUESTIONS.md #19) and try Music/Voice with your key.
4. Ideas not done: real-time mode, Game Designer UI for picking a mission's mode, export/
   backup of the quick-build signing key, sprite animation playback in the engine
   (the Imperium suite already exports animation sheets + JSON).
