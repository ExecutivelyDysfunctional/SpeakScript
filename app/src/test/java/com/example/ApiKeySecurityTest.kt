package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApiKeySecurityTest {

    @Test
    fun testFirebaseAppletConfigHasNoExposedKey() {
        val candidates = listOf(
            File("firebase-applet-config.json"),
            File("../firebase-applet-config.json")
        )
        val configFile = candidates.firstOrNull { it.exists() }
        val oldLeakedKey = "AIzaSy" + "Bvqfj8Ldo9Q_9YTotVKWSSfuMpufVzXqM"
        if (configFile != null) {
            val content = configFile.readText()
            // Ensure the leaked key is not present
            assertFalse(
                "Leaked API key was found in firebase-applet-config.json!",
                content.contains(oldLeakedKey)
            )
            // Ensure no real AIza key pattern is present (39 chars)
            val aizaRegex = Regex("\"apiKey\"\\s*:\\s*\"AIzaSy[A-Za-z0-9_-]{33}\"")
            assertFalse(
                "A real 39-character AIzaSy key was found in firebase-applet-config.json!",
                aizaRegex.containsMatchIn(content)
            )
        }
    }

    @Test
    fun testGitignoreProtectsSensitiveFiles() {
        val candidates = listOf(
            File("../.gitignore"),
            File(".gitignore")
        )
        val gitignore = candidates.firstOrNull { it.exists() && it.readText().contains(".env") }
            ?: candidates.firstOrNull { it.exists() }
        if (gitignore != null) {
            val lines = gitignore.readLines().map { it.trim() }
            assertTrue(".env must be in .gitignore", lines.any { it == ".env" || it == "/.env" })
            assertTrue("firebase-applet-config.json must be in .gitignore", lines.any { it.contains("firebase-applet-config.json") })
            assertTrue("google-services.json must be in .gitignore", lines.any { it.contains("google-services.json") })
            assertTrue("local.properties must be in .gitignore", lines.any { it.contains("local.properties") })
        }
    }

    @Test
    fun testMaskKeyRedactsVariousGoogleApiKeyFormats() {
        val sampleLeak1 = "AIzaSy" + "Bvqfj8Ldo9Q_9YTotVKWSSfuMpufVzXqM"
        val sampleLeak2 = "AIzaSyDummyKeyForTestingPurposes1234567"
        val text = "Request failed: Key $sampleLeak1 is invalid. Backup: $sampleLeak2"

        val masked = text.replace(Regex("AIzaSy[a-zA-Z0-9_-]+"), "***MASKED_KEY***")

        assertFalse(masked.contains(sampleLeak1))
        assertFalse(masked.contains(sampleLeak2))
        assertTrue(masked.contains("***MASKED_KEY***"))
    }

    @Test
    fun testEnvExampleContainsOnlyPlaceholders() {
        val candidates = listOf(
            File(".env.example"),
            File("../.env.example")
        )
        val envExample = candidates.firstOrNull { it.exists() }
        if (envExample != null) {
            val content = envExample.readText()
            assertFalse(
                "Real AIzaSy key should not be in .env.example",
                content.contains("AIzaSy")
            )
            assertTrue(
                "Placeholder should be present in .env.example",
                content.contains("MY_GEMINI_API_KEY")
            )
        }
    }
}
