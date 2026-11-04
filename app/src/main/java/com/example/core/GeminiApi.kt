package com.example.core

/** Output of a Gemini generateContent call: inline media plus any text (e.g. song lyrics). */
data class GeminiMedia(val mimeType: String, val data: ByteArray, val text: String)

/**
 * Request bodies and response parsing for the Gemini API (generateContent).
 * Model names change often, so the defaults here are only defaults: the user can type any
 * model ID in the app.
 */
object GeminiApi {
    const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    // Defaults checked against Google's docs (Nov 2026). Editable in the app.
    const val DEFAULT_TTS_MODEL = "gemini-3.8-flash-tts"
    val TTS_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
    const val DEFAULT_MUSIC_MODEL = "lyria-3-clip-preview"
    val MUSIC_MODELS = listOf("lyria-3-clip-preview", "lyria-3.5")

    /** Prebuilt voices offered in the voice picker (any name can be typed). */
    val VOICES = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr",
        "Algenib", "Gacrux", "Achernar", "Enceladus", "Iapetus", "Umbriel", "Sadaltager", "Schedar"
    )

    private val MODEL_ID = Regex("^[A-Za-z0-9._-]{2,80}$")

    fun isValidModel(id: String) = MODEL_ID.matches(id)

    fun endpoint(model: String): String {
        require(isValidModel(model)) { "'$model' isn't a valid model ID" }
        return "$BASE/$model:generateContent"
    }

    /** Single-speaker TTS. [direction] is optional style guidance ("Say gruffly:"). */
    fun ttsBody(text: String, voice: String, direction: String = ""): String {
        val spoken = if (direction.isBlank()) text else "${direction.trim().trimEnd(':')}: $text"
        return Json.stringify(
            mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to spoken)))),
                "generationConfig" to mapOf(
                    "responseModalities" to listOf("AUDIO"),
                    "speechConfig" to mapOf("voiceConfig" to mapOf("prebuiltVoiceConfig" to mapOf("voiceName" to voice)))
                )
            )
        )
    }

    /**
     * Music request bodies to try in order. The plain prompt is the documented form; the second
     * asks for audio explicitly, for model versions that need it.
     */
    fun musicBodies(prompt: String): List<String> = listOf(
        Json.stringify(mapOf("contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))))),
        Json.stringify(
            mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))),
                "generationConfig" to mapOf("responseModalities" to listOf("AUDIO", "TEXT"))
            )
        )
    )

    /** All inline media and text from the first candidate. Throws with the API's message on errors. */
    fun parseMedia(json: String): List<GeminiMedia> {
        val m = Json.parseObject(json)
        m.obj("error")?.let { throw IllegalStateException(it.str("message", "Gemini API error")) }
        val candidate = m.objList("candidates").firstOrNull()
        val parts = candidate?.obj("content")?.objList("parts") ?: emptyList()
        val text = parts.mapNotNull { it["text"] as? String }.joinToString("\n").trim()
        val media = parts.mapNotNull { p ->
            val inline = p.obj("inlineData") ?: p.obj("inline_data") ?: return@mapNotNull null
            val mime = inline.str("mimeType", inline.str("mime_type", "application/octet-stream"))
            GeminiMedia(mime, B64.decode(inline.str("data")), text)
        }
        if (media.isEmpty()) {
            val reason = candidate?.str("finishReason").orEmpty()
            val blocked = m.obj("promptFeedback")?.str("blockReason").orEmpty()
            throw IllegalStateException(
                when {
                    blocked.isNotBlank() -> "The prompt was blocked ($blocked). Try different wording."
                    reason.isNotBlank() && reason != "STOP" -> "No audio returned (finish reason: $reason)."
                    text.isNotBlank() -> "The model answered with text only: ${text.take(200)}"
                    else -> "The model returned no audio."
                }
            )
        }
        return media
    }

    fun errorMessage(code: Int, body: String): String {
        val msg = runCatching { Json.parseObject(body).obj("error")?.str("message") }.getOrNull().orEmpty()
        val hint = when (code) {
            400 -> if (msg.contains("API key", true)) "Check the Gemini API key." else ""
            401, 403 -> "The key was rejected or this model isn't enabled for it."
            404 -> "Model not found. Model names change often; pick or type a current one."
            429 -> "Quota or rate limit reached. Free-tier keys may not include this model."
            else -> ""
        }
        return "Gemini error $code${if (msg.isNotBlank()) ": $msg" else ""}. $hint".trim()
    }

    /** Turns model output into a file: (extension, bytes). Raw PCM gets a WAV header. */
    fun toAudioFile(media: GeminiMedia): Pair<String, ByteArray> {
        val mime = media.mimeType.lowercase()
        return when {
            mime.contains("l16") || mime.contains("pcm") -> "wav" to Wav.fromPcm(media.data, Wav.rateFromMime(mime), 1, 16)
            mime.contains("wav") -> "wav" to media.data
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3" to media.data
            mime.contains("ogg") -> "ogg" to media.data
            mime.contains("aac") || mime.contains("mp4") -> "m4a" to media.data
            else -> "bin" to media.data
        }
    }
}
