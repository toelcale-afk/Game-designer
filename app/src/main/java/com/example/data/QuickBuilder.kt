package com.example.data

import android.content.Context
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.android.apksig.ApkSigner
import com.example.core.B64
import com.example.core.QuickBuildSpec
import com.example.core.SelfSignedCert
import com.example.core.ShellApk
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * The app's own APK signing key for quick builds. Generated once on the phone; the private
 * key is stored in private storage encrypted by an Android Keystore key (see SecretCipher).
 * Every quick-built app is signed with it, so rebuilding a game installs as an update.
 */
object SigningKeyStore {
    private const val DIR = "quickbuild"
    private const val KEY_FILE = "signing_key.enc"
    private const val CERT_FILE = "signing_cert.der"
    private const val ALIAS = "packflow_quickbuild_signing"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun exists(context: Context): Boolean =
        File(dir(context), KEY_FILE).exists() && File(dir(context), CERT_FILE).exists()

    @Synchronized
    fun getOrCreate(context: Context): Pair<PrivateKey, X509Certificate> {
        val keyFile = File(dir(context), KEY_FILE)
        val certFile = File(dir(context), CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            val plain = SecretCipher.decrypt(ALIAS, keyFile.readText())
            if (plain != null) {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(B64.decode(plain)))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream()) as X509Certificate
                return key to cert
            }
            ErrorLog.error("Quick build signing key could not be decrypted; creating a new one")
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = SelfSignedCert.create(kp, commonName = "PackFlow Quick Build")
        keyFile.writeText(SecretCipher.encrypt(ALIAS, B64.encode(kp.private.encoded)))
        certFile.writeBytes(cert.encoded)
        ErrorLog.info("Created quick build signing key ${fingerprint(cert).take(23)}…")
        return kp.private to cert
    }

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    fun currentFingerprint(context: Context): String? = runCatching {
        val f = File(dir(context), CERT_FILE)
        if (!f.exists()) null
        else fingerprint(CertificateFactory.getInstance("X.509").generateCertificate(f.inputStream()) as X509Certificate)
    }.getOrNull()
}

/**
 * Offline quick build for HTML/JS apps: prebuilt shell APK (assets/shell/webshell.apk,
 * added by CI) + the game's web files and icon -> renamed, zip-aligned, signed APK.
 */
object QuickBuilder {
    const val SHELL_ASSET = "shell/webshell.apk"

    fun isShellBundled(context: Context): Boolean =
        runCatching { context.assets.open(SHELL_ASSET).use { true } }.getOrDefault(false)

    /** Blocking; call on Dispatchers.IO. Returns the signed APK. */
    fun build(context: Context, spec: QuickBuildSpec, onProgress: (String) -> Unit = {}): File {
        spec.validationError()?.let { throw IllegalArgumentException(it) }
        val work = File(context.cacheDir, "quickbuild").apply { deleteRecursively(); mkdirs() }
        onProgress("Unpacking the app shell…")
        val shell = File(work, "shell.apk")
        context.assets.open(SHELL_ASSET).use { input -> shell.outputStream().use { input.copyTo(it) } }

        onProgress("Adding ${spec.webFiles.size} web file(s), icon and app name…")
        val unsigned = File(work, "unsigned.apk")
        val report = ShellApk.patch(shell, unsigned, spec)
        ErrorLog.info("Quick build: ${spec.packageName} patched (${report.replacedStrings} strings, ${report.replacedIcons} icons, ${report.webFiles} files)")

        onProgress("Signing…")
        val (key, cert) = SigningKeyStore.getOrCreate(context)
        val outDir = File(context.filesDir, "quickbuild/out").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }
        val signed = File(outDir, "${spec.packageName}-${spec.versionName}.apk")
        sign(unsigned, signed, key, cert)
        work.deleteRecursively()
        ErrorLog.info("Quick build ready: ${signed.name} (${signed.length()} bytes)")
        return signed
    }

    /** APK Signature Scheme v2 + v3 (enough for minSdk 24), aligned by apksig. */
    fun sign(input: File, output: File, key: PrivateKey, cert: X509Certificate) {
        val signer = ApkSigner.SignerConfig.Builder("packflow", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(24)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .setCreatedBy("PackFlow Studio")
            .build()
            .sign()
    }
}
