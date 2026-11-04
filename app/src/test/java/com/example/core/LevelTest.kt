package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelTest {
    @Test
    fun paintFillAndBounds() {
        val l = Level.blank("t", 4, 3).paint(1, 1, 5)
        assertEquals("water", l.tileAt(1, 1).id)
        assertSame(l, l.paint(9, 9, 1))
        val filled = l.fill(0, 0, 2)
        assertEquals("road", filled.tileAt(3, 2).id)
        assertEquals("water", filled.tileAt(1, 1).id) // other region untouched
    }

    @Test
    fun unitsObjectsSpawns() {
        var l = Level.blank("t", 5, 5)
            .placeUnit("marine", 0, 1, 1)
            .placeUnit("ork", 1, 1, 1)    // replaces
            .placeObject("crate", 2, 2)
            .toggleSpawn(0, 0, 0)
        assertEquals(listOf(LevelUnit("ork", 1, 1, 1)), l.units)
        assertEquals(1, l.spawns.size)
        l = l.toggleSpawn(0, 0, 0)
        assertTrue(l.spawns.isEmpty())
        l = l.clearCell(2, 2)
        assertTrue(l.objects.isEmpty())
    }

    @Test
    fun resizeDropsOutside() {
        val l = Level.blank("t", 6, 6).paint(5, 5, 3).placeUnit("u", 0, 5, 5).paint(1, 1, 4)
        val small = l.resize(3, 3)
        assertEquals(9, small.grid.size)
        assertTrue(small.units.isEmpty())
        assertEquals("ruins", small.tileAt(1, 1).id)
        assertEquals(10, small.resize(10, 2).width)
    }

    @Test
    fun jsonRoundTrip() {
        val l = Level.blank("Hive City", 7, 5)
            .withTile(LevelTile("plate", "Deck plate", 0xFF777788, image = "tiles/plate.png", cover = 1))
            .paint(3, 2, 8).placeUnit("marine", 0, 0, 0).placeObject("barricade", 4, 4, blocking = false).toggleSpawn(1, 6, 4)
        val back = Level.fromJson(l.toJson())
        assertEquals(l, back)
        assertEquals("tiles/plate.png", back.tileAt(3, 2).image)
        assertFalse(back.objects.single().blocking)
        assertTrue(l.toJson().contains("\"format\": \"packflow-level\""))
    }
}
