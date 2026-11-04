package com.example.core

data class Faction(val id: String, val name: String, val color: Long)

data class UnitDef(
    val id: String,
    val name: String,
    val faction: String,
    val sprite: String? = null,     // project path, e.g. sprites/marines/general/sergeant/sergeant.png
    val health: Int = 10,
    val movement: Int = 4,
    val attack: Int = 3,
    val range: Int = 1,
    val defense: Int = 0,
    val cost: Int = 10
)

enum class WinCondition(val label: String, val needsValue: Boolean) {
    ELIMINATE_ALL("Destroy all enemies", false),
    SURVIVE_TURNS("Survive N turns", true),
    REACH_TILE("Reach a tile (x,y)", true),
    KILL_UNIT("Kill a unit type", true)
}

enum class LoseCondition(val label: String, val needsValue: Boolean) {
    ALL_UNITS_LOST("All your units are lost", false),
    TURN_LIMIT("Turn limit reached", true),
    UNIT_LOST("A unit type is lost", true)
}

data class Mission(
    val id: String,
    val name: String,
    val briefing: String = "",
    val level: String? = null,      // levels/<name>.json
    val playerFaction: String = "",
    val enemyFaction: String = "",
    val win: WinCondition = WinCondition.ELIMINATE_ALL,
    val winValue: String = "",      // turns, "x,y" or unit id
    val lose: LoseCondition = LoseCondition.ALL_UNITS_LOST,
    val loseValue: String = "",
    val next: String? = null        // id of the mission that follows on victory
)

data class Campaign(val name: String = "Campaign", val start: String? = null, val missions: List<Mission> = emptyList()) {
    /** Missions in play order following `next` from `start`, then any unlinked ones. */
    fun ordered(): List<Mission> {
        val byId = missions.associateBy { it.id }
        val out = ArrayList<Mission>()
        val seen = HashSet<String>()
        var cur = start?.let { byId[it] } ?: missions.firstOrNull()
        while (cur != null && seen.add(cur.id)) {
            out.add(cur)
            cur = cur.next?.let { byId[it] }
        }
        missions.filter { it.id !in seen }.forEach { out.add(it) }
        return out
    }
}

data class MenuSettings(
    val title: String = "My Game",
    val subtitle: String = "",
    val background: String? = null, // project image
    val showContinue: Boolean = true,
    val showSettings: Boolean = true,
    val showCredits: Boolean = false,
    val credits: String = "",
    val accentColor: Long = 0xFFF3C623
)

data class HudSettings(
    val showTurnCounter: Boolean = true,
    val showUnitPanel: Boolean = true,
    val showEndTurnButton: Boolean = true,
    val showObjective: Boolean = true,
    val position: String = "bottom", // or "top"
    val scale: Float = 1f
)

data class SaveSettings(
    val enabled: Boolean = true,
    val slots: Int = 3,
    val autosaveEachTurn: Boolean = true,
    val storageKey: String = "packflow-save"
)

/** Game actions that can play a sound. */
enum class SoundAction(val id: String, val label: String) {
    SELECT("select", "Unit selected"),
    MOVE("move", "Unit moves"),
    ATTACK("attack", "Attack"),
    HIT("hit", "Unit hit"),
    DEATH("death", "Unit destroyed"),
    TURN_START("turn_start", "Turn starts"),
    VICTORY("victory", "Victory"),
    DEFEAT("defeat", "Defeat"),
    BUTTON("button", "Menu button"),
    MUSIC_MENU("music_menu", "Menu music"),
    MUSIC_BATTLE("music_battle", "Battle music")
}

/**
 * Everything the Game Designer edits. Stored in the project as code/data/units.json,
 * campaign.json, ui.json, sounds.json and save.json (plus game-data.js, a bundle the engine
 * can load without fetch()).
 */
