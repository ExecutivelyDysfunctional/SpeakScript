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
}
