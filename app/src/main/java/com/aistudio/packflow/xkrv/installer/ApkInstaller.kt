package com.aistudio.packflow.xkrv.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class InstallState {
    object Idle : InstallState()
    object Writing : InstallState()
    object WaitingForUser : InstallState()
    data class Success(val packageName: String?, val statusCode: Int = PackageInstaller.STATUS_SUCCESS) : InstallState()
    data class Failed(val reason: String, val statusCode: Int = PackageInstaller.STATUS_FAILURE) : InstallState()
}

object InstallEvents {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state
    fun set(s: InstallState) { _state.value = s }
}

/** Installs through the system PackageInstaller, so Android shows its own confirm dialog. */
object ApkInstaller {
    const val ACTION_STATUS = "com.aistudio.packflow.xkrv.INSTALL_STATUS"

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        if (!apk.exists()) {
            val msg = "Cached APK file no longer exists on disk. Choose or load the APK again."
            ErrorLog.error(msg)
            InstallEvents.set(InstallState.Failed(msg))
            return@withContext
        }
        InstallEvents.set(InstallState.Writing)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setSize(apk.length()) }
        val sessionId = installer.createSession(params)
        ErrorLog.info("Install session $sessionId created for ${apk.name} (${apk.length()} bytes)")
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            ErrorLog.info("Session $sessionId committed, waiting for system installer")
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            ApkInspector.deleteCachedCandidates(context)
            ErrorLog.error("Install session $sessionId failed before commit", t)
            InstallEvents.set(InstallState.Failed(t.message ?: t::class.java.simpleName))
        }
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ErrorLog.init(context)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    ApkInspector.deleteCachedCandidates(context)
                    ErrorLog.error("System asked for confirmation but sent no dialog intent")
                    InstallEvents.set(InstallState.Failed("No confirmation dialog from system (code $status)", status))
                    return
                }
                InstallEvents.set(InstallState.WaitingForUser)
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (t: Throwable) {
                    ApkInspector.deleteCachedCandidates(context)
                    ErrorLog.error("Could not open the system install dialog", t)
                    InstallEvents.set(InstallState.Failed("Could not open the system install dialog (code $status)", status))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                ApkInspector.deleteCachedCandidates(context)
                ErrorLog.info("Installed ${pkg ?: "package"} (status code $status: STATUS_SUCCESS)")
                InstallEvents.set(InstallState.Success(pkg, status))
            }
            else -> {
                ApkInspector.deleteCachedCandidates(context)
                val reason = "${statusName(status)} (code $status): ${message ?: "no details"}. ${hint(status)}".trim()
                ErrorLog.error("Install failed for ${pkg ?: "package"} | $reason")
                InstallEvents.set(InstallState.Failed(reason, status))
            }
        }
    }

    private fun statusName(s: Int) = when (s) {
        PackageInstaller.STATUS_FAILURE -> "FAILURE"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "ABORTED"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "BLOCKED"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "CONFLICT"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "INCOMPATIBLE"
        PackageInstaller.STATUS_FAILURE_INVALID -> "INVALID"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "STORAGE"
        else -> "STATUS_$s"
    }

    private fun hint(s: Int) = when (s) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Blocked by the device. On Samsung, check Settings > Security and privacy > Auto Blocker."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "An installed app with the same package has a different signing key or a higher version. Uninstall it first."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Built for a different CPU or a newer Android version."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The APK is corrupt or unsigned."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage."
        else -> ""
    }
}
