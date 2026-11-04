package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLayoutTest {
    @Test
    fun mapsProjectFilesToWebRoot() {
        assertEquals("index.html", ProjectLayout.webPathFor("code/index.html"))
        assertEquals("data/units.json", ProjectLayout.webPathFor("code/data/units.json"))
        assertEquals("sprites/marine.png", ProjectLayout.webPathFor("sprites/marine.png"))
        assertNull(ProjectLayout.webPathFor("icon/icon.json"))
        assertNull(ProjectLayout.webPathFor("builds/game.apk"))
        assertNull(ProjectLayout.webPathFor("project.json"))
        assertNull(ProjectLayout.webPathFor("code/.hidden"))
        assertNull(ProjectLayout.webPathFor("sprites/../project.json"))
    }

    @Test
    fun inverseMappingRoundTrips() {
        listOf("code/index.html", "code/js/engine.js", "sounds/laser.wav", "levels/l1.json").forEach { rel ->
            assertEquals(rel, ProjectLayout.projectPathForWeb(ProjectLayout.webPathFor(rel)!!))
        }
        assertNull(ProjectLayout.projectPathForWeb("../secret"))
        assertEquals("code/sprites", ProjectLayout.projectPathForWeb("sprites")) // bare folder name is code/
    }

    @Test
    fun webFilesKeepFirstOnClash() {
        val out = ProjectLayout.webFiles(mapOf(
            "code/sprites/a.png" to byteArrayOf(1),
            "sprites/a.png" to byteArrayOf(2),
            "icon/x.png" to byteArrayOf(3)
        ))
        assertEquals(setOf("sprites/a.png"), out.keys)
        assertEquals(1, out.getValue("sprites/a.png")[0].toInt()) // code/ sorts first
    }

    @Test
    fun namesAndKinds() {
        assertEquals("space-marines-act-1", ProjectLayout.slug("Space Marines: Act 1"))
        assertEquals("project", ProjectLayout.slug("!!!"))
        assertEquals("a_b", ProjectLayout.safeName("a/b"))
        assertNull(ProjectLayout.safeName(".."))
        assertNull(ProjectLayout.safeName("   "))
        assertEquals(FileKind.IMAGE, ProjectLayout.kindOf("x.PNG"))
        assertEquals(FileKind.AUDIO, ProjectLayout.kindOf("x.wav"))
        assertEquals(FileKind.TEXT, ProjectLayout.kindOf("x.json"))
        assertEquals(FileKind.OTHER, ProjectLayout.kindOf("x.apk"))
        assertFalse(ProjectLayout.isSafeRelative("/abs"))
        assertTrue(ProjectLayout.isSafeRelative("a/b.c"))
    }

    @Test
    fun metaRoundTripsAndRepairsBadPackage() {
        val m = ProjectMeta("Ork Waaagh", packageName = "com.me.orks", versionCode = 3, createdAt = 5)
        assertEquals(m, ProjectMeta.fromJson(m.toJson(), "x"))
        val fixed = ProjectMeta.fromJson("""{"name":"Ork","packageName":"BAD NAME"}""", "x")
        assertTrue(ShellApk.isValidPackageName(fixed.packageName))
        assertEquals("folder", ProjectMeta.fromJson("not json", "folder").name)
    }

    @Test
    fun templatesProduceIndexHtml() {
        ProjectTemplates.all.forEach { t ->
            assertTrue(t.id, t.files("Test").containsKey("code/index.html"))
        }
    }
}
