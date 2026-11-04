package com.example.yourapp

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PackFlow error catcher, Android side (framework APIs only, no dependencies).
 *
 * - Saves any uncaught exception to filesDir/packflow_errors/last_crash.txt.
 * - Web errors from error-catcher.js arrive through [PackFlowErrorBridge] and go to the
 *   same log (filesDir/packflow_errors/error_log.txt).
 * - On the next launch, the first screen shows "Copy last crash".
 *
 * Setup: register PackFlowApplication in the manifest (android:name=".PackFlowApplication"),
 * or call PackFlowCrashCatcher.install(this) from your own Application.onCreate().
 * For WebViews: webView.addJavascriptInterface(PackFlowErrorBridge(context), "PackFlowAndroid").
 */
object PackFlowCrashCatcher {
    private const val DIR = "packflow_errors"
    private const val CRASH_FILE = "last_crash.txt"
    private const val LOG_FILE = "error_log.txt"
    private const val MAX_LOG_BYTES = 64 * 1024

    @Volatile
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(app, thread, error)
            } catch (ignored: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var offered = false
            override fun onActivityResumed(activity: Activity) {
                if (!offered) {
                    offered = true
                    offerLastCrash(activity)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun header(context: Context): String {
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "?"
        } catch (e: Throwable) {
            "?"
        }
        return context.packageName + " " + version + " | Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + ") | " + Build.MANUFACTURER + " " + Build.MODEL
    }

    fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        val text = "CRASH " + stamp() + " on thread '" + thread.name + "'\n" +
            header(context) + "\n\n" + trace.toString()
        File(dir(context), CRASH_FILE).writeText(text)
        appendLog(context, "CRASH", error.toString())
    }

    @Synchronized
    fun appendLog(context: Context, kind: String, message: String) {
        val f = File(dir(context), LOG_FILE)
        if (f.exists() && f.length() > MAX_LOG_BYTES) {
            val keep = f.readText().takeLast(MAX_LOG_BYTES / 2)
            f.writeText(keep)
        }
        f.appendText(stamp() + " " + kind + " " + message.replace('\n', ' ') + "\n")
    }

    fun lastCrash(context: Context): String? {
        val f = File(dir(context), CRASH_FILE)
        return if (f.exists()) f.readText() else null
    }

    fun recentLog(context: Context, lines: Int = 30): String {
        val f = File(dir(context), LOG_FILE)
        return if (f.exists()) f.readLines().takeLast(lines).joinToString("\n") else ""
    }

    fun clearLastCrash(context: Context) {
        File(dir(context), CRASH_FILE).delete()
    }

    fun clearLog(context: Context) {
        File(dir(context), LOG_FILE).delete()
    }

    /** Full text for "Copy last crash": the crash plus the most recent web/app errors. */
    fun reportText(context: Context): String {
        val crash = lastCrash(context) ?: "No crash recorded."
        val log = recentLog(context)
        return if (log.isBlank()) crash else crash + "\n\nRecent errors:\n" + log
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        Handler(Looper.getMainLooper()).post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    fun offerLastCrash(activity: Activity) {
        val crash = lastCrash(activity) ?: return
        val preview = crash.lines().take(8).joinToString("\n")
        try {
            AlertDialog.Builder(activity)
                .setTitle("The app closed unexpectedly last time")
                .setMessage(preview)
                .setPositiveButton("Copy last crash") { _, _ ->
                    copyToClipboard(activity.applicationContext, "Last crash", reportText(activity))
                    clearLastCrash(activity)
                }
                .setNegativeButton("Dismiss") { _, _ -> clearLastCrash(activity) }
                .show()
        } catch (ignored: Throwable) {
        }
    }
}

/** Register this as android:name in <application> to switch the catcher on. */
class PackFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PackFlowCrashCatcher.install(this)
    }
}

/** JavaScript bridge used by error-catcher.js (exposed as window.PackFlowAndroid). */
class PackFlowErrorBridge(context: Context) {
    private val appContext = context.applicationContext

    @JavascriptInterface
    fun logError(json: String) {
        PackFlowCrashCatcher.appendLog(appContext, "WEB", json)
    }

    @JavascriptInterface
    fun copyText(text: String) {
        PackFlowCrashCatcher.copyToClipboard(appContext, "Errors", text)
    }

    @JavascriptInterface
    fun clearErrors() {
        PackFlowCrashCatcher.clearLog(appContext)
    }

    @JavascriptInterface
    fun lastCrash(): String = PackFlowCrashCatcher.lastCrash(appContext) ?: ""
}
