package com.example.core

import java.net.URLEncoder

/** Licenses Freesound uses, from most to least permissive. */
enum class SoundLicense(val label: String, val filterName: String, val needsCredit: Boolean, val nonCommercial: Boolean) {
    CC0("CC0 (public domain)", "Creative Commons 0", needsCredit = false, nonCommercial = false),
    BY("CC BY (credit required)", "Attribution", needsCredit = true, nonCommercial = false),
    BY_NC("CC BY-NC (credit, no commercial use)", "Attribution NonCommercial", needsCredit = true, nonCommercial = true),
    SAMPLING_PLUS("Sampling+", "Sampling+", needsCredit = true, nonCommercial = false),
    UNKNOWN("Unknown license", "", needsCredit = true, nonCommercial = true);

    companion object {
        /** Freesound returns license URLs (or names); map them to a kind. */
        fun fromApi(value: String): SoundLicense {
            val v = value.lowercase()
            return when {
                "publicdomain/zero" in v || "creative commons 0" in v || v == "cc0" -> CC0
                "sampling+" in v || "sampling-plus" in v -> SAMPLING_PLUS
                "/by-nc" in v || "noncommercial" in v -> BY_NC
                "/by/" in v || v == "attribution" -> BY
                else -> UNKNOWN
            }
        }
    }
}

data class OnlineSound(
    val id: Long,
    val name: String,
    val tags: List<String>,
    val license: SoundLicense,
    val licenseUrl: String,
    val username: String,
    val duration: Double,
    val previewUrl: String?,
    val pageUrl: String
) {
    /** Credit line for CREDITS.md (always written; required for everything except CC0). */
    val creditLine: String
        get() = "\"$name\" by $username ($pageUrl), license: ${license.label}${if (licenseUrl.isNotBlank()) " $licenseUrl" else ""}"

    /** Project file name, e.g. "123456_battle_cry.mp3" (extension follows the preview format). */
    val fileName: String
        get() {
            val ext = previewUrl?.substringAfterLast('.')?.lowercase()?.takeIf { it == "mp3" || it == "ogg" } ?: "mp3"
            return "${id}_${GameDesign.slugId(name.substringBeforeLast('.')).take(40)}.$ext"
        }
}

data class OnlinePage(val count: Int, val results: List<OnlineSound>, val hasNext: Boolean)

/** A ready-made voice search. */
data class VoiceCategory(val label: String, val query: String)

/**
 * Freesound (freesound.org) API v2: free online database of sounds, including thousands of
 * voice clips. Needs the user's own free API key; previews (MP3) are downloaded without OAuth.
 */
object FreesoundApi {
    const val BASE = "https://freesound.org/apiv2"
    const val APPLY_URL = "https://freesound.org/apiv2/apply/"
    private const val FIELDS = "id,name,tags,license,username,duration,previews,url"

    val VOICE_CATEGORIES = listOf(
        VoiceCategory("Battle cries", "battle cry shout"),
        VoiceCategory("Grunts & pain", "grunt pain voice"),
        VoiceCategory("Death screams", "death scream"),
        VoiceCategory("Commands", "voice command soldier"),
        VoiceCategory("Yes / no / ok", "voice affirmative"),
        VoiceCategory("Laughs", "evil laugh"),
        VoiceCategory("Monster voices", "monster voice growl"),
        VoiceCategory("Robot voices", "robot voice"),
        VoiceCategory("Announcer", "announcer voice"),
        VoiceCategory("Crowd & cheers", "crowd cheer"),
        VoiceCategory("Whispers", "whisper"),
        VoiceCategory("Breathing", "breathing")
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The filter clause: allowed licenses and a duration range. */
    fun filter(licenses: Set<SoundLicense>, maxSeconds: Double): String {
        val lic = licenses.filter { it.filterName.isNotBlank() }.ifEmpty { listOf(SoundLicense.CC0) }
            .joinToString(" OR ") { "\"${it.filterName}\"" }
        return "license:($lic) duration:[0 TO ${maxSeconds.coerceIn(0.5, 600.0)}]"
    }

    /** Text search URL (the key goes in the Authorization header, not the URL). */
    fun searchUrl(query: String, licenses: Set<SoundLicense>, maxSeconds: Double, page: Int = 1, pageSize: Int = 30): String =
        "$BASE/search/text/?query=${enc(query.trim())}&filter=${enc(filter(licenses, maxSeconds))}" +
            "&fields=$FIELDS&page=${page.coerceAtLeast(1)}&page_size=${pageSize.coerceIn(1, 150)}&sort=score"

    fun parse(json: String): OnlinePage {
        val m = Json.parseObject(json)
        m["detail"]?.let { throw IllegalStateException(it.toString()) }
        val results = m.objList("results").mapNotNull { r ->
            val id = r.long("id", -1)
            if (id < 0) return@mapNotNull null
            val previews = r.obj("previews") ?: emptyMap()
            val licenseRaw = r.str("license")
            OnlineSound(
                id = id,
                name = r.str("name", "sound $id"),
                tags = r.list("tags").mapNotNull { it as? String },
                license = SoundLicense.fromApi(licenseRaw),
                licenseUrl = licenseRaw.takeIf { it.startsWith("http") }.orEmpty(),
                username = r.str("username", "unknown"),
                duration = r.dbl("duration"),
                previewUrl = listOf("preview-hq-mp3", "preview-lq-mp3", "preview-hq-ogg", "preview-lq-ogg")
                    .firstNotNullOfOrNull { k -> (previews[k] as? String)?.takeIf { it.startsWith("https://") } },
                pageUrl = r.str("url", "https://freesound.org/s/$id/")
            )
        }
        return OnlinePage(m.int("count", results.size), results, m["next"] != null)
    }

    fun errorMessage(code: Int, body: String): String {
        val detail = runCatching { Json.parseObject(body).str("detail") }.getOrDefault("")
        val hint = when (code) {
            401, 403 -> "The Freesound API key was rejected. Check it on freesound.org (Your API credentials)."
            404 -> "Freesound's search address changed; please report this."
            429 -> "Freesound's daily request limit was reached. Try again later."
            else -> ""
        }
        return "Freesound error $code${if (detail.isNotBlank()) ": $detail" else ""}. $hint".trim()
    }

    /** Adds [sound]'s credit to an existing CREDITS.md text (no duplicates). */
    fun withCredit(existing: String, sound: OnlineSound): String {
        val header = "# Sound credits\n\nVoice clips from Freesound (https://freesound.org). Keep this file with your game and show the credits for CC BY sounds.\n\n"
        val base = existing.ifBlank { header }
        val marker = "[freesound #${sound.id}]"
        return if (marker in base) base else base.trimEnd() + "\n- $marker ${sound.creditLine}\n"
    }
}
