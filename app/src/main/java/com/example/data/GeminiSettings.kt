package com.example.data

import android.content.Context
import com.example.core.GeminiApi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The user's own Gemini API key (typed into the app, encrypted with the Android Keystore,
 * same pattern as [GitHubSettings]) and the model IDs they picked. Shared by the Asset
 * Generator's Sprites/Tiles suite, Music and Voice.
 */
class GeminiSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val hasKey: Boolean get() = prefs.contains(KEY)

    fun key(): String? = prefs.getString(KEY, null)?.let { SecretCipher.decrypt(ALIAS, it) }

    fun saveKey(key: String) {
        val k = key.trim()
        if (k.isEmpty()) clearKey() else prefs.edit().putString(KEY, SecretCipher.encrypt(ALIAS, k)).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY).apply()
    }

    var ttsModel: String
        get() = prefs.getString("tts_model", GeminiApi.DEFAULT_TTS_MODEL) ?: GeminiApi.DEFAULT_TTS_MODEL
        set(v) { if (GeminiApi.isValidModel(v)) prefs.edit().putString("tts_model", v).apply() }

    var musicModel: String
        get() = prefs.getString("music_model", GeminiApi.DEFAULT_MUSIC_MODEL) ?: GeminiApi.DEFAULT_MUSIC_MODEL
        set(v) { if (GeminiApi.isValidModel(v)) prefs.edit().putString("music_model", v).apply() }

    companion object {
        private const val PREFS = "gemini_settings"
        private const val KEY = "api_key_enc"
        private const val ALIAS = "packflow_gemini_key"
    }
}

/** Blocking Gemini generateContent call (run on Dispatchers.IO). The key goes only in the header. */
object GeminiClient {
    fun generate(key: String, model: String, body: String): String {
        val conn = (URL(GeminiApi.endpoint(model)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000 // music can take a while
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException(GeminiApi.errorMessage(code, text))
            return text
        } finally {
            conn.disconnect()
        }
    }
}
