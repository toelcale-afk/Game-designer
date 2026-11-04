package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubApiTest {
    @Test
    fun validatesTarget() {
        assertNull(GitHubTarget("toelcale-afk", "Game-designer", "main").validationError())
        assertNotNull(GitHubTarget("", "r").validationError())
        assertNotNull(GitHubTarget("me", "bad name").validationError())
        assertNotNull(GitHubTarget("me", "r", "a..b").validationError())
    }

    @Test
    fun treeBodyKeepsBaseTree() {
        val body = Json.parseObject(GitHubApi.treeBody(listOf("a/b.txt" to "abc"), "base1"))
        assertEquals("base1", body.str("base_tree"))
        val entry = body.objList("tree").single()
        assertEquals("a/b.txt", entry.str("path"))
        assertEquals("100644", entry.str("mode"))
    }

    @Test
    fun blobBodyIsBase64() {
        val body = Json.parseObject(GitHubApi.blobBody("hi".toByteArray()))
        assertEquals("aGk=", body.str("content"))
        assertEquals("base64", body.str("encoding"))
    }

    @Test
    fun parsesRunsAndStatus() {
        val json = """{"total_count":1,"workflow_runs":[{"id":5,"status":"completed","conclusion":"success","html_url":"u","head_sha":"s","name":"Build"}]}"""
        val run = GitHubApi.parseRuns(json).single()
        assertTrue(run.succeeded)
        assertEquals("Build succeeded", run.plainStatus)
        val queued = run.copy(status = "queued", conclusion = null)
        assertFalse(queued.isFinished)
    }

    @Test
    fun picksApkArtifactSkippingExpired() {
        val list = listOf(
            WorkflowArtifact(1, "reports", 10, false),
            WorkflowArtifact(2, "old-apk", 10, true),
            WorkflowArtifact(3, "game-debug-apk", 10, false)
        )
        assertEquals(3L, GitHubApi.pickApkArtifact(list)?.id)
    }

    @Test
    fun summarizesLogErrors() {
        val log = "2026-01-01T00:00:00.000Z ok\n2026-01-01T00:00:01.000Z e: file.kt:3 Unresolved reference\nlast"
        val s = GitHubApi.summarizeLog(log)
        assertTrue(s.contains("e: file.kt:3 Unresolved reference"))
        assertTrue(s.contains("last"))
    }

    @Test
    fun explainsHttpErrors() {
        val msg = GitHubApi.explainHttp(404, """{"message":"Not Found"}""", "Opening repo")
        assertTrue(msg.startsWith("Opening repo failed (HTTP 404: Not Found)"))
    }
}
