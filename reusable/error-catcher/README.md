# PackFlow error catcher

A small, dependency-free error catcher for HTML5 games/apps and Android apps.
PackFlow Studio injects it into every APK project it generates. You can also drop it into any
project by hand.

| File | What it does |
|---|---|
| `error-catcher.js` | Catches `window` errors (including failed script/image loads), unhandled promise rejections and `console.error`. Saves the last 50 (message, file, line, column, stack, time) in `localStorage` and shows a floating **Copy errors (n)** button when there are any. Tap to copy, long-press to clear. |
| `android/PackFlowCrashCatcher.kt` | Uncaught-exception handler that saves a crash file, a JS bridge so web errors go to the same log, and a **Copy last crash** dialog on the next launch. Framework APIs only. |

## HTML / JavaScript

Put the script **first** in `<head>` so it sees errors from everything loaded after it:

```html
<head>
  <script src="error-catcher.js"></script>
  <!-- your other scripts -->
</head>
```

Or paste the file's contents into an inline `<script>` (that's what PackFlow does).

From your code:

```js
PackFlowErrors.report(new Error('Level 3 failed to load'));  // add your own entry
PackFlowErrors.list();   // array of {type, message, file, line, column, stack, time, page}
PackFlowErrors.text();   // the same as readable text
PackFlowErrors.copy();   // copy to clipboard (returns a Promise)
PackFlowErrors.clear();
```

Entries are stored under the `localStorage` key `__packflow_errors`, so errors from a session
that froze or reloaded are still there next time.

## Android (Kotlin)

1. Copy `android/PackFlowCrashCatcher.kt` into your app and change the first line to your
   package (`package com.yourname.yourgame`).
2. Turn it on, either:
   - in `AndroidManifest.xml`: `<application android:name=".PackFlowApplication" ...>`, or
   - from your own `Application.onCreate()`: `PackFlowCrashCatcher.install(this)`.
3. For a WebView, expose the bridge **before** loading the page:

   ```kotlin
   webView.settings.javaScriptEnabled = true
   webView.addJavascriptInterface(PackFlowErrorBridge(this), "PackFlowAndroid")
   webView.loadUrl("file:///android_asset/index.html")
   ```

   `error-catcher.js` detects `window.PackFlowAndroid` and sends each error to the app log, and
   "Copy errors" uses the Android clipboard.

Files (in the app's private storage, never uploaded anywhere):

- `filesDir/packflow_errors/last_crash.txt`: the last uncaught exception with device info.
- `filesDir/packflow_errors/error_log.txt`: crashes and web errors, newest last (trimmed at 64 KB).

On the next launch the first screen shows **The app closed unexpectedly last time** with
**Copy last crash** (crash + recent errors) and **Dismiss**. Both clear the crash file.

To show the crash somewhere else (a settings screen, for example), call
`PackFlowCrashCatcher.reportText(context)`.

## Notes

- No network access, no analytics. Nothing leaves the device unless the user copies it.
- The catcher wraps `console.error` and still calls the original, so DevTools and Android's
  WebView console logging keep working.
- PackFlow keeps an embedded copy of both files in
  `app/src/main/java/com/example/core/ErrorCatcherAssets.kt`; a unit test fails if they drift.
