package com.example.core

/** A terrain type a level can paint with. [image] is a project path such as "tiles/grass.png". */
data class LevelTile(
    val id: String,
    val name: String,
    val color: Long,
    val image: String? = null,
    val walkable: Boolean = true,
    val moveCost: Int = 1,
    val cover: Int = 0
)

data class LevelUnit(val unitId: String, val team: Int, val x: Int, val y: Int)

data class LevelObject(val type: String, val x: Int, val y: Int, val image: String? = null, val blocking: Boolean = true)

data class SpawnPoint(val team: Int, val x: Int, val y: Int)

/**
 * A tile-grid level, saved as levels/<name>.json and read by the PackFlow 2D engine.
 * grid[y * width + x] is an index into [tiles].
 */
data class Level(
    val name: String,
    val width: Int,
    val height: Int,
    val tileSize: Int = 32,
    val tiles: List<LevelTile> = DEFAULT_TILES,
    val grid: IntArray = IntArray(width * height),
    val units: List<LevelUnit> = emptyList(),
    val objects: List<LevelObject> = emptyList(),
    val spawns: List<SpawnPoint> = emptyList()
) {
    init {
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE) { "Level size must be 1..$MAX_SIDE" }
        require(grid.size == width * height) { "Grid size doesn't match ${width}x$height" }
    }

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun tileAt(x: Int, y: Int): LevelTile = tiles[grid[y * width + x].coerceIn(0, tiles.lastIndex)]

    fun paint(x: Int, y: Int, tile: Int): Level {
        if (!inBounds(x, y) || tile !in tiles.indices || grid[y * width + x] == tile) return this
        return copy(grid = grid.copyOf().also { it[y * width + x] = tile })
    }

    /** Flood fill of the 4-connected region of the same tile. */
    fun fill(x: Int, y: Int, tile: Int): Level {
        if (!inBounds(x, y) || tile !in tiles.indices) return this
        val from = grid[y * width + x]
        if (from == tile) return this
        val g = grid.copyOf()
        val stack = ArrayDeque<Int>()
        stack.add(y * width + x)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (g[i] != from) continue
            g[i] = tile
            val cx = i % width
            val cy = i / width
            if (cx > 0) stack.add(i - 1)
            if (cx < width - 1) stack.add(i + 1)
            if (cy > 0) stack.add(i - width)
            if (cy < height - 1) stack.add(i + width)
        }
        return copy(grid = g)
    }

    /** Resizes from the top-left corner; things outside the new size are dropped. */
    fun resize(w: Int, h: Int): Level {
        val nw = w.coerceIn(1, MAX_SIDE)
        val nh = h.coerceIn(1, MAX_SIDE)
        val g = IntArray(nw * nh)
        for (yy in 0 until minOf(height, nh)) for (xx in 0 until minOf(width, nw)) g[yy * nw + xx] = grid[yy * width + xx]
        fun ok(x: Int, y: Int) = x in 0 until nw && y in 0 until nh
        return copy(
            width = nw, height = nh, grid = g,
            units = units.filter { ok(it.x, it.y) },
            objects = objects.filter { ok(it.x, it.y) },
            spawns = spawns.filter { ok(it.x, it.y) }
        )
    }

    /** Puts a unit on a cell (replacing whatever unit was there). */
    fun placeUnit(unitId: String, team: Int, x: Int, y: Int): Level =
        if (!inBounds(x, y)) this else copy(units = units.filterNot { it.x == x && it.y == y } + LevelUnit(unitId, team, x, y))

    fun placeObject(type: String, x: Int, y: Int, image: String? = null, blocking: Boolean = true): Level =
        if (!inBounds(x, y)) this else copy(objects = objects.filterNot { it.x == x && it.y == y } + LevelObject(type, x, y, image, blocking))

    fun toggleSpawn(team: Int, x: Int, y: Int): Level {
        if (!inBounds(x, y)) return this
        val existing = spawns.any { it.x == x && it.y == y && it.team == team }
        return copy(spawns = spawns.filterNot { it.x == x && it.y == y } + if (existing) emptyList() else listOf(SpawnPoint(team, x, y)))
    }

    /** Removes the unit, object and spawn on a cell. */
    fun clearCell(x: Int, y: Int): Level = copy(
        units = units.filterNot { it.x == x && it.y == y },
        objects = objects.filterNot { it.x == x && it.y == y },
        spawns = spawns.filterNot { it.x == x && it.y == y }
    )

    fun withTile(tile: LevelTile): Level = copy(tiles = tiles + tile)

    fun toJson(): String = Json.stringify(
        linkedMapOf(
            "format" to "packflow-level",
            "version" to 1,
            "name" to name,
            "width" to width,
            "height" to height,
            "tileSize" to tileSize,
            "tiles" to tiles.map {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "color" to colorHex(it.color), "image" to it.image,
                    "walkable" to it.walkable, "moveCost" to it.moveCost, "cover" to it.cover
                )
            },
            "grid" to (0 until height).map { y -> (0 until width).map { x -> grid[y * width + x] } },
            "units" to units.map { linkedMapOf("unit" to it.unitId, "team" to it.team, "x" to it.x, "y" to it.y) },
            "objects" to objects.map { linkedMapOf("type" to it.type, "x" to it.x, "y" to it.y, "image" to it.image, "blocking" to it.blocking) },
            "spawns" to spawns.map { linkedMapOf("team" to it.team, "x" to it.x, "y" to it.y) }
        ),
        indent = 1
    )

    override fun equals(other: Any?): Boolean = other is Level && name == other.name && width == other.width &&
        height == other.height && tileSize == other.tileSize && tiles == other.tiles && grid.contentEquals(other.grid) &&
        units == other.units && objects == other.objects && spawns == other.spawns

    override fun hashCode(): Int = name.hashCode() * 31 + grid.contentHashCode()

    companion object {
        const val MAX_SIDE = 128

        val DEFAULT_TILES = listOf(
            LevelTile("grass", "Grass", 0xFF3F6B2F),
            LevelTile("dirt", "Dirt", 0xFF6B5233),
            LevelTile("road", "Road", 0xFF8A8172),
            LevelTile("forest", "Forest", 0xFF22451E, moveCost = 2, cover = 1),
            LevelTile("ruins", "Ruins", 0xFF5A5A63, moveCost = 2, cover = 2),
            LevelTile("water", "Water", 0xFF1E4F7A, walkable = false),
            LevelTile("rock", "Rock", 0xFF3A3A40, walkable = false),
            LevelTile("lava", "Lava", 0xFFB3361B, walkable = false)
        )

        fun blank(name: String, width: Int = 16, height: Int = 12) = Level(name, width, height)

        fun colorHex(argb: Long) = String.format("#%06X", argb and 0xFFFFFF)

        fun fromJson(text: String): Level {
            val m = Json.parseObject(text)
            val w = m.int("width")
            val h = m.int("height")
            val tiles = m.objList("tiles").map {
                LevelTile(
                    id = it.str("id"), name = it.str("name", it.str("id")),
                    color = IconSpec.parseColor(it.str("color")) ?: 0xFF808080,
                    image = it["image"] as? String, walkable = it.bool("walkable", true),
                    moveCost = it.int("moveCost", 1), cover = it.int("cover", 0)
                )
            }.ifEmpty { DEFAULT_TILES }
            val grid = IntArray(w * h)
            m.list("grid").forEachIndexed { y, row ->
                (row as? List<*>)?.forEachIndexed { x, v -> if (x < w && y < h) grid[y * w + x] = ((v as? Number)?.toInt() ?: 0).coerceIn(0, tiles.lastIndex) }
            }
            return Level(
                name = m.str("name", "level"), width = w, height = h, tileSize = m.int("tileSize", 32),
                tiles = tiles, grid = grid,
                units = m.objList("units").map { LevelUnit(it.str("unit"), it.int("team"), it.int("x"), it.int("y")) },
                objects = m.objList("objects").map { LevelObject(it.str("type"), it.int("x"), it.int("y"), it["image"] as? String, it.bool("blocking", true)) },
                spawns = m.objList("spawns").map { SpawnPoint(it.int("team"), it.int("x"), it.int("y")) }
            )
        }
    }
}
