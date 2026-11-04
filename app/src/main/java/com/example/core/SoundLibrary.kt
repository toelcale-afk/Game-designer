package com.example.core

import kotlin.random.Random

/** One sound in the built-in library. It's a recipe: synthesized on demand, never stored in the APK. */
data class LibrarySound(
    val id: String,
    val name: String,
    val category: String,
    val tags: List<String>,
    val params: SfxParams
) {
    /** File name used when the sound is added to a project. */
    val fileName: String get() = "$id.wav"
}

/**
 * Built-in sound database: hundreds of retro game sounds made from sfxr recipes with fixed
 * seeds, so every phone gets the same sounds and nothing has to be downloaded.
 */
object SoundLibrary {

    private class Recipe(val category: String, val kind: String, val tags: List<String>, val variants: Int, val make: (Random) -> SfxParams)

    private fun Random.f(x: Float) = nextFloat() * x
    private fun Random.between(a: Float, b: Float) = a + nextFloat() * (b - a)
    private fun preset(p: SfxPreset, r: Random) = Sfxr.preset(p, r.nextLong())

    private val recipes = listOf(
        // ---- Weapons
        Recipe("Weapons", "Laser", listOf("shoot", "gun", "sci-fi", "beam"), 20) { r -> preset(SfxPreset.LASER, r) },
        Recipe("Weapons", "Blaster", listOf("shoot", "gun", "pistol", "sci-fi"), 14) { r ->
            preset(SfxPreset.LASER, r).copy(freqLimit = 0f, wave = SfxWave.SQUARE, baseFreq = r.between(0.35f, 0.55f), freqRamp = r.between(-0.45f, -0.25f), sustain = r.between(0.05f, 0.12f), decay = r.between(0.12f, 0.25f), punch = r.between(0.3f, 0.6f))
        },
        Recipe("Weapons", "Plasma", listOf("shoot", "energy", "sci-fi", "charge"), 12) { r ->
            preset(SfxPreset.LASER, r).copy(freqLimit = 0f, wave = SfxWave.SAWTOOTH, baseFreq = r.between(0.25f, 0.45f), vibStrength = r.between(0.3f, 0.6f), vibSpeed = r.between(0.4f, 0.8f), phaserOffset = r.between(0.1f, 0.3f), phaserRamp = -r.f(0.2f), sustain = r.between(0.15f, 0.3f), decay = r.between(0.25f, 0.45f))
        },
        Recipe("Weapons", "Bolt shot", listOf("shoot", "gun", "rifle", "bang", "heavy"), 14) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.3f, 0.5f), freqRamp = r.between(-0.3f, -0.1f), sustain = r.between(0.03f, 0.08f), punch = r.between(0.5f, 0.8f), decay = r.between(0.15f, 0.3f), lpfFreq = r.between(0.6f, 0.95f))
        },
        Recipe("Weapons", "Shotgun", listOf("shoot", "gun", "blast", "heavy"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.15f, 0.3f), freqRamp = -r.f(0.15f), sustain = r.between(0.08f, 0.15f), punch = r.between(0.6f, 0.9f), decay = r.between(0.3f, 0.5f), lpfFreq = r.between(0.4f, 0.7f))
        },
        Recipe("Weapons", "Zap", listOf("electric", "shock", "sci-fi"), 12) { r ->
            SfxParams(wave = SfxWave.SAWTOOTH, baseFreq = r.between(0.6f, 0.9f), freqRamp = r.between(-0.7f, -0.4f), sustain = r.f(0.08f), decay = r.between(0.08f, 0.2f), hpfFreq = r.f(0.2f), repeatSpeed = r.between(0.5f, 0.75f))
        },
        Recipe("Weapons", "Sword swing", listOf("melee", "whoosh", "blade", "chainsword"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.4f, 0.7f), freqRamp = r.between(0.1f, 0.3f), attack = r.between(0.05f, 0.12f), sustain = r.f(0.05f), decay = r.between(0.1f, 0.2f), lpfFreq = r.between(0.35f, 0.6f), hpfFreq = r.between(0.05f, 0.2f))
        },
        // ---- Explosions
        Recipe("Explosions", "Explosion", listOf("boom", "blast", "destroy"), 20) { r -> preset(SfxPreset.EXPLOSION, r) },
        Recipe("Explosions", "Big explosion", listOf("boom", "blast", "artillery", "heavy"), 12) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.05f, 0.15f), freqRamp = -r.f(0.1f), sustain = r.between(0.3f, 0.45f), punch = r.between(0.5f, 0.8f), decay = r.between(0.5f, 0.7f), repeatSpeed = if (r.nextBoolean()) r.between(0.2f, 0.4f) else 0f, lpfFreq = r.between(0.5f, 0.8f))
        },
        Recipe("Explosions", "Distant explosion", listOf("boom", "far", "rumble"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.05f, 0.12f), sustain = r.between(0.2f, 0.35f), punch = r.f(0.3f), decay = r.between(0.5f, 0.7f), lpfFreq = r.between(0.12f, 0.25f), volume = 0.6f)
        },
        Recipe("Explosions", "Grenade", listOf("boom", "pop", "frag"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.25f, 0.4f), freqRamp = r.between(-0.3f, -0.15f), sustain = r.between(0.1f, 0.2f), punch = r.between(0.6f, 0.9f), decay = r.between(0.25f, 0.4f))
        },
        // ---- Impacts
        Recipe("Impacts", "Hit", listOf("damage", "hurt", "impact"), 20) { r -> preset(SfxPreset.HIT, r) },
        Recipe("Impacts", "Punch", listOf("melee", "thud", "impact"), 12) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.1f, 0.2f), freqRamp = -r.f(0.2f), sustain = r.f(0.04f), punch = r.between(0.6f, 0.9f), decay = r.between(0.08f, 0.15f), lpfFreq = r.between(0.25f, 0.45f))
        },
        Recipe("Impacts", "Metal clang", listOf("armor", "ricochet", "impact", "metal"), 12) { r ->
            SfxParams(wave = SfxWave.SQUARE, duty = r.f(0.4f), baseFreq = r.between(0.55f, 0.8f), freqRamp = -r.f(0.05f), sustain = r.f(0.05f), punch = r.between(0.4f, 0.7f), decay = r.between(0.25f, 0.45f), hpfFreq = r.between(0.1f, 0.25f), arpMod = r.between(0.3f, 0.6f), arpSpeed = r.between(0.6f, 0.8f))
        },
        Recipe("Impacts", "Crunch", listOf("break", "bone", "impact"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.2f, 0.35f), sustain = r.f(0.05f), punch = r.between(0.4f, 0.7f), decay = r.between(0.1f, 0.2f), repeatSpeed = r.between(0.6f, 0.8f), hpfFreq = r.f(0.1f))
        },
        // ---- UI
        Recipe("UI", "Blip", listOf("menu", "click", "button"), 16) { r -> preset(SfxPreset.BLIP, r) },
        Recipe("UI", "Confirm", listOf("menu", "ok", "accept", "button"), 12) { r ->
            SfxParams(wave = SfxWave.SQUARE, duty = r.f(0.5f), baseFreq = r.between(0.4f, 0.55f), sustain = r.between(0.05f, 0.1f), decay = r.between(0.1f, 0.2f), arpMod = r.between(0.3f, 0.5f), arpSpeed = r.between(0.55f, 0.7f))
        },
        Recipe("UI", "Error", listOf("menu", "denied", "wrong", "button"), 10) { r ->
            SfxParams(wave = SfxWave.SQUARE, duty = r.between(0.2f, 0.5f), baseFreq = r.between(0.22f, 0.3f), sustain = r.between(0.1f, 0.15f), decay = r.between(0.1f, 0.2f), arpMod = -r.between(0.3f, 0.5f), arpSpeed = r.between(0.5f, 0.65f))
        },
        Recipe("UI", "Back", listOf("menu", "cancel", "button"), 8) { r ->
            SfxParams(wave = SfxWave.SINE, baseFreq = r.between(0.35f, 0.45f), freqRamp = -r.between(0.15f, 0.3f), sustain = r.f(0.05f), decay = r.between(0.08f, 0.15f))
        },
        Recipe("UI", "Typewriter tick", listOf("text", "dialog", "typing"), 8) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.6f, 0.9f), sustain = r.f(0.02f), decay = r.between(0.08f, 0.12f), hpfFreq = r.between(0.3f, 0.5f), volume = 0.4f)
        },
        // ---- Pickups
        Recipe("Pickups", "Coin", listOf("collect", "gold", "money", "reward"), 16) { r -> preset(SfxPreset.PICKUP, r) },
        Recipe("Pickups", "Power-up", listOf("upgrade", "buff", "level up"), 14) { r -> preset(SfxPreset.POWERUP, r) },
        Recipe("Pickups", "Heal", listOf("health", "repair", "restore"), 10) { r ->
            SfxParams(wave = SfxWave.SINE, baseFreq = r.between(0.3f, 0.4f), freqRamp = r.between(0.1f, 0.2f), sustain = r.between(0.15f, 0.25f), decay = r.between(0.2f, 0.35f), vibStrength = r.between(0.1f, 0.25f), vibSpeed = r.between(0.4f, 0.6f), repeatSpeed = r.between(0.45f, 0.6f))
        },
        Recipe("Pickups", "Ammo", listOf("reload", "collect", "click"), 10) { r ->
            SfxParams(wave = if (r.nextBoolean()) SfxWave.NOISE else SfxWave.SQUARE, baseFreq = r.between(0.45f, 0.7f), sustain = r.f(0.04f), punch = r.between(0.3f, 0.6f), decay = r.between(0.1f, 0.16f), repeatSpeed = r.between(0.65f, 0.8f), hpfFreq = r.f(0.2f))
        },
        // ---- Movement
        Recipe("Movement", "Footstep", listOf("walk", "step", "move"), 16) { r -> preset(SfxPreset.FOOTSTEP, r) },
        Recipe("Movement", "Heavy step", listOf("walk", "armor", "walker", "stomp"), 10) { r ->
            preset(SfxPreset.FOOTSTEP, r).copy(baseFreq = r.between(0.04f, 0.09f), decay = r.between(0.15f, 0.25f), punch = r.between(0.5f, 0.8f), lpfFreq = r.between(0.15f, 0.3f))
        },
        Recipe("Movement", "Jump", listOf("hop", "leap"), 12) { r -> preset(SfxPreset.JUMP, r) },
        Recipe("Movement", "Dash", listOf("whoosh", "fast", "dodge"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.2f, 0.35f), freqRamp = r.between(0.2f, 0.4f), attack = r.between(0.02f, 0.06f), sustain = r.f(0.05f), decay = r.between(0.1f, 0.2f), lpfFreq = r.between(0.4f, 0.7f))
        },
        Recipe("Movement", "Land", listOf("fall", "thud", "drop"), 8) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.06f, 0.12f), sustain = r.f(0.03f), punch = r.between(0.4f, 0.7f), decay = r.between(0.12f, 0.2f), lpfFreq = r.between(0.2f, 0.35f))
        },
        // ---- Creatures
        Recipe("Creatures", "Growl", listOf("monster", "beast", "ork", "angry"), 12) { r ->
            SfxParams(wave = SfxWave.SAWTOOTH, baseFreq = r.between(0.08f, 0.16f), freqRamp = r.between(-0.05f, 0.05f), vibStrength = r.between(0.3f, 0.6f), vibSpeed = r.between(0.5f, 0.8f), sustain = r.between(0.3f, 0.45f), decay = r.between(0.2f, 0.35f), lpfFreq = r.between(0.3f, 0.5f), lpfResonance = r.f(0.4f))
        },
        Recipe("Creatures", "Screech", listOf("monster", "alien", "tyranid", "scream"), 12) { r ->
            SfxParams(wave = SfxWave.SAWTOOTH, baseFreq = r.between(0.55f, 0.75f), freqRamp = -r.between(0.05f, 0.2f), vibStrength = r.between(0.2f, 0.5f), vibSpeed = r.between(0.6f, 0.9f), attack = r.f(0.05f), sustain = r.between(0.2f, 0.35f), decay = r.between(0.15f, 0.3f), hpfFreq = r.f(0.15f))
        },
        Recipe("Creatures", "Chitter", listOf("insect", "alien", "swarm", "bug"), 10) { r ->
            SfxParams(wave = SfxWave.SQUARE, duty = r.f(0.5f), baseFreq = r.between(0.45f, 0.7f), sustain = r.between(0.15f, 0.3f), decay = r.between(0.1f, 0.2f), repeatSpeed = r.between(0.7f, 0.85f), arpMod = r.between(-0.4f, 0.4f), arpSpeed = r.between(0.7f, 0.9f))
        },
        Recipe("Creatures", "Roar", listOf("monster", "beast", "big", "angry"), 10) { r ->
            SfxParams(wave = SfxWave.NOISE, baseFreq = r.between(0.06f, 0.14f), vibStrength = r.between(0.2f, 0.5f), vibSpeed = r.between(0.3f, 0.6f), attack = r.between(0.05f, 0.12f), sustain = r.between(0.35f, 0.5f), decay = r.between(0.3f, 0.45f), lpfFreq = r.between(0.25f, 0.45f), punch = r.f(0.3f))
        },
        // ---- Sci-fi
        Recipe("Sci-fi", "Teleport", listOf("warp", "portal", "magic", "psychic"), 12) { r ->
            SfxParams(wave = SfxWave.SINE, baseFreq = r.between(0.2f, 0.35f), freqRamp = r.between(0.2f, 0.35f), vibStrength = r.between(0.3f, 0.6f), vibSpeed = r.between(0.6f, 0.9f), sustain = r.between(0.25f, 0.4f), decay = r.between(0.2f, 0.35f), phaserOffset = r.between(0.2f, 0.4f), phaserRamp = r.f(0.2f))
        },
        Recipe("Sci-fi", "Shield", listOf("force field", "energy", "block", "protect"), 10) { r ->
            SfxParams(wave = SfxWave.SINE, baseFreq = r.between(0.25f, 0.4f), vibStrength = r.between(0.4f, 0.7f), vibSpeed = r.between(0.7f, 0.95f), attack = r.between(0.02f, 0.08f), sustain = r.between(0.3f, 0.4f), decay = r.between(0.2f, 0.3f), phaserOffset = r.between(0.2f, 0.4f))
        },
        Recipe("Sci-fi", "Alarm", listOf("warning", "siren", "alert"), 10) { r ->
            SfxParams(wave = SfxWave.SQUARE, duty = r.f(0.5f), baseFreq = r.between(0.4f, 0.55f), sustain = r.between(0.35f, 0.5f), decay = r.between(0.1f, 0.2f), repeatSpeed = r.between(0.45f, 0.6f), arpMod = r.between(0.3f, 0.5f), arpSpeed = r.between(0.55f, 0.7f))
        },
        Recipe("Sci-fi", "Computer beep", listOf("terminal", "data", "cogitator", "machine"), 14) { r ->
            SfxParams(wave = SfxWave.SINE, baseFreq = r.between(0.45f, 0.8f), sustain = r.between(0.06f, 0.12f), decay = r.between(0.05f, 0.12f), repeatSpeed = if (r.nextBoolean()) r.between(0.6f, 0.8f) else 0f, arpMod = r.between(-0.5f, 0.5f), arpSpeed = r.between(0.6f, 0.85f))
        },
        Recipe("Sci-fi", "Engine hum", listOf("vehicle", "tank", "machine", "loop"), 8) { r ->
            SfxParams(wave = SfxWave.SAWTOOTH, baseFreq = r.between(0.05f, 0.1f), vibStrength = r.between(0.05f, 0.2f), vibSpeed = r.between(0.4f, 0.7f), attack = r.between(0.1f, 0.2f), sustain = r.between(0.5f, 0.6f), decay = r.between(0.2f, 0.3f), lpfFreq = r.between(0.2f, 0.35f))
        },
        Recipe("Sci-fi", "Power down", listOf("shutdown", "fail", "machine"), 8) { r ->
            SfxParams(wave = if (r.nextBoolean()) SfxWave.SINE else SfxWave.SAWTOOTH, baseFreq = r.between(0.4f, 0.6f), freqRamp = -r.between(0.2f, 0.35f), sustain = r.between(0.3f, 0.45f), decay = r.between(0.2f, 0.35f), lpfFreq = r.between(0.4f, 0.8f))
        }
    )

    /** Every sound, in category order. Deterministic: the same on every phone and every run. */
    val all: List<LibrarySound> by lazy {
        recipes.flatMap { rec ->
            (1..rec.variants).map { i ->
                val slug = GameDesign.slugId(rec.kind)
                val seed = (rec.category + "/" + rec.kind).hashCode().toLong() * 1000 + i
                LibrarySound(
                    id = String.format(java.util.Locale.ROOT, "%s_%02d", slug, i),
                    name = String.format(java.util.Locale.ROOT, "%s %02d", rec.kind, i),
                    category = rec.category,
                    tags = listOf(rec.kind.lowercase()) + rec.tags,
                    params = rec.make(Random(seed))
                )
            }
        }
    }

    val categories: List<String> get() = recipes.map { it.category }.distinct()

    fun byId(id: String): LibrarySound? = all.firstOrNull { it.id == id }

    /** Matches every word of [query] against the name and tags. */
    fun search(query: String, category: String? = null, onlyIds: Set<String>? = null): List<LibrarySound> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter { s ->
            (category == null || s.category == category) &&
                (onlyIds == null || s.id in onlyIds) &&
                words.all { w -> s.name.lowercase().contains(w) || s.tags.any { it.contains(w) } || s.category.lowercase().contains(w) }
        }
    }

    fun render(sound: LibrarySound): ShortArray = Sfxr.synth(sound.params)
}
