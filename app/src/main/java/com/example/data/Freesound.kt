package com.example.data

import android.content.Context
import com.example.core.FreesoundApi
import com.example.core.OnlinePage
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The user's own Freesound API key, encrypted with the Android Keystore (same pattern as GitHubSettings). */
class FreesoundSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("freesound_settings", Context.MODE_PRIVATE)

    val hasKey: Boolean get() = prefs.contains(KEY)

    fun key(): String? = prefs.getString(KEY, null)?.let { SecretCipher.decrypt(ALIAS, it) }

    fun saveKey(key: String) {
        val k = key.trim()
        if (k.isEmpty()) clearKey() else prefs.edit().putString(KEY, SecretCipher.encrypt(ALIAS, k)).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "api_key_enc"
        private const val ALIAS = "packflow_freesound_key"
    }
}

/** Blocking Freesound calls (run on Dispatchers.IO). The key only goes to freesound.org in a header. */
object FreesoundClient {
    fun search(key: String, url: String): OnlinePage {
        require(url.startsWith(FreesoundApi.BASE)) { "Not a Freesound URL" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Token $key")
            setRequestProperty("User-Agent", "PackFlow-Studio-Android")
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException(FreesoundApi.errorMessage(code, body))
            return FreesoundApi.parse(body)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads a preview (public CDN URL, no key) to [dest]. */
    fun download(url: String, dest: File, maxBytes: Long = 20L * 1024 * 1024): File {
        require(url.startsWith("https://")) { "Only https downloads" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "PackFlow-Studio-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Download failed (HTTP ${conn.responseCode})")
            dest.parentFile?.mkdirs()
            var total = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("File is larger than 20 MB")
                        out.write(buf, 0, n)
                    }
                }
            }
            return dest
        } catch (t: Throwable) {
            dest.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }
}
