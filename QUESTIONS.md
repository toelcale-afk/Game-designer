# Questions & decisions for review

Each item lists the decision I needed, the default I used, and how to change it.

## Phase 0

1. **The repo was empty; the project came from your uploaded zip.**
   The GitHub repo only had a README. I imported `packflow-studio-nov-4-2026-10-55-50-am.zip`
   unmodified as the first commit, then made changes on top.
   - `app/src/main/java/com/example/data/GitHubSettings.kt` and the "existing GitHub build panel"
     are **not in the zip**. I wrote both from scratch (Phase 1), following the ground rule:
     the token is typed in the app and encrypted with an Android Keystore AES key.
   - **Default used:** build them new. If you have a newer export with those files, send it and
     I'll merge.

2. **Debug keystore / signing config.** `app/build.gradle.kts` reads `${rootDir}/debug.keystore`
   and `.gitignore` ignored `debug.keystore`. The workflow creates `keystore/debug.keystore` once
   (keytool, alias `androiddebugkey`, passwords `android`), commits it, and then **copies it to
   `${rootDir}/debug.keystore` before building**.
   - **Default used:** the signing config is unchanged (AI Studio needs it). The copy step is
     enough, so I didn't repoint the config.
   - `.gitignore` now has `!keystore/debug.keystore` so the shared key can be committed.
   - Local builds: copy `keystore/debug.keystore` to the project root once.

3. **No Gradle wrapper script in the export** (only `gradle-wrapper.properties`). CI uses
   `gradle/actions/setup-gradle` with Gradle 9.3.1, the version the properties file names.
   - **Default used:** no `gradlew` added (AI Studio provides its own).

4. **Removed dead code** (it's still in the first commit if you want anything back):
   `ui/InstallApkScreen.kt`, `ui/BlueprintScreen.kt`, the view-model state and functions only
   they used, `public/assets/aistudio/packflow-installer_*.zip` (an old copy of the installer
   sources already in `app/src/main/java/com/aistudio/...`), `preview/apk-preview.html` and
   `app/src/main/assets/preview.html` (duplicates), and `MANIFEST_ADDITIONS.xml` and
   `CLAUDE_INSTALLER_README.md` (already merged into the manifest).
   - Kept `preview.html` at the root (the AI Studio HTML preview) and
     `app/src/main/assets/apk-preview.html` (opened by the installer).

## Phase 1

5. **Navigation:** seven tabs don't fit a bottom bar, so the pipeline uses a scrollable tab row
   under the title (main steps numbered 1-4, extras unnumbered).
   **Default used:** scrollable tab row.

6. **GitHub token type:** the panel asks for a fine-grained token limited to the build repo
   (Contents RW, Actions R, Workflows RW). Classic tokens with `repo` + `workflow` also work.

7. **Generated project toolchain:** generated apps use AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.11.1
   (a stable, widely cached set), independent of PackFlow's own AGP 9.1.1 build.
   **Default used:** keep them separate so generated builds don't break when AI Studio
   updates PackFlow.

8. **I couldn't push to GitHub from this session** (403 for both git push and the API: the
   Claude GitHub App has read-only access). All work is committed locally on
   `claude/packflow-studio-android-5318tj`. I attached a git bundle of the branch so nothing is
   lost; see the final message. To give write access, install the Claude GitHub App with
   write permission on the repo.

## Phase 4

9. **Package rename approach (you asked me to document it):** I rewrite the compiled binary
   `AndroidManifest.xml` directly. The package, label and version name are strings in its
   string pool; `core/Axml.kt` decodes the whole pool, replaces the placeholder strings and
   re-encodes it (fixing the chunk and file sizes), so new names can be any length.
   Attribute references are indices, which don't change. `versionCode` is patched in place.
   Strings that start with the placeholder package plus a dot are renamed too, in case a
   future shell declares package-based permissions. The shell has no content providers or
   androidx libraries, so no other file contains the package name.
   **Default used:** this approach; it's unit-tested and the CI test checks the real shell
   with `aapt2 dump badging`.

10. **Shell language:** the shell is Java with framework APIs only (no androidx, no Kotlin
    runtime) so the template APK is tiny and has nothing package-specific baked in.
    **Default used:** Java for the shell only; PackFlow itself stays Kotlin/Compose.

11. **Signature schemes:** quick-built APKs are signed with v2 + v3 (minSdk 24 doesn't need
    v1, and apksig's v1 signer uses `java.util.Base64`, which is missing on Android 7).
    **Default used:** v2 + v3.

12. **apksig version:** `com.android.tools.build:apksig:8.7.3` (added to
    `gradle/libs.versions.toml`; it's the only new library). I couldn't check Google Maven
    from this session; if CI can't resolve it, bump to the version matching your AGP.

13. **Signing key lifetime:** one key per phone, kept in PackFlow's private storage (encrypted
    with the Android Keystore). If PackFlow is uninstalled or its data cleared, a new key is
    made, and older quick-built apps must be uninstalled before the new build installs.
    **Default used:** no export/import of the key yet. Tell me if you want a backup option.

14. **React (JSX) artifacts** still load React/Babel from unpkg.com, so they need internet at
    run time even when built offline. Plain HTML/JS games run fully offline.

## Phase 5

15. **Where projects live:** in PackFlow's private storage (`filesDir/projects/`), not shared
    storage. Android 11+ hides other apps' folders, and private storage needs no permissions.
    Moving files in and out is done with Import (phone storage picker) and Export zip.
    **Default used:** private storage + import/export.

16. **Web root layout:** `code/` is the web root of the built game, and asset folders keep
    their names next to it (`sprites/x.png`, `levels/l1.json`). Designer JSON goes in
    `code/data/`. **Default used:** this layout (documented in `core/ProjectLayout.kt`).

17. **Startup:** if a project was open last time, PackFlow opens it in step 1 at launch
    (project mode). "Close project" in step 1 goes back to pasting code.

## Phase 6

18. **Music on the free tier:** I couldn't open Google's docs from this session (ai.google.dev
    was blocked), but search results consistently say the Lyria models
    (`lyria-3-clip-preview`, `lyria-3.5`) have **no free tier** in the Gemini API (about
    $0.04 per 30-second clip). So music generation likely needs billing on your key.
    **Default used:** Lyria generation with `lyria-3-clip-preview`, an editable model ID, and a
    clear note in the UI, plus the free "Import a track made in the Gemini app" path.

19. **Model names (checked via search, Nov 2026):** images `gemini-nano-banana-2.1` /
    `gemini-3-pro-image` (`gemini-3.1-flash-image` deprecated); TTS `gemini-3.8-flash-tts`
    (and `-lite`); music `lyria-3-clip-preview` / `lyria-3.5`. All are editable in the app
    because they change often. Please double-check them against
    https://ai.google.dev/gemini-api/docs/models when you can.

20. **Lyria request format:** I send the documented plain `generateContent` prompt and, if that
    returns no audio, retry once asking for `responseModalities: ["AUDIO","TEXT"]`. I couldn't
    test against the live API. Any error message is shown and logged.

21. **Imperium suite storage:** the suite still keeps its own library (IndexedDB) and Project
    Files tab; inside PackFlow every save is also written into the PackFlow project. I left
    its internal library in place rather than rewriting it.
    **Default used:** both.

22. **Blue drift root cause (my reading of the code):** generated images come back with a
    slight cool cast; the palette snap measured distance mostly by lightness, so warm and
    neutral mid-tones snapped to the steel-blue/blue-gray entries; and the next generation
    used that bluer sprite as its reference, so it compounded. See `tools/build_asset_suite.py`.
