package com.example.core

import com.example.model.ClaudeApkConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedProjectFilesTest {
    @Test
    fun workflowKeepsApkOneDayAndBootstrapsKeystore() {
        val yaml = GeneratedProjectFiles.workflowYaml(ClaudeApkConfig(appName = "My Game!"))
        assertTrue(yaml.contains("retention-days: 1"))
        assertTrue(yaml.contains("keystore/debug.keystore"))
        assertTrue(yaml.contains("name: My-Game-debug-apk"))
        assertTrue(yaml.contains("\"HEAD:\${GITHUB_REF_NAME}\""))
    }

    @Test
    fun appBuildUsesApplicationId() {
        val gradle = GeneratedProjectFiles.appBuildGradle(ClaudeApkConfig(applicationId = "com.me.game"))
        assertTrue(gradle.contains("applicationId = \"com.me.game\""))
        assertTrue(GeneratedProjectFiles.ROOT_BUILD_GRADLE.contains(GeneratedProjectFiles.AGP_VERSION))
    }

    @Test
    fun readsKotlinPackage() {
        org.junit.Assert.assertEquals("com.claude.habitflow", GeneratedProjectFiles.kotlinPackageOf("// x\npackage com.claude.habitflow\n\nimport a.b"))
        org.junit.Assert.assertNull(GeneratedProjectFiles.kotlinPackageOf("fun main() {}"))
    }

    @Test
    fun manifestRegistersCrashCatcherAndEscapesName() {
        val xml = GeneratedProjectFiles.manifestXml("Tom & Jerry's", "a.b.MainActivity", "a.b.PackFlowApplication", hasIcon = true)
        assertTrue(xml.contains("android:name=\"a.b.PackFlowApplication\""))
        assertTrue(xml.contains("android:label=\"Tom &amp; Jerry\\'s\""))
        assertTrue(xml.contains("@mipmap/ic_launcher_round"))
    }
}
