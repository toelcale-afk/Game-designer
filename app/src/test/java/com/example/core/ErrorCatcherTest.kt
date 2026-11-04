package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ErrorCatcherTest {
    /** Unit tests run from the app module directory (Gradle) or the repo root. */
    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path), File("/home/user/Game-designer/$path")).firstOrNull { it.exists() }
            ?: error("Can't find $path")

    @Test
    fun embeddedCopiesMatchReusableFiles() {
        assertEquals(repoFile("reusable/error-catcher/error-catcher.js").readText(), ErrorCatcher.JS)
        assertEquals(
            repoFile("reusable/error-catcher/android/PackFlowCrashCatcher.kt").readText(),
            ErrorCatcherAssets.ANDROID_KT_TEMPLATE
        )
    }

    @Test
    fun injectsFirstInHead() {
        val out = ErrorCatcher.injectIntoHtml("<!DOCTYPE html><html><head><title>x</title><script>a()</script></head><body></body></html>")
        assertTrue(out.indexOf(ErrorCatcher.MARKER) < out.indexOf("<title>"))
        assertTrue(out.startsWith("<!DOCTYPE html><html><head>"))
    }

    @Test
    fun injectsIntoFragmentsAndOnlyOnce() {
        val frag = ErrorCatcher.injectIntoHtml("<div>hi</div>")
        assertTrue(frag.startsWith("<script>"))
        assertEquals(frag, ErrorCatcher.injectIntoHtml(frag))
        val noHead = ErrorCatcher.injectIntoHtml("<html lang=\"en\"><body></body></html>")
        assertTrue(noHead.contains("<head>\n<script>"))
    }

    @Test
    fun androidTemplateUsesAppPackage() {
        val src = ErrorCatcher.androidKotlin("com.me.game")
        assertTrue(src.startsWith("package com.me.game\n"))
        assertTrue(src.contains("class PackFlowErrorBridge"))
    }
}
