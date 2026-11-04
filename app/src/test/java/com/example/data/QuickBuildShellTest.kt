package com.example.data

import com.android.apksig.ApkVerifier
import com.example.core.Axml
import com.example.core.Density
import com.example.core.QuickBuildSpec
import com.example.core.SelfSignedCert
import com.example.core.ShellApk
import java.io.File
import java.security.KeyPairGenerator
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end check of the offline quick build against the REAL shell APK that CI builds
 * (skipped when the shell isn't bundled). Writes build/quickbuild-test.apk so the workflow
 * can also run aapt2 and apksigner on it.
 */
class QuickBuildShellTest {

    private fun shellApk(): File? = listOf(
        File("src/main/assets/${QuickBuilder.SHELL_ASSET}"),
        File("app/src/main/assets/${QuickBuilder.SHELL_ASSET}")
    ).firstOrNull { it.exists() }

    @Test
    fun patchedShellIsAValidSignedApk() {
        val shell = shellApk()
        assumeTrue("Shell APK not bundled (CI builds it first)", shell != null)
        val work = File("build/quickbuild-test").apply { deleteRecursively(); mkdirs() }

        // Reuse the shell's own xxhdpi icon bytes as the "new" icon, but mark them.
        val iconBytes = ZipFile(shell!!).use { z ->
            z.entries().toList().first { it.name.startsWith("res/") && it.name.endsWith(".png") }
                .let { z.getInputStream(it).readBytes() }
        }
        val spec = QuickBuildSpec(
            appName = "CI Test Game",
            packageName = "com.packflow.citest",
            versionName = "2.5",
            versionCode = 25,
            webFiles = mapOf("index.html" to "<h1>CI</h1>".toByteArray(), "data/level1.json" to "{}".toByteArray()),
            icons = Density.entries.associateWith { iconBytes }
        )
        val unsigned = File(work, "unsigned.apk")
        val report = ShellApk.patch(shell, unsigned, spec)
        assertTrue("placeholders replaced", report.replacedStrings >= 2)

        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = SelfSignedCert.create(kp, "CI")
        val signed = File("build/quickbuild-test.apk")
        QuickBuilder.sign(unsigned, signed, kp.private, cert)

        val result = ApkVerifier.Builder(signed).build().verify()
        assertTrue("Signature errors: ${result.allErrors}", result.isVerified)
        assertTrue(result.isVerifiedUsingV2Scheme)

        ZipFile(signed).use { z ->
            val ax = Axml.parse(z.getInputStream(z.getEntry("AndroidManifest.xml")).readBytes())
            assertEquals("com.packflow.citest", ax.attributeString("manifest", "package"))
            assertEquals("CI Test Game", ax.attributeString("application", "label"))
            assertEquals("2.5", ax.attributeString("manifest", "versionName"))
            assertTrue(z.getEntry("assets/www/data/level1.json") != null)
            assertTrue(z.getEntry("classes.dex") != null)
        }
    }
}
