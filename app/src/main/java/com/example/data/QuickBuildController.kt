package com.example.data

import android.content.Context
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.Density
import com.example.core.IconSpec
import com.example.core.QuickBuildSpec
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickBuildUi(
    val shellBundled: Boolean = false,
    val busy: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val apk: File? = null,
    val signerFingerprint: String? = null
)

/** Runs the offline quick build off the main thread and reports progress. */
class QuickBuildController(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(QuickBuildUi())
    val state: StateFlow<QuickBuildUi> = _state.asStateFlow()

    fun refresh(context: Context) {
        _state.update {
            it.copy(
                shellBundled = QuickBuilder.isShellBundled(context),
                signerFingerprint = SigningKeyStore.currentFingerprint(context)
            )
        }
    }

    fun build(context: Context, spec: QuickBuildSpec) {
        if (_state.value.busy) return
        spec.validationError()?.let { msg -> _state.update { it.copy(error = msg) }; return }
        val app = context.applicationContext
        scope.launch {
            _state.update { it.copy(busy = true, error = null, apk = null, progress = "Starting…") }
            try {
                val apk = withContext(Dispatchers.IO) {
                    QuickBuilder.build(app, spec) { p -> _state.update { s -> s.copy(progress = p) } }
                }
                _state.update {
                    it.copy(
                        busy = false, apk = apk, progress = "Built ${apk.name} (${apk.length() / 1024} KB)",
                        signerFingerprint = SigningKeyStore.currentFingerprint(app)
                    )
                }
            } catch (t: Throwable) {
                ErrorLog.error("Quick build failed", t)
                _state.update { it.copy(busy = false, error = t.message ?: t::class.java.simpleName) }
            }
        }
    }

    companion object {
        /** Square legacy icons from the icon maker, keyed by density. */
        fun legacyIcons(pngs: Map<String, ByteArray>): Map<Density, ByteArray> =
            Density.entries.mapNotNull { d -> pngs[IconSpec.legacyPath(d)]?.let { d to it } }.toMap()
    }
}
