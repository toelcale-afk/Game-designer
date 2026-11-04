package com.example.data

import com.example.core.GitHubApi
import com.example.core.GitHubTarget
import com.example.core.WorkflowArtifact
import com.example.core.WorkflowRun
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class GitHubHttpException(val code: Int, message: String) : IOException(message)

/**
 * Minimal GitHub REST client on HttpURLConnection (no extra dependency).
 * All calls block: run them on Dispatchers.IO. The token only goes in the
 * Authorization header to api.github.com and is never logged.
 */
class GitHubBuildClient(private val target: GitHubTarget, private val token: String) {

    private data class Response(val code: Int, val body: String, val location: String?)

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        auth: Boolean = true,
        followRedirects: Boolean = true
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            try {
                requestMethod = method // Android's built-in HttpURLConnection accepts PATCH
            } catch (_: java.net.ProtocolException) {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", method)
            }
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = followRedirects
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PackFlow-Studio-Android")
            if (auth) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, text, conn.getHeaderField("Location"))
        } finally {
            conn.disconnect()
        }
    }

    private fun ok(r: Response, action: String): String {
        if (r.code !in 200..299) throw GitHubHttpException(r.code, GitHubApi.explainHttp(r.code, r.body, action))
        return r.body
    }

    fun checkAccess(): String {
        val r = request("GET", target.apiBase)
        return GitHubApi.parseDefaultBranch(ok(r, "Opening ${target.owner}/${target.repo}"))
    }

    /** Returns the branch head sha, creating the branch (or the first commit of an empty repo) if needed. */
    private fun ensureBranch(): String {
        val ref = request("GET", "${target.apiBase}/git/ref/heads/${target.branch}")
        if (ref.code == 200) return GitHubApi.parseRefSha(ref.body)
        val repoInfo = request("GET", target.apiBase)
        ok(repoInfo, "Opening ${target.owner}/${target.repo}")
        val defaultBranch = GitHubApi.parseDefaultBranch(repoInfo.body)
        val def = request("GET", "${target.apiBase}/git/ref/heads/$defaultBranch")
        if (def.code == 200) {
            val sha = GitHubApi.parseRefSha(def.body)
            ok(request("POST", "${target.apiBase}/git/refs", GitHubApi.createRefBody(target.branch, sha)), "Creating branch ${target.branch}")
            return sha
        }
        // Empty repository: the Git Data API refuses until there is one commit.
        ok(
            request(
                "PUT", "${target.apiBase}/contents/.packflow",
                GitHubApi.initFileBody(target.branch, "Initialize repository for PackFlow builds", "PackFlow Studio build target\n")
            ),
            "Initializing empty repository"
        )
        val again = request("GET", "${target.apiBase}/git/ref/heads/${target.branch}")
        return GitHubApi.parseRefSha(ok(again, "Reading branch ${target.branch}"))
    }

    /**
     * Commits [files] (path -> bytes) on top of the branch in a single commit, keeping files
     * that aren't sent (for example the debug keystore CI committed). Returns the new commit sha.
     */
    fun pushFiles(files: List<Pair<String, ByteArray>>, message: String, onProgress: (String) -> Unit = {}): String {
        onProgress("Checking branch ${target.branch}…")
        val parent = ensureBranch()
        val parentTree = GitHubApi.parseCommitTreeSha(
            ok(request("GET", "${target.apiBase}/git/commits/$parent"), "Reading last commit")
        )
        val entries = files.mapIndexed { i, (path, bytes) ->
            onProgress("Uploading ${i + 1}/${files.size}: $path")
            val sha = GitHubApi.parseSha(ok(request("POST", "${target.apiBase}/git/blobs", GitHubApi.blobBody(bytes)), "Uploading $path"))
            path to sha
        }
        onProgress("Creating commit…")
        val tree = GitHubApi.parseSha(ok(request("POST", "${target.apiBase}/git/trees", GitHubApi.treeBody(entries, parentTree)), "Creating file tree"))
        val commit = GitHubApi.parseSha(ok(request("POST", "${target.apiBase}/git/commits", GitHubApi.commitBody(message, tree, parent)), "Creating commit"))
        ok(request("PATCH", "${target.apiBase}/git/refs/heads/${target.branch}", GitHubApi.updateRefBody(commit, force = false)), "Moving ${target.branch} to the new commit")
        return commit
    }

    fun runsForCommit(sha: String): List<WorkflowRun> =
        GitHubApi.parseRuns(ok(request("GET", "${target.apiBase}/actions/runs?head_sha=$sha&per_page=10"), "Listing builds"))

    fun run(id: Long): WorkflowRun =
        GitHubApi.parseRun(ok(request("GET", "${target.apiBase}/actions/runs/$id"), "Reading build"))

    fun artifacts(runId: Long): List<WorkflowArtifact> =
        GitHubApi.parseArtifacts(ok(request("GET", "${target.apiBase}/actions/runs/$runId/artifacts"), "Listing build files"))

    /** Downloads the artifact zip and extracts the first .apk inside it into [destDir]. */
    fun downloadApk(artifact: WorkflowArtifact, destDir: File): File {
        val first = request("GET", "${target.apiBase}/actions/artifacts/${artifact.id}/zip", followRedirects = false)
        val zipUrl = when (first.code) {
            302, 301, 307 -> first.location ?: throw IOException("GitHub sent a redirect without a location")
            else -> {
                ok(first, "Downloading ${artifact.name}")
                throw IOException("GitHub did not return a download link for ${artifact.name}")
            }
        }
        destDir.mkdirs()
        // The signed storage URL must be fetched WITHOUT the GitHub token.
        val conn = (URL(zipUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "PackFlow-Studio-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Artifact download failed (HTTP ${conn.responseCode})")
            ZipInputStream(conn.inputStream.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && e.name.endsWith(".apk", ignoreCase = true)) {
                        val out = File(destDir, e.name.substringAfterLast('/'))
                        out.outputStream().use { zip.copyTo(it) }
                        return out
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        throw IOException("The build artifact ${artifact.name} has no .apk inside")
    }

    /** Returns a short excerpt of the failed job's log (error lines plus the tail). */
    fun failedLogExcerpt(runId: Long): String {
        val jobs = GitHubApi.parseJobs(ok(request("GET", "${target.apiBase}/actions/runs/$runId/jobs"), "Listing jobs"))
        val failed = jobs.firstOrNull { it.conclusion == "failure" } ?: return "No failed job found."
        val first = request("GET", "${target.apiBase}/actions/jobs/${failed.id}/logs", followRedirects = false)
        val logText = when (first.code) {
            302, 301, 307 -> first.location?.let { request("GET", it, auth = false).body } ?: ""
            200 -> first.body
            else -> return GitHubApi.explainHttp(first.code, first.body, "Reading the build log")
        }
        return "Job '${failed.name}' failed.\n" + GitHubApi.summarizeLog(logText)
    }
}
