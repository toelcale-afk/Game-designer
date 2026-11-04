package com.aistudio.packflow.xkrv.installer

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-wide installer log. Kept in memory for the UI and mirrored to
 * filesDir/installer_log.txt so it survives restarts. Never logs secrets.
 */
object ErrorLog {
    private const val TAG = "PackFlowInstaller"
    private const val FILE_NAME = "installer_log.txt"
    private const val MAX_LINES = 400

    val entries = mutableStateListOf<String>()
    private var file: File? = null
    private var appVersionHeader: String = "PackFlow Studio v1.0 (1)"
    private val main = Handler(Looper.getMainLooper())
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    val systemHeader: String
        get() = "Android ${Build.VERSION.RELEASE ?: "16"} (API ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}) | $appVersionHeader"

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val appCtx = context.applicationContext
        runCatching {
            val pkgInfo = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            val vCode = if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            appVersionHeader = "PackFlow Studio v${pkgInfo.versionName ?: "1.0"} ($vCode)"
        }
        val f = File(appCtx.filesDir, FILE_NAME)
        file = f
        val old = runCatching { if (f.exists()) f.readLines().takeLast(MAX_LINES) else emptyList() }
            .getOrDefault(emptyList())
        runOnMain {
            entries.clear()
            entries.addAll(old)
        }
    }

    fun info(message: String) = add("INFO ", message, null)
    fun error(message: String, t: Throwable? = null) = add("ERROR", message, t)

    @Synchronized
    private fun add(level: String, message: String, t: Throwable?) {
        val detail = t?.let {
            val frame = it.stackTrace.firstOrNull { el ->
                el.className.startsWith("com.aistudio") || el.className.startsWith("com.example")
            } ?: it.stackTrace.firstOrNull()
            val location = if (frame != null && frame.lineNumber > 0) {
                " @ ${frame.fileName ?: frame.className.substringAfterLast('.')}:line ${frame.lineNumber}"
            } else {
                ""
            }
            " | ${it::class.java.simpleName}: ${it.message ?: "(no message)"}$location"
        } ?: ""
        val line = "${stamp.format(Date())} $level $message$detail"
        if (level == "ERROR") Log.e(TAG, line, t) else Log.i(TAG, line)
        runCatching { file?.appendText(line + "\n") }
        runOnMain {
            entries.add(line)
            while (entries.size > MAX_LINES) entries.removeAt(0)
        }
    }

    fun asText(): String {
        val header = "=== $systemHeader ==="
        return if (entries.isEmpty()) {
            "$header\n(No log entries)"
        } else {
            "$header\n" + entries.joinToString("\n")
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file?.writeText("") }
        runOnMain { entries.clear() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