data class GameDesign(
    val factions: List<Faction> = DEFAULT_FACTIONS,
    val units: List<UnitDef> = DEFAULT_UNITS,
    val campaign: Campaign = Campaign(),
    val menu: MenuSettings = MenuSettings(),
    val hud: HudSettings = HudSettings(),
    /** action id -> project path of a sound (sounds/…, voice/…, music/…), or absent. */
    val sounds: Map<String, String> = emptyMap(),
    val save: SaveSettings = SaveSettings()
) {
    /** Problems worth fixing before building (empty means OK). */
    fun validate(existingFiles: Set<String> = emptySet()): List<String> {
        val issues = ArrayList<String>()
        val factionIds = factions.map { it.id }.toSet()
        units.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { issues.add("Two units share the ID '$it'.") }
        units.forEach { u ->
            if (u.id.isBlank()) issues.add("A unit has no ID.")
            if (u.faction !in factionIds) issues.add("${u.name}: faction '${u.faction}' doesn't exist.")
            if (u.health < 1) issues.add("${u.name}: health must be at least 1.")
            if (u.range < 1) issues.add("${u.name}: range must be at least 1.")
            if (u.sprite != null && existingFiles.isNotEmpty() && u.sprite !in existingFiles) issues.add("${u.name}: sprite ${u.sprite} is missing.")
        }
        val missionIds = campaign.missions.map { it.id }.toSet()
        campaign.missions.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { issues.add("Two missions share the ID '$it'.") }
        if (campaign.missions.isNotEmpty() && campaign.start != null && campaign.start !in missionIds) issues.add("The campaign start mission doesn't exist.")
        campaign.missions.forEach { m ->
            if (m.next != null && m.next !in missionIds) issues.add("${m.name}: next mission '${m.next}' doesn't exist.")
            if (m.level == null) issues.add("${m.name}: pick a level.")
            else if (existingFiles.isNotEmpty() && m.level !in existingFiles) issues.add("${m.name}: level ${m.level} is missing.")
            if (m.win.needsValue && m.winValue.isBlank()) issues.add("${m.name}: '${m.win.label}' needs a value.")
            if (m.win == WinCondition.SURVIVE_TURNS && m.winValue.toIntOrNull() == null) issues.add("${m.name}: turns must be a number.")
            if (m.win == WinCondition.REACH_TILE && !Regex("^\\d+\\s*,\\s*\\d+$").matches(m.winValue.trim())) issues.add("${m.name}: tile must look like 5,3.")
            if (m.lose == LoseCondition.TURN_LIMIT && m.loseValue.toIntOrNull() == null) issues.add("${m.name}: turn limit must be a number.")
            if (m.playerFaction.isNotBlank() && m.playerFaction !in factionIds) issues.add("${m.name}: player faction doesn't exist.")
        }
        // Following `next` from the start must end (a loop would never finish the campaign).
        val byId = campaign.missions.associateBy { it.id }
        val seen = HashSet<String>()
        var cur = campaign.start?.let { byId[it] } ?: campaign.missions.firstOrNull()
        while (cur != null) {
            if (!seen.add(cur.id)) { issues.add("The mission chain loops back to '${cur.name}'."); break }
            cur = cur.next?.let { byId[it] }
        }
        if (save.slots !in 1..10) issues.add("Save slots must be 1 to 10.")
        return issues
    }

    // ---- JSON ----

    fun unitsJson(): String = Json.stringify(
        linkedMapOf(
            "format" to "packflow-units", "version" to 1,
            "factions" to factions.map { linkedMapOf("id" to it.id, "name" to it.name, "color" to Level.colorHex(it.color)) },
            "units" to units.map {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "faction" to it.faction, "sprite" to it.sprite,
                    "health" to it.health, "movement" to it.movement, "attack" to it.attack,
                    "range" to it.range, "defense" to it.defense, "cost" to it.cost
                )
            }
        ),
        indent = 2
    )

    fun campaignJson(): String = Json.stringify(
        linkedMapOf(
            "format" to "packflow-campaign", "version" to 1, "name" to campaign.name, "start" to campaign.start,
            "missions" to campaign.missions.map {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "briefing" to it.briefing, "level" to it.level,
                    "playerFaction" to it.playerFaction, "enemyFaction" to it.enemyFaction,
                    "win" to linkedMapOf("type" to it.win.name, "value" to it.winValue),
                    "lose" to linkedMapOf("type" to it.lose.name, "value" to it.loseValue),
                    "next" to it.next
                )
            }
        ),
        indent = 2
    )

    fun uiJson(): String = Json.stringify(
        linkedMapOf(
            "format" to "packflow-ui", "version" to 1,
            "menu" to linkedMapOf(
                "title" to menu.title, "subtitle" to menu.subtitle, "background" to menu.background,
                "showContinue" to menu.showContinue, "showSettings" to menu.showSettings,
                "showCredits" to menu.showCredits, "credits" to menu.credits, "accentColor" to Level.colorHex(menu.accentColor)
            ),
            "hud" to linkedMapOf(
                "showTurnCounter" to hud.showTurnCounter, "showUnitPanel" to hud.showUnitPanel,
                "showEndTurnButton" to hud.showEndTurnButton, "showObjective" to hud.showObjective,
                "position" to hud.position, "scale" to hud.scale.toDouble()
            )
        ),
        indent = 2
    )

    fun soundsJson(): String = Json.stringify(linkedMapOf("format" to "packflow-sounds", "version" to 1, "hookups" to sounds.toSortedMap()), indent = 2)

    fun saveJson(): String = Json.stringify(
        linkedMapOf(
            "format" to "packflow-save-settings", "version" to 1, "enabled" to save.enabled, "slots" to save.slots,
            "autosaveEachTurn" to save.autosaveEachTurn, "storageKey" to save.storageKey
        ),
        indent = 2
    )

    /** Files to write under code/data/, path -> text. [levels] are bundled into game-data.js. */
    fun files(levels: Map<String, String> = emptyMap()): Map<String, String> {
        val parts = linkedMapOf(
            "units.json" to unitsJson(), "campaign.json" to campaignJson(), "ui.json" to uiJson(),
            "sounds.json" to soundsJson(), "save.json" to saveJson()
        )
        val bundle = StringBuilder("// Generated by PackFlow Game Designer. Lets the engine start without fetch().\nwindow.GAME_DATA = {\n")
        parts.forEach { (name, json) -> bundle.append("  ").append(Json.quote(name.removeSuffix(".json"))).append(": ").append(json.replace("\n", "\n  ")).append(",\n") }
        bundle.append("  \"levels\": {")
        levels.entries.forEachIndexed { i, (path, json) ->
            bundle.append(if (i == 0) "\n" else ",\n").append("    ").append(Json.quote(path)).append(": ").append(json.replace("\n", " "))
        }
        bundle.append("\n  }\n};\n")
        return parts + ("game-data.js" to bundle.toString())
    }

    companion object {
        val DEFAULT_FACTIONS = listOf(
            Faction("space_marines", "Space Marines", 0xFF2E5AAC),
            Faction("orks", "Orks", 0xFF3E8E2F),
            Faction("tyranids", "Tyranids", 0xFF7B3FA0)
        )
        val DEFAULT_UNITS = listOf(
            UnitDef("marine", "Tactical Marine", "space_marines", health = 12, movement = 4, attack = 4, range = 4, defense = 2, cost = 20),
            UnitDef("marine_sergeant", "Sergeant", "space_marines", health = 14, movement = 4, attack = 5, range = 3, defense = 2, cost = 30),
            UnitDef("ork_boy", "Ork Boy", "orks", health = 10, movement = 5, attack = 5, range = 1, defense = 0, cost = 8),
            UnitDef("ork_shoota", "Shoota Boy", "orks", health = 10, movement = 4, attack = 3, range = 3, defense = 0, cost = 10),
            UnitDef("termagant", "Termagant", "tyranids", health = 6, movement = 6, attack = 3, range = 2, defense = 0, cost = 6),
            UnitDef("genestealer", "Genestealer", "tyranids", health = 9, movement = 7, attack = 6, range = 1, defense = 1, cost = 16)
        )

        fun slugId(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "item" }

        fun uniqueId(base: String, taken: Collection<String>): String {
            val b = slugId(base)
            if (b !in taken) return b
            var i = 2
            while ("${b}_$i" in taken) i++
            return "${b}_$i"
        }

        /** Reads whatever files exist (missing ones keep their defaults). */
        fun fromFiles(read: (String) -> String?): GameDesign {
            var d = GameDesign()
            read("units.json")?.let { t ->
                runCatching {
                    val m = Json.parseObject(t)
                    val factions = m.objList("factions").map { Faction(it.str("id"), it.str("name"), IconSpec.parseColor(it.str("color")) ?: 0xFF808080) }
                    val units = m.objList("units").map {
                        UnitDef(
                            id = it.str("id"), name = it.str("name"), faction = it.str("faction"), sprite = it["sprite"] as? String,
                            health = it.int("health", 10), movement = it.int("movement", 4), attack = it.int("attack", 3),
                            range = it.int("range", 1), defense = it.int("defense", 0), cost = it.int("cost", 10)
                        )
                    }
                    d = d.copy(factions = factions.ifEmpty { DEFAULT_FACTIONS }, units = units)
                }
            }
            read("campaign.json")?.let { t ->
                runCatching {
                    val m = Json.parseObject(t)
                    d = d.copy(
                        campaign = Campaign(
                            name = m.str("name", "Campaign"), start = m["start"] as? String,
                            missions = m.objList("missions").map {
                                Mission(
                                    id = it.str("id"), name = it.str("name"), briefing = it.str("briefing"), level = it["level"] as? String,
                                    playerFaction = it.str("playerFaction"), enemyFaction = it.str("enemyFaction"),
                                    win = runCatching { WinCondition.valueOf(it.obj("win")?.str("type").orEmpty()) }.getOrDefault(WinCondition.ELIMINATE_ALL),
                                    winValue = it.obj("win")?.str("value").orEmpty(),
                                    lose = runCatching { LoseCondition.valueOf(it.obj("lose")?.str("type").orEmpty()) }.getOrDefault(LoseCondition.ALL_UNITS_LOST),
                                    loseValue = it.obj("lose")?.str("value").orEmpty(),
                                    next = it["next"] as? String
                                )
                            }
                        )
                    )
                }
            }
            read("ui.json")?.let { t ->
                runCatching {
                    val m = Json.parseObject(t)
                    val mm = m.obj("menu") ?: emptyMap()
                    val h = m.obj("hud") ?: emptyMap()
                    d = d.copy(
                        menu = MenuSettings(
                            title = mm.str("title", "My Game"), subtitle = mm.str("subtitle"), background = mm["background"] as? String,
                            showContinue = mm.bool("showContinue", true), showSettings = mm.bool("showSettings", true),
                            showCredits = mm.bool("showCredits", false), credits = mm.str("credits"),
                            accentColor = IconSpec.parseColor(mm.str("accentColor")) ?: 0xFFF3C623
                        ),
                        hud = HudSettings(
                            showTurnCounter = h.bool("showTurnCounter", true), showUnitPanel = h.bool("showUnitPanel", true),
                            showEndTurnButton = h.bool("showEndTurnButton", true), showObjective = h.bool("showObjective", true),
                            position = if (h.str("position") == "top") "top" else "bottom", scale = h.dbl("scale", 1.0).toFloat()
                        )
                    )
                }
            }
            read("sounds.json")?.let { t ->
                runCatching {
                    val hook = Json.parseObject(t).obj("hookups") ?: emptyMap()
                    d = d.copy(sounds = hook.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap())
                }
            }
            read("save.json")?.let { t ->
                runCatching {
                    val m = Json.parseObject(t)
                    d = d.copy(save = SaveSettings(m.bool("enabled", true), m.int("slots", 3).coerceIn(1, 10), m.bool("autosaveEachTurn", true), m.str("storageKey", "packflow-save")))
                }
            }
            return d
        }
    }
}
