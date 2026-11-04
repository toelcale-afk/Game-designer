package com.example.data

import android.content.Context
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.GitHubApi
import com.example.core.GitHubTarget
import com.example.core.WorkflowRun
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GitHubBuildUi(
    val target: GitHubTarget = GitHubTarget("", "", "main"),
    val hasToken: Boolean = false,
    val busy: Boolean = false,
    val progress: String? = null,
    val commitSha: String? = null,
    val run: WorkflowRun? = null,
    val error: String? = null,
    val apkFile: File? = null,
    val logExcerpt: String? = null
)

/**
 * Pushes a generated project to the user's GitHub repo, waits for the Actions build,
 * downloads the APK artifact and hands it to the Install step.
 */
class GitHubBuildController(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(GitHubBuildUi())
    val state: StateFlow<GitHubBuildUi> = _state.asStateFlow()
    private var job: Job? = null

    fun load(context: Context) {
        val s = GitHubSettings(context)
        _state.update { it.copy(target = s.target, hasToken = s.hasToken) }
    }

    fun save(context: Context, target: GitHubTarget, newToken: String?) {
        val s = GitHubSettings(context)
        s.target = target
        if (!newToken.isNullOrBlank()) s.saveToken(newToken)
        _state.update { it.copy(target = s.target, hasToken = s.hasToken, error = null, progress = "Settings saved") }
    }

    fun clearToken(context: Context) {
        GitHubSettings(context).clearToken()
        _state.update { it.copy(hasToken = false, progress = "Token removed from this phone") }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(busy = false, progress = "Stopped watching the build (it keeps running on GitHub)") }
    }

    /** files: path -> bytes of the generated project. */
    fun pushAndBuild(context: Context, appName: String, files: List<Pair<String, ByteArray>>) {
        if (_state.value.busy) return
        val settings = GitHubSettings(context)
        val target = settings.target
        target.validationError()?.let { msg -> _state.update { it.copy(error = msg) }; return }
        val token = settings.token()
        if (token.isNullOrBlank()) {
            _state.update { it.copy(error = "Add a GitHub token first (it's stored encrypted on this phone).") }
            return
        }
        val appCtx = context.applicationContext
        job = scope.launch {
            _state.update { GitHubBuildUi(target = target, hasToken = true, busy = true, progress = "Connecting to GitHub…") }
            try {
                val client = GitHubBuildClient(target, token)
                val sha = withContext(Dispatchers.IO) {
                    client.pushFiles(files, "PackFlow build: $appName") { p -> _state.update { it.copy(progress = p) } }
                }
                ErrorLog.info("Pushed $appName to ${target.owner}/${target.repo}@${target.branch} (${sha.take(7)})")
                _state.update { it.copy(commitSha = sha, progress = "Pushed ${sha.take(7)}. Waiting for GitHub Actions to start…") }
                watch(appCtx, client, sha)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                ErrorLog.error("GitHub build failed", t)
                _state.update { it.copy(busy = false, error = t.message ?: t::class.java.simpleName) }
            }
        }
    }

    private suspend fun watch(context: Context, client: GitHubBuildClient, sha: String) {
        var run: WorkflowRun? = null
        // The run usually appears within a few seconds of the push.
        for (attempt in 0 until 24) {
            run = withContext(Dispatchers.IO) { client.runsForCommit(sha) }.firstOrNull()
            if (run != null) break
            delay(5_000)
        }
        if (run == null) {
            _state.update {
                it.copy(busy = false, error = "No GitHub Actions run started for ${sha.take(7)}. Check that Actions is enabled for the repository.")
            }
            return
        }
        val started = System.currentTimeMillis()
        while (!run!!.isFinished && System.currentTimeMillis() - started < 40 * 60_000L) {
            _state.update { it.copy(run = run, progress = run!!.plainStatus) }
            delay(10_000)
            run = withContext(Dispatchers.IO) { client.run(run!!.id) }
        }
        _state.update { it.copy(run = run, progress = run!!.plainStatus) }
        if (!run!!.isFinished) {
            _state.update { it.copy(busy = false, error = "The build is taking longer than 40 minutes. Open it on GitHub to check.") }
            return
        }
        if (!run!!.succeeded) {
            val excerpt = runCatching { withContext(Dispatchers.IO) { client.failedLogExcerpt(run!!.id) } }
                .getOrElse { "Could not read the build log: ${it.message}" }
            ErrorLog.error("GitHub build ${run!!.id} ${run!!.conclusion}: ${excerpt.lines().take(6).joinToString(" | ")}")
            _state.update { it.copy(busy = false, logExcerpt = excerpt, error = "Build ${run!!.conclusion ?: "failed"}. The log excerpt below shows why.") }
            return
        }
        _state.update { it.copy(progress = "Build succeeded. Downloading the APK…") }
        val artifacts = withContext(Dispatchers.IO) { client.artifacts(run!!.id) }
        val artifact = GitHubApi.pickApkArtifact(artifacts)
        if (artifact == null) {
            _state.update { it.copy(busy = false, error = "The build finished but uploaded no APK artifact.") }
            return
        }
        val dir = File(context.cacheDir, "github_builds").apply { mkdirs(); listFiles()?.forEach { f -> f.delete() } }
        val apk = withContext(Dispatchers.IO) { client.downloadApk(artifact, dir) }
        ErrorLog.info("Downloaded ${apk.name} (${apk.length()} bytes) from run ${run!!.id}")
        _state.update { it.copy(busy = false, apkFile = apk, progress = "APK ready: ${apk.name}") }
    }
}
