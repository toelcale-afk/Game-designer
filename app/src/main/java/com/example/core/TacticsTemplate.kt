package com.example.core

/**
 * "Grid tactics" starter: the PackFlow 2D engine, a two-mission Warhammer-style campaign
 * (Space Marines vs Orks, then Tyranids), two levels and generated sound effects.
 * Runs in PackFlow's Preview and builds with both the quick build and GitHub.
 */
object TacticsTemplate {
    const val ID = "tactics"

    val TEMPLATE = ProjectTemplate(
        id = ID,
        title = "Grid tactics (turn-based)",
        description = "Space Marines, Orks and Tyranids on a tile grid: move, attack, enemy AI, two missions."
    ) { name -> files(name) }

    fun indexHtml(title: String): String = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
  <title>${GeneratedProjectFiles.xmlEscape(title)}</title>
  <style>
    html, body { margin: 0; height: 100%; background: #0B1120; overflow: hidden; }
    canvas { display: block; width: 100vw; height: 100vh; touch-action: none; user-select: none; -webkit-user-select: none; }
  </style>
</head>
<body>
  <canvas id="game"></canvas>
  <!-- Designer data bundle (lets the game start even where fetch() of local files is blocked). -->
  <script src="data/game-data.js" onerror="void 0"></script>
  <script src="engine/packflow-engine.js"></script>
  <script src="engine/tactics.js"></script>
  <script>PF.boot(document.getElementById('game'));</script>
</body>
</html>
"""

    /** Mission 1: Marines land on the left, an Ork mob holds the ruins on the right. */
    fun landfall(): Level {
        var l = Level.blank("Landfall", 14, 10)
        // terrain: 0 grass, 1 dirt, 2 road, 3 forest, 4 ruins, 5 water, 6 rock
        for (x in 0 until 14) l = l.paint(x, 5, 2)
        for (y in 0 until 10) l = l.paint(6, y, if (y == 5) 2 else 5)
        l = l.paint(6, 4, 2).paint(6, 6, 2) // ford
        listOf(1 to 1, 2 to 1, 1 to 2, 3 to 8, 4 to 8, 2 to 8).forEach { (x, y) -> l = l.paint(x, y, 3) }
        listOf(10 to 2, 11 to 2, 10 to 3, 11 to 7, 12 to 7, 9 to 8).forEach { (x, y) -> l = l.paint(x, y, 4) }
        listOf(8 to 0, 9 to 0, 13 to 9, 4 to 3).forEach { (x, y) -> l = l.paint(x, y, 6) }
        l = l.placeObject("barricade", 8, 4).placeObject("crate", 3, 3, blocking = true).placeObject("crate", 11, 5)
        l = l.placeUnit("marine_sergeant", 0, 1, 5).placeUnit("marine", 0, 1, 4).placeUnit("marine", 0, 1, 6).placeUnit("marine", 0, 0, 5)
        l = l.placeUnit("ork_boy", 1, 11, 3).placeUnit("ork_boy", 1, 12, 5).placeUnit("ork_boy", 1, 11, 6)
            .placeUnit("ork_shoota", 1, 12, 2).placeUnit("ork_shoota", 1, 12, 8)
        return l
    }

    /** Mission 2: hold the gate for 6 turns while Tyranids pour in from spawn points. */
    fun holdTheGate(): Level {
        var l = Level.blank("Hold the Gate", 12, 10)
        for (y in 0 until 10) for (x in 0 until 12) l = l.paint(x, y, if ((x + y) % 7 == 0) 1 else 0)
        for (y in 0 until 10) if (y !in 4..5) l = l.paint(3, y, 6)
        listOf(2 to 3, 2 to 6, 4 to 4, 4 to 5).forEach { (x, y) -> l = l.paint(x, y, 4) }
        listOf(7 to 1, 8 to 1, 8 to 8, 9 to 8, 7 to 4).forEach { (x, y) -> l = l.paint(x, y, 3) }
        l = l.placeObject("objective", 1, 4, blocking = false)
        l = l.placeUnit("marine_sergeant", 0, 2, 4).placeUnit("marine", 0, 2, 5).placeUnit("marine", 0, 1, 3).placeUnit("marine", 0, 1, 6)
        // No enemy units placed: the engine fills team 1 spawns from the mission's enemy faction.
        listOf(11 to 0, 11 to 2, 11 to 4, 11 to 5, 11 to 7, 11 to 9, 10 to 1, 10 to 8).forEach { (x, y) -> l = l.toggleSpawn(1, x, y) }
        return l
    }

    fun design(name: String): GameDesign = GameDesign(
        campaign = Campaign(
            name = "Purge of Vigil Hive",
            start = "landfall",
            missions = listOf(
                Mission(
                    id = "landfall", name = "Landfall",
                    briefing = "Brothers, the Ork warband has dug into the ruins east of the river. Cross at the ford, use the forest for cover and leave none standing.\n\nTap a marine to see where it can move (blue) and what it can hit (red). Tap End turn when you're done.",
                    level = "levels/landfall.json", playerFaction = "space_marines", enemyFaction = "orks",
                    win = WinCondition.ELIMINATE_ALL, next = "hold_the_gate"
                ),
                Mission(
                    id = "hold_the_gate", name = "Hold the Gate",
                    briefing = "The hive gate must not fall. Tyranid swarms are coming through the eastern breach. Hold the ruins for six turns until the gunships arrive.",
                    level = "levels/hold_the_gate.json", playerFaction = "space_marines", enemyFaction = "tyranids",
                    win = WinCondition.SURVIVE_TURNS, winValue = "6"
                )
            )
        ),
        menu = MenuSettings(title = name, subtitle = "A PackFlow tactics game", showCredits = true, credits = "Made with PackFlow Studio.\nSound effects generated with the PackFlow sfxr generator."),
        sounds = SOUNDS.associate { (action, file, _) -> action.id to "sounds/$file" },
        save = SaveSettings(storageKey = ProjectLayout.slug(name) + "-save")
    )

    private val SOUNDS = listOf(
        Triple(SoundAction.SELECT, "select.wav", SfxPreset.BLIP),
        Triple(SoundAction.MOVE, "step.wav", SfxPreset.FOOTSTEP),
        Triple(SoundAction.ATTACK, "shot.wav", SfxPreset.LASER),
        Triple(SoundAction.HIT, "hit.wav", SfxPreset.HIT),
        Triple(SoundAction.DEATH, "explosion.wav", SfxPreset.EXPLOSION),
        Triple(SoundAction.TURN_START, "turn.wav", SfxPreset.PICKUP),
        Triple(SoundAction.VICTORY, "victory.wav", SfxPreset.POWERUP),
        Triple(SoundAction.BUTTON, "button.wav", SfxPreset.BLIP)
    )

    fun files(name: String): Map<String, ByteArray> {
        val levels = mapOf("levels/landfall.json" to landfall().toJson(), "levels/hold_the_gate.json" to holdTheGate().toJson())
        val out = LinkedHashMap<String, ByteArray>()
        out["code/index.html"] = indexHtml(name).toByteArray()
        out["code/engine/packflow-engine.js"] = EngineAssets.ENGINE_JS.toByteArray()
        out["code/engine/tactics.js"] = EngineAssets.TACTICS_JS.toByteArray()
        design(name).files(levels).forEach { (file, text) -> out["${ProjectLayout.DATA}/$file"] = text.toByteArray() }
        levels.forEach { (path, json) -> out[path] = json.toByteArray() }
        SOUNDS.forEachIndexed { i, (_, file, preset) ->
            out["sounds/$file"] = Wav.encode(Sfxr.synth(Sfxr.preset(preset, seed = 100L + i), seed = 7), Sfxr.SAMPLE_RATE)
        }
        out["README.md"] = README.toByteArray()
        return out
    }

    private const val README = """# Grid tactics starter

Made from the PackFlow "Grid tactics" template.

- `code/index.html` starts the PackFlow 2D engine (`code/engine/`).
- `code/data/*.json` is written by the Game Designer (units, campaign, menus/HUD, sounds,
  saves). Edit it there, then tap Save; `data/game-data.js` is regenerated too.
- `levels/*.json` are edited in Asset Generator → Levels.
- `sounds/` holds the effects hooked up in Game Designer → Sounds.

Play it in PackFlow step 1 (Preview), then build it with the quick build or on GitHub.

Engine notes: rules live in modes. `engine/tactics.js` registers `turnbased`; set
`"mode": "realtime"` on a mission and register a `realtime` mode to add real-time play.
"""
}
