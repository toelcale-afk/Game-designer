package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.core.B64
import com.example.core.GitHubTarget
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (API keys, tokens) with an AES-256-GCM key that lives in the
 * Android Keystore. The key never leaves the Keystore, and only ciphertext goes to
 * SharedPreferences. Secrets are typed into the app by the user; nothing is bundled.
 *
 * Pattern for any new secret: `SecretCipher.encrypt(alias, plain)` -> store the string,
 * `SecretCipher.decrypt(alias, stored)` -> plain text or null if the key was lost.
 */
object SecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(alias: String): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Returns base64(iv + ciphertext). */
    fun encrypt(alias: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return B64.encode(iv + ct)
    }

    /** Returns the plain text, or null if it can't be decrypted (e.g. app data restored to a new phone). */
    fun decrypt(alias: String, stored: String): String? = runCatching {
        val all = B64.decode(stored)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES))
        String(cipher.doFinal(all, IV_BYTES, all.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()
}

/**
 * GitHub build settings: owner/repo/branch in plain prefs, the personal access token
 * encrypted with [SecretCipher]. The token is never logged, exported or zipped.
 */
class GitHubSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var target: GitHubTarget
        get() = GitHubTarget(
            owner = prefs.getString(KEY_OWNER, "") ?: "",
            repo = prefs.getString(KEY_REPO, "") ?: "",
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main"
        )
        set(value) {
            prefs.edit()
                .putString(KEY_OWNER, value.owner.trim())
                .putString(KEY_REPO, value.repo.trim())
                .putString(KEY_BRANCH, value.branch.trim())
                .apply()
        }

    val hasToken: Boolean get() = prefs.contains(KEY_TOKEN)

    fun saveToken(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            clearToken()
            return
        }
        prefs.edit().putString(KEY_TOKEN, SecretCipher.encrypt(KEY_ALIAS, clean)).apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.let { SecretCipher.decrypt(KEY_ALIAS, it) }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS = "github_settings"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_TOKEN = "token_enc"
        private const val KEY_ALIAS = "packflow_github_token"
    }
}
