package com.example.core

/** Where generated projects are pushed and built. The token is NOT part of this (see GitHubSettings). */
data class GitHubTarget(
    val owner: String,
    val repo: String,
    val branch: String = "main"
) {
    val isComplete: Boolean get() = validationError() == null

    fun validationError(): String? = when {
        owner.isBlank() -> "Enter the GitHub owner (your user or organization name)."
        !NAME.matches(owner) -> "Owner '$owner' has characters GitHub doesn't allow."
        repo.isBlank() -> "Enter the repository name."
        !REPO.matches(repo) -> "Repository '$repo' has characters GitHub doesn't allow."
        branch.isBlank() -> "Enter a branch name (for example main)."
        branch.contains("..") || branch.startsWith("/") || branch.endsWith("/") || branch.contains(' ') ->
            "Branch '$branch' isn't a valid git branch name."
        else -> null
    }

    val apiBase: String get() = "https://api.github.com/repos/$owner/$repo"
    val webUrl: String get() = "https://github.com/$owner/$repo"

    companion object {
        private val NAME = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}

data class WorkflowRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val headSha: String,
    val name: String
) {
    val isFinished: Boolean get() = status == "completed"
    val succeeded: Boolean get() = isFinished && conclusion == "success"

    val plainStatus: String
        get() = when {
            !isFinished && status == "queued" -> "Waiting for a GitHub runner…"
            !isFinished -> "Building on GitHub ($status)…"
            succeeded -> "Build succeeded"
            else -> "Build ${conclusion ?: "failed"}"
        }
}

data class WorkflowArtifact(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean)

data class WorkflowJob(val id: Long, val name: String, val conclusion: String?)

/** Pure helpers for the GitHub REST API (request bodies and response parsing). */
object GitHubApi {

    fun blobBody(bytes: ByteArray): String = Json.stringify(
        mapOf("content" to B64.encode(bytes), "encoding" to "base64")
    )

    /** Tree entries: path -> blob sha. `baseTree` keeps files we don't send (e.g. the CI-made keystore). */
    fun treeBody(entries: List<Pair<String, String>>, baseTree: String?): String {
        val tree = entries.map { (path, sha) ->
            mapOf("path" to path, "mode" to "100644", "type" to "blob", "sha" to sha)
        }
        val body = LinkedHashMap<String, Any?>()
        if (baseTree != null) body["base_tree"] = baseTree
        body["tree"] = tree
        return Json.stringify(body)
    }

    fun commitBody(message: String, treeSha: String, parentSha: String?): String = Json.stringify(
        mapOf(
            "message" to message,
            "tree" to treeSha,
            "parents" to listOfNotNull(parentSha)
        )
    )

    fun updateRefBody(sha: String, force: Boolean = true): String =
        Json.stringify(mapOf("sha" to sha, "force" to force))

    fun createRefBody(branch: String, sha: String): String =
        Json.stringify(mapOf("ref" to "refs/heads/$branch", "sha" to sha))

    /** Used once to put a first commit into a brand-new empty repository. */
    fun initFileBody(branch: String, message: String, content: String): String = Json.stringify(
        mapOf(
            "message" to message,
            "content" to B64.encode(content.toByteArray()),
            "branch" to branch
        )
    )

    fun dispatchBody(branch: String): String = Json.stringify(mapOf("ref" to branch))

    fun parseSha(json: String): String = Json.parseObject(json).str("sha")

    fun parseRefSha(json: String): String =
        Json.parseObject(json).obj("object")?.str("sha") ?: error("No object.sha in ref response")

    fun parseCommitTreeSha(json: String): String =
        Json.parseObject(json).obj("tree")?.str("sha") ?: error("No tree.sha in commit response")

    fun parseDefaultBranch(json: String): String = Json.parseObject(json).str("default_branch", "main")

    fun parseRuns(json: String): List<WorkflowRun> =
        Json.parseObject(json).objList("workflow_runs").map(::toRun)

    fun parseRun(json: String): WorkflowRun = toRun(Json.parseObject(json))

    private fun toRun(m: Map<String, Any?>) = WorkflowRun(
        id = m.long("id"),
        status = m.str("status"),
        conclusion = m["conclusion"] as? String,
        htmlUrl = m.str("html_url"),
        headSha = m.str("head_sha"),
        name = m.str("name")
    )

    fun parseArtifacts(json: String): List<WorkflowArtifact> =
        Json.parseObject(json).objList("artifacts").map {
            WorkflowArtifact(it.long("id"), it.str("name"), it.long("size_in_bytes"), it.bool("expired"))
        }

    fun parseJobs(json: String): List<WorkflowJob> =
        Json.parseObject(json).objList("jobs").map {
            WorkflowJob(it.long("id"), it.str("name"), it["conclusion"] as? String)
        }

    /** Picks the APK artifact (prefers names containing "apk", skips expired ones). */
    fun pickApkArtifact(artifacts: List<WorkflowArtifact>): WorkflowArtifact? =
        artifacts.filterNot { it.expired }.let { live ->
            live.firstOrNull { it.name.contains("apk", ignoreCase = true) } ?: live.firstOrNull()
        }

    /** Keeps the useful end of a CI log: lines with errors plus the last [tail] lines. */
    fun summarizeLog(log: String, tail: Int = 60): String {
        val lines = log.lines().map { it.replace(Regex("^\\d{4}-\\d\\d-\\d\\dT[0-9:.]+Z\\s"), "") }
        val errors = lines.filter {
            it.startsWith("e: ") || it.contains("error:", ignoreCase = true) ||
                it.contains("FAILED") || it.contains("What went wrong")
        }.distinct().take(40)
        val end = lines.takeLast(tail)
        return buildString {
            if (errors.isNotEmpty()) {
                appendLine("--- Error lines ---")
                errors.forEach { appendLine(it) }
                appendLine()
            }
            appendLine("--- Last ${end.size} lines ---")
            end.forEach { appendLine(it) }
        }.trim()
    }

    /** Explains common HTTP failures in plain English. */
    fun explainHttp(code: Int, body: String, action: String): String {
        val apiMsg = runCatching { Json.parseObject(body).str("message") }.getOrDefault("")
        val hint = when (code) {
            401 -> "The token was rejected. Check it hasn't expired and paste it again."
            403 -> if (apiMsg.contains("workflow", true))
                "The token can't change workflow files. Give it the 'workflow' scope (classic) or Workflows: Read and write (fine-grained)."
            else "The token doesn't have permission. It needs Contents: Read and write and Actions: Read (fine-grained), or the 'repo' and 'workflow' scopes (classic)."
            404 -> "Not found. Check owner and repository name, and that the token can see this repository."
            409 -> "The repository is empty or the branch changed at the same time. Try again."
            422 -> "GitHub rejected the request: $apiMsg"
            else -> ""
        }
        return "$action failed (HTTP $code${if (apiMsg.isNotBlank()) ": $apiMsg" else ""}). $hint".trim()
    }
}
