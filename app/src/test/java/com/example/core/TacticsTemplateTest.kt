package com.example.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticsTemplateTest {
    private val files = TacticsTemplate.files("Grim Test")

    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path), File("/home/user/Game-designer/$path")).firstOrNull { it.exists() } ?: error("missing $path")

    @Test
    fun embeddedEngineMatchesCanonicalFiles() {
        assertEquals(repoFile("engine/packflow-engine.js").readText(), EngineAssets.ENGINE_JS)
        assertEquals(repoFile("engine/tactics.js").readText(), EngineAssets.TACTICS_JS)
    }

    @Test
    fun designIsValidAgainstItsOwnFiles() {
        val design = GameDesign.fromFiles { name -> files["${ProjectLayout.DATA}/$name"]?.let { String(it) } }
        assertEquals(emptyList<String>(), design.validate(existingFiles = files.keys))
        assertEquals(2, design.campaign.missions.size)
        assertEquals("turnbased", design.campaign.missions.first().mode)
    }

    @Test
    fun webRootHasEverythingTheGameLoads() {
        val web = ProjectLayout.webFiles(files)
        listOf(
            "index.html", "engine/packflow-engine.js", "engine/tactics.js", "data/units.json", "data/campaign.json",
            "data/game-data.js", "levels/landfall.json", "levels/hold_the_gate.json", "sounds/shot.wav"
        ).forEach { assertTrue("missing $it", it in web) }
        val design = GameDesign.fromFiles { name -> files["${ProjectLayout.DATA}/$name"]?.let { String(it) } }
        design.sounds.values.forEach { assertTrue("sound $it not shipped", it in web) }
    }

    @Test
    fun levelsHaveBothSides() {
        val l1 = TacticsTemplate.landfall()
        assertTrue(l1.units.any { it.team == 0 } && l1.units.any { it.team == 1 })
        val l2 = TacticsTemplate.holdTheGate()
        assertTrue(l2.units.none { it.team == 1 })
        assertTrue(l2.spawns.count { it.team == 1 } >= 4)
        // every placed unit stands on walkable ground
        listOf(l1, l2).forEach { l -> l.units.forEach { u -> assertTrue("${l.name} ${u.unitId}", l.tileAt(u.x, u.y).walkable) } }
    }

    @Test
    fun registeredAsTemplate() {
        assertEquals(TacticsTemplate.ID, ProjectTemplates.byId("tactics").id)
    }
}
