package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDesignTest {
    private val design = GameDesign(
        campaign = Campaign(
            name = "Purge", start = "m1",
            missions = listOf(
                Mission("m2", "Hold", level = "levels/b.json", win = WinCondition.SURVIVE_TURNS, winValue = "5", next = null),
                Mission("m1", "Landing", briefing = "Secure the zone.\nNo retreat.", level = "levels/a.json", playerFaction = "space_marines", enemyFaction = "orks", next = "m2")
            )
        ),
        sounds = mapOf("attack" to "sounds/laser.wav"),
        menu = MenuSettings(title = "Grim Tactics", accentColor = 0xFFAA2200),
        hud = HudSettings(position = "top", scale = 1.25f),
        save = SaveSettings(slots = 5, autosaveEachTurn = false)
    )

    @Test
    fun roundTripsThroughFiles() {
        val files = design.files()
        val back = GameDesign.fromFiles { files[it] }
        assertEquals(design, back)
        assertTrue(files.getValue("game-data.js").startsWith("// Generated"))
    }

    @Test
    fun gameDataJsIsParseableJson() {
        val js = design.files(mapOf("levels/a.json" to Level.blank("a", 3, 2).toJson())).getValue("game-data.js")
        val json = js.substringAfter("window.GAME_DATA = ").trim().removeSuffix(";")
        val m = Json.parseObject(json)
        assertEquals("Grim Tactics", m.obj("ui")!!.obj("menu")!!.str("title"))
        assertEquals(3, m.obj("levels")!!.obj("levels/a.json")!!.int("width"))
        assertEquals(6, m.obj("units")!!.objList("units").size)
    }

    @Test
    fun ordersMissionsByChain() {
        assertEquals(listOf("m1", "m2"), design.campaign.ordered().map { it.id })
    }

    @Test
    fun validDesignHasNoIssues() {
        assertEquals(emptyList<String>(), design.validate())
    }

    @Test
    fun findsProblems() {
        val bad = design.copy(
            units = design.units + UnitDef("marine", "Copy", "nobody", health = 0, range = 0),
            campaign = design.campaign.copy(missions = design.campaign.missions.map {
                if (it.id == "m2") it.copy(next = "m1", win = WinCondition.REACH_TILE, winValue = "here") else it
            })
        )
        val issues = bad.validate(existingFiles = setOf("levels/a.json"))
        listOf("share the ID 'marine'", "faction 'nobody'", "health", "range", "loops back", "tile must look like", "levels/b.json is missing").forEach { needle ->
            assertTrue("missing '$needle' in $issues", issues.any { it.contains(needle) })
        }
    }

    @Test
    fun uniqueIds() {
        assertEquals("ork_boy_2", GameDesign.uniqueId("Ork Boy", listOf("ork_boy")))
        assertEquals("item", GameDesign.slugId("!!"))
    }

    @Test
    fun missingFilesKeepDefaults() {
        val d = GameDesign.fromFiles { null }
        assertEquals(GameDesign.DEFAULT_UNITS, d.units)
    }
}
