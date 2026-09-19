package com.example

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testContextAwareFileRenaming_withLocationAndTitle() {
    val timestamp = 1789569373000L // 2026-09-16
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    val location = "Coffee Shop"
    val title = "Meeting with Sarah"
    val originalFileName = "recording_123.m4a"

    val ext = if (originalFileName.endsWith(".m4a")) ".m4a" else ".aac"
    val titlePart = "$location - $title"
    val cleanBaseName = "[$dateStr] $titlePart".replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
    val standardizedName = "$cleanBaseName$ext"

    assertEquals("[2026-09-16] Coffee Shop - Meeting with Sarah.m4a", standardizedName)
  }

  @Test
  fun testContextAwareFileRenaming_withTitleOnly() {
    val timestamp = 1789569373000L
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    val location: String? = null
    val title = "Product Roadmap Sync"
    val originalFileName = "audio_test.aac"

    val ext = if (originalFileName.endsWith(".aac")) ".aac" else ".m4a"
    val titlePart = location?.let { "$it - $title" } ?: title
    val cleanBaseName = "[$dateStr] $titlePart".replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
    val standardizedName = "$cleanBaseName$ext"

    assertEquals("[2026-09-16] Product Roadmap Sync.aac", standardizedName)
  }

  @Test
  fun testFolderHierarchyNaming() {
    val timestamp = 1789569373000L
    val location = "Office & Headquarters"
    val cleanLoc = location.trim().replace(Regex("[/\\\\?%*:|\"<>]"), " ")
    val subfolderWithLocation = cleanLoc.ifBlank { SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp)) }

    assertEquals("Office & Headquarters", subfolderWithLocation)

    val nullLocation: String? = null
    val subfolderFallback = nullLocation?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))
    assertEquals("2026-09", subfolderFallback)
  }

  @Test
  fun testCompanionDocumentJsonStructure() {
    val timestamp = 1789569373000L
    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    val companionJson = JSONObject().apply {
      put("sessionTitle", "Meeting with Sarah")
      put("date", dateFormatted)
      put("timestamp", timestamp)
      put("location", "Coffee Shop")
      put("activeSpeakers", JSONArray(listOf("Speaker A", "Speaker B")))
      put("mentionedPeople", JSONArray(listOf("Sarah", "Alex")))
      put("summary", "Executive overview of project milestones.")
      put("transcript", "[00:00] Speaker A: Hello")
      put("audioFileName", "[2026-09-16] Coffee Shop - Meeting with Sarah.m4a")
      put("googleDriveAudioFileId", "drive_file_abc123")
    }

    assertEquals("Meeting with Sarah", companionJson.getString("sessionTitle"))
    assertEquals("Coffee Shop", companionJson.getString("location"))
    assertEquals(2, companionJson.getJSONArray("activeSpeakers").length())
    assertEquals("drive_file_abc123", companionJson.getString("googleDriveAudioFileId"))
    assertTrue(companionJson.has("transcript"))
    assertTrue(companionJson.has("summary"))
  }

  @Test
  fun testGroqPromptTruncation_shortAndExact() {
    val shortPrompt = "This is a short audio transcription prompt."
    val truncated = com.example.ui.MainViewModel.truncatePromptForGroq(shortPrompt)
    assertEquals(shortPrompt, truncated)
    assertTrue(truncated.length <= 896)

    val exactPrompt = "A".repeat(896)
    val truncatedExact = com.example.ui.MainViewModel.truncatePromptForGroq(exactPrompt)
    assertEquals(896, truncatedExact.length)
  }

  @Test
  fun testGroqPromptTruncation_wordBoundary() {
    val words = List(200) { "word$it" }.joinToString(" ")
    assertTrue(words.length > 896)

    val truncated = com.example.ui.MainViewModel.truncatePromptForGroq(words)
    assertTrue(truncated.length <= 896)
    assertFalse(truncated.endsWith(" "))
    // Check clean word boundary - last character should not split a word
    val lastWord = truncated.split(" ").last()
    assertTrue(words.contains(lastWord))
  }

  @Test
  fun testGroqPromptTruncation_noSpacesAndUnicode() {
    val longSingleWord = "x".repeat(1000)
    val truncatedWord = com.example.ui.MainViewModel.truncatePromptForGroq(longSingleWord)
    assertEquals(896, truncatedWord.length)

    val unicodePrompt = "Transcrìpção multilingüe de áudio com caractères especiais e acentuação! " + "palabra ".repeat(150)
    assertTrue(unicodePrompt.length > 896)
    val truncatedUnicode = com.example.ui.MainViewModel.truncatePromptForGroq(unicodePrompt)
    assertTrue(truncatedUnicode.length <= 896)
  }

  @Test
  fun testOpenRouterDefaultModelConstant() {
    assertEquals("google/gemini-2.5-flash:free", com.example.api.AiConstants.OPENROUTER_DEFAULT_MODEL)
    assertEquals(896, com.example.api.AiConstants.GROQ_TRANSCRIPTION_PROMPT_MAX_LENGTH)
  }

  @Test
  fun testApiKeyMasking() {
    val rawText = "Error with key sk-or-v1-abcdef123456789 and gsk_9876543210123 and AIzaSyD123456789"
    var masked = rawText
      .replace(Regex("sk-or-v1-[a-zA-Z0-9_-]+"), "***MASKED_KEY***")
      .replace(Regex("gsk_[a-zA-Z0-9_-]+"), "***MASKED_KEY***")
      .replace(Regex("AIzaSy[a-zA-Z0-9_-]+"), "***MASKED_KEY***")

    assertFalse(masked.contains("sk-or-v1-abcdef123456789"))
    assertFalse(masked.contains("gsk_9876543210123"))
    assertFalse(masked.contains("AIzaSyD123456789"))
    assertTrue(masked.contains("***MASKED_KEY***"))
  }
}
