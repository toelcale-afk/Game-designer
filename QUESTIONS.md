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
