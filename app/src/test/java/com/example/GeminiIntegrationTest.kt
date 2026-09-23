package com.example

import com.example.api.AiConstants
import com.example.api.Content
import com.example.api.GeminiStreamParser
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.ThinkingConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiIntegrationTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testGeminiModelConstantsAreValid() {
        assertEquals("gemini-3.5-flash", AiConstants.GEMINI_DEFAULT_MODEL)
        assertEquals("gemini-3.5-flash", AiConstants.GEMINI_PRO_MODEL)
    }

    @Test
    fun testGeminiRequestMoshiSerializationFormat() {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Please transcribe this audio."),
                        Part(inlineData = InlineData(mimeType = "audio/aac", data = "QUJDREVGR0g="))
                    ),
                    role = "user"
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json",
                thinkingConfig = ThinkingConfig(thinkingLevel = "LOW")
            )
        )

        val adapter = moshi.adapter(GenerateContentRequest::class.java)
        val json = adapter.toJson(request)

        assertNotNull(json)
        assertTrue("JSON must contain contents", json.contains("\"contents\""))
        assertTrue("JSON must contain parts", json.contains("\"parts\""))
        assertTrue("JSON must contain inline_data with snake_case", json.contains("\"inline_data\""))
        assertTrue("JSON must contain mime_type with snake_case", json.contains("\"mime_type\""))
        assertTrue("JSON must contain generation_config with snake_case", json.contains("\"generation_config\""))
        assertTrue("JSON must contain response_mime_type with snake_case", json.contains("\"response_mime_type\""))
        assertTrue("JSON must contain thinking_config with snake_case", json.contains("\"thinking_config\""))
        assertTrue("JSON must contain thinking_level with snake_case", json.contains("\"thinking_level\""))
    }

    @Test
    fun testSseJsonPayloadParsing_validChunk() {
        val validJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "[00:00] Speaker A: Hello world" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val text = GeminiStreamParser.parseSseJsonPayload(validJson, "test_key")
        assertEquals("[00:00] Speaker A: Hello world", text)
    }

    @Test
    fun testSseJsonPayloadParsing_doneAndBlankSignal() {
        assertNull(GeminiStreamParser.parseSseJsonPayload("[DONE]"))
        assertNull(GeminiStreamParser.parseSseJsonPayload(""))
        assertNull(GeminiStreamParser.parseSseJsonPayload("   "))
    }

    @Test(expected = IllegalStateException::class)
    fun testSseJsonPayloadParsing_errorInStream() {
        val errorJson = """
            {
              "error": {
                "code": 400,
                "message": "Invalid API key AIzaSyD123456789 provided",
                "status": "INVALID_ARGUMENT"
              }
            }
        """.trimIndent()

        GeminiStreamParser.parseSseJsonPayload(errorJson, "AIzaSyD123456789")
    }

    @Test(expected = IllegalStateException::class)
    fun testSseJsonPayloadParsing_finishReasonSafety() {
        val safetyJson = """
            {
              "candidates": [
                {
                  "finishReason": "SAFETY",
                  "content": {
                    "parts": [ { "text": "Unsafe content" } ]
                  }
                }
              ]
            }
        """.trimIndent()

        GeminiStreamParser.parseSseJsonPayload(safetyJson)
    }

    @Test
    fun testSseJsonPayloadParsing_maskingKeysInError() {
        val leakKey = "AIzaSyKey1234567890123"
        val errorJson = """
            {
              "error": {
                "message": "Quota exceeded for key $leakKey",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
        """.trimIndent()

        try {
            GeminiStreamParser.parseSseJsonPayload(errorJson, leakKey)
        } catch (e: IllegalStateException) {
            assertFalse(e.message!!.contains(leakKey))
            assertTrue(e.message!!.contains("***MASKED_KEY***"))
        }
    }

    @Test
    fun testApiKeyMaskingUtility() {
        val sampleText = "Errors: Google AIzaSyTestKey12345678, OpenRouter sk-or-v1-abcdef123, Groq gsk_123456"
        val masked = GeminiStreamParser.maskApiKey(sampleText)

        assertFalse(masked.contains("AIzaSyTestKey12345678"))
        assertFalse(masked.contains("sk-or-v1-abcdef123"))
        assertFalse(masked.contains("gsk_123456"))
        assertTrue(masked.contains("***MASKED_KEY***"))
    }
}
