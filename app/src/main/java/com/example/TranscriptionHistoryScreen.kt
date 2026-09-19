package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.db.TranscriptionRecord
import com.example.db.SpeakerProfile
import com.example.ui.SpeakerDetailDialog
import com.example.ui.UiState
import com.example.ui.formatDuration
import java.text.SimpleDateFormat
import java.util.*

/**
 * High-level unified journal model representing either a single standalone recording
 * or a connected multi-part recording session.
 */
sealed class JournalEntry {
    abstract val sortTimestamp: Long
    abstract val entryId: String

    data class Single(val record: TranscriptionRecord) : JournalEntry() {
        override val sortTimestamp: Long get() = record.timestamp
        override val entryId: String get() = record.id
    }

    data class SessionGroup(
        val sessionId: String,
        val sessionTitle: String,
        val timestamp: Long,
        val parts: List<TranscriptionRecord>,
        val masterSummary: String?,
        val totalDurationMs: Int,
        val modelName: String?
    ) : JournalEntry() {
        override val sortTimestamp: Long get() = timestamp
        override val entryId: String get() = "session_$sessionId"
    }
}

/**
 * Filter time buckets for dynamic journal history filtering.
 */
enum class DateBucketFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    PAST_7_DAYS("Past 7 Days"),
    THIS_MONTH("This Month")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionHistoryScreen(
    uiState: UiState,
    onUpdateCategory: (TranscriptionRecord, String) -> Unit,
    onUpdateSpeakerName: (TranscriptionRecord, String) -> Unit,
    onDeleteTranscriptions: (List<String>) -> Unit,
    onDeleteSession: (String) -> Unit,
    onExportJson: (TranscriptionRecord) -> Unit,
    onExportSessionJson: (sessionId: String, title: String, parts: List<TranscriptionRecord>) -> Unit,
    onExportAllJson: () -> Unit,
    onImportJson: () -> Unit,
    onSync: () -> Unit,
    defaultKeywords: List<String>,
    onOpenPlayback: (TranscriptionRecord) -> Unit,
    onSaveSpeaker: ((SpeakerProfile) -> Unit)? = null,
    onRemoveGoldenSample: ((String) -> Unit)? = null,
    audioPlayerContent: @Composable (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSpeakerFilter by remember { mutableStateOf<String?>(null) }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }
    var selectedDateBucketFilter by remember { mutableStateOf(DateBucketFilter.ALL) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var expandedEntryIds by remember { mutableStateOf(setOf<String>()) }
    var expandedPartIds by remember { mutableStateOf(setOf<String>()) }
    var sessionToDelete by remember { mutableStateOf<JournalEntry.SessionGroup?>(null) }
    var activeSpeakerForDialog by remember { mutableStateOf<String?>(null) }

    val activeFilterCount = (if (selectedSpeakerFilter != null) 1 else 0) +
            (if (selectedLocationFilter != null) 1 else 0) +
            (if (selectedDateBucketFilter != DateBucketFilter.ALL) 1 else 0) +
            (if (searchQuery.isNotBlank()) 1 else 0)

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val todayStr = dayFormat.format(Date())
    val yesterdayStr = dayFormat.format(Date(System.currentTimeMillis() - 86400000L))

    // Group raw flat records into JournalEntries (Single or SessionGroup)
    val journalEntries = remember(uiState.history) {
        val nonSession = mutableListOf<JournalEntry.Single>()
        val sessionMap = mutableMapOf<String, MutableList<TranscriptionRecord>>()

        for (record in uiState.history) {
            if (record.sessionId.isNullOrBlank()) {
                nonSession.add(JournalEntry.Single(record))
            } else {
                sessionMap.getOrPut(record.sessionId) { mutableListOf() }.add(record)
            }
        }

        val sessionGroups = sessionMap.map { (sessionId, partList) ->
            val sortedParts = partList.sortedBy { it.partIndex ?: 0 }
            val firstPart = sortedParts.firstOrNull()
            val title = sortedParts.mapNotNull { it.sessionTitle }.firstOrNull()
                ?.ifBlank { null }
                ?: "Recording Session"
            val masterSummary = sortedParts.mapNotNull { it.summary }.firstOrNull { it.isNotBlank() }
            val model = sortedParts.mapNotNull { it.modelName }.firstOrNull { it.isNotBlank() } ?: "Gemini 3.6 Flash"
            val totalDuration = sortedParts.sumOf { it.partDurationMs ?: 0 }
            val time = firstPart?.timestamp ?: System.currentTimeMillis()

            JournalEntry.SessionGroup(
                sessionId = sessionId,
                sessionTitle = title,
                timestamp = time,
                parts = sortedParts,
                masterSummary = masterSummary,
                totalDurationMs = totalDuration,
                modelName = model
            )
        }

        (nonSession + sessionGroups).sortedByDescending { it.sortTimestamp }
    }

    // Dynamic extraction of distinct speakers and locations for filter options
    val availableSpeakers = remember(uiState.history, uiState.speakers) {
        val list = mutableListOf<String>()
        uiState.speakers.forEach { list.add(it.name) }
        uiState.history.forEach { rec ->
            rec.activeSpeakersCsv?.split(",")?.forEach { s ->
                val trimmed = s.trim()
                if (trimmed.isNotBlank()) list.add(trimmed)
            }
            rec.speakerLabels?.split(",")?.forEach { s ->
                val trimmed = s.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("Speaker ", ignoreCase = true)) list.add(trimmed)
            }
        }
        list.distinct().sorted()
    }

    val availableLocations = remember(uiState.history) {
        val list = mutableListOf<String>()
        uiState.history.forEach { rec ->
            val loc = rec.locationName?.trim()
            if (!loc.isNullOrBlank()) list.add(loc)
        }
        list.distinct().sorted()
    }

    // Filter entries based on search query, speaker, location, and date bucket
    val filteredEntries = journalEntries.filter { entry ->
        // 1. Search Query Filter
        val query = searchQuery.lowercase().trim()
        val matchesQuery = if (query.isBlank()) true else {
            when (entry) {
                is JournalEntry.Single -> {
                    val dateStr = dateFormat.format(Date(entry.record.timestamp)).lowercase()
                    dateStr.contains(query) ||
                            entry.record.transcription.lowercase().contains(query) ||
                            (entry.record.summary?.lowercase()?.contains(query) == true) ||
                            (entry.record.speakerLabels?.lowercase()?.contains(query) == true) ||
                            (entry.record.locationName?.lowercase()?.contains(query) == true) ||
                            (entry.record.activeSpeakersCsv?.lowercase()?.contains(query) == true) ||
                            (entry.record.mentionedPeopleCsv?.lowercase()?.contains(query) == true)
                }
                is JournalEntry.SessionGroup -> {
                    val dateStr = dateFormat.format(Date(entry.timestamp)).lowercase()
                    entry.sessionTitle.lowercase().contains(query) ||
                            dateStr.contains(query) ||
                            (entry.masterSummary?.lowercase()?.contains(query) == true) ||
                            entry.parts.any { p ->
                                p.transcription.lowercase().contains(query) ||
                                        (p.speakerLabels?.lowercase()?.contains(query) == true) ||
                                        (p.locationName?.lowercase()?.contains(query) == true) ||
                                        (p.activeSpeakersCsv?.lowercase()?.contains(query) == true) ||
                                        (p.mentionedPeopleCsv?.lowercase()?.contains(query) == true)
                            }
                }
            }
        }
        if (!matchesQuery) return@filter false

        // 2. Specific Speaker Filter
        if (selectedSpeakerFilter != null) {
            val spk = selectedSpeakerFilter!!.lowercase().trim()
            val matchesSpeaker = when (entry) {
                is JournalEntry.Single -> {
                    (entry.record.activeSpeakersCsv?.lowercase()?.contains(spk) == true) ||
                            (entry.record.speakerLabels?.lowercase()?.contains(spk) == true) ||
                            (entry.record.transcription.lowercase().contains(spk))
                }
                is JournalEntry.SessionGroup -> {
                    entry.parts.any { p ->
                        (p.activeSpeakersCsv?.lowercase()?.contains(spk) == true) ||
                                (p.speakerLabels?.lowercase()?.contains(spk) == true) ||
                                (p.transcription.lowercase().contains(spk))
                    }
                }
            }
            if (!matchesSpeaker) return@filter false
        }

        // 3. Location Filter
        if (selectedLocationFilter != null) {
            val loc = selectedLocationFilter!!.lowercase().trim()
            val matchesLocation = when (entry) {
                is JournalEntry.Single -> {
                    entry.record.locationName?.lowercase()?.contains(loc) == true
                }
                is JournalEntry.SessionGroup -> {
                    entry.parts.any { p -> p.locationName?.lowercase()?.contains(loc) == true }
                }
            }
            if (!matchesLocation) return@filter false
        }

        // 4. Date Range / Time Bucket Filter
        when (selectedDateBucketFilter) {
            DateBucketFilter.ALL -> true
            DateBucketFilter.TODAY -> {
                val recordDay = dayFormat.format(Date(entry.sortTimestamp))
                recordDay == todayStr
            }
            DateBucketFilter.PAST_7_DAYS -> {
                System.currentTimeMillis() - entry.sortTimestamp <= 7 * 86400000L
            }
            DateBucketFilter.THIS_MONTH -> {
                val nowCal = Calendar.getInstance()
                val entryCal = Calendar.getInstance().apply { timeInMillis = entry.sortTimestamp }
                nowCal.get(Calendar.YEAR) == entryCal.get(Calendar.YEAR) &&
                        nowCal.get(Calendar.MONTH) == entryCal.get(Calendar.MONTH)
            }
        }
    }

    // Group filtered items by Date Header
    val groupedEntries = filteredEntries.groupBy { entry ->
        val recordDay = dayFormat.format(Date(entry.sortTimestamp))
        when {
            recordDay == todayStr -> "Today"
            recordDay == yesterdayStr -> "Yesterday"
            System.currentTimeMillis() - entry.sortTimestamp < 7 * 86400000L -> "This Week"
            else -> "Older"
        }
    }.toSortedMap(compareBy { header ->
        when (header) {
            "Today" -> 0
            "Yesterday" -> 1
            "This Week" -> 2
            else -> 3
        }
    })

    // Confirmation dialog for deleting a session
    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Entire Session?") },
            text = {
                Text(
                    "Are you sure you want to delete '${s.sessionTitle}' and all ${s.parts.size} linked audio parts? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(s.sessionId)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription Journal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                actions = {
                    IconButton(
                        onClick = onImportJson,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import JSON Data")
                    }
                    IconButton(
                        onClick = onExportAllJson,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export All Data (JSON)")
                    }
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud")
                    }
                    if (uiState.history.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedIds = emptySet()
                            },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(if (isSelectionMode) "Done" else "Select")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionMode && selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = {
                                onDeleteTranscriptions(selectedIds.toList())
                                selectedIds = emptySet()
                                isSelectionMode = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Selected")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Dynamic Journal Filtering Bar (Above Search Bar)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear Filters badge button if active
                if (activeFilterCount > 0) {
                    item(key = "clear_filters") {
                        FilterChip(
                            selected = true,
                            onClick = {
                                selectedSpeakerFilter = null
                                selectedLocationFilter = null
                                selectedDateBucketFilter = DateBucketFilter.ALL
                                searchQuery = ""
                            },
                            label = {
                                Text("Clear ($activeFilterCount)", fontWeight = FontWeight.Bold)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear all filters",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }

                // Date Range / Time Bucket Filter
                item(key = "date_filter") {
                    var showDateMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = selectedDateBucketFilter != DateBucketFilter.ALL,
                            onClick = { showDateMenu = true },
                            label = { Text(selectedDateBucketFilter.label) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Date filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (selectedDateBucketFilter != DateBucketFilter.ALL) {
                                    IconButton(
                                        onClick = { selectedDateBucketFilter = DateBucketFilter.ALL },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear date filter",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showDateMenu,
                            onDismissRequest = { showDateMenu = false }
                        ) {
                            DateBucketFilter.values().forEach { bucket ->
                                DropdownMenuItem(
                                    text = { Text(bucket.label) },
                                    onClick = {
                                        selectedDateBucketFilter = bucket
                                        showDateMenu = false
                                    },
                                    leadingIcon = if (selectedDateBucketFilter == bucket) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Specific Speaker Filter
                item(key = "speaker_filter") {
                    var showSpeakerMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = selectedSpeakerFilter != null,
                            onClick = { showSpeakerMenu = true },
                            label = {
                                Text(selectedSpeakerFilter ?: "Speaker")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Speaker filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (selectedSpeakerFilter != null) {
                                    IconButton(
                                        onClick = { selectedSpeakerFilter = null },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear speaker filter",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showSpeakerMenu,
                            onDismissRequest = { showSpeakerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Speakers") },
                                onClick = {
                                    selectedSpeakerFilter = null
                                    showSpeakerMenu = false
                                }
                            )
                            availableSpeakers.forEach { spkName ->
                                DropdownMenuItem(
                                    text = { Text(spkName) },
                                    onClick = {
                                        selectedSpeakerFilter = spkName
                                        showSpeakerMenu = false
                                    },
                                    leadingIcon = if (selectedSpeakerFilter == spkName) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Location Filter
                item(key = "location_filter") {
                    var showLocationMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = selectedLocationFilter != null,
                            onClick = { showLocationMenu = true },
                            label = {
                                Text(selectedLocationFilter ?: "Location")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = "Location filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (selectedLocationFilter != null) {
                                    IconButton(
                                        onClick = { selectedLocationFilter = null },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear location filter",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showLocationMenu,
                            onDismissRequest = { showLocationMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Locations") },
                                onClick = {
                                    selectedLocationFilter = null
                                    showLocationMenu = false
                                }
                            )
                            availableLocations.forEach { locName ->
                                DropdownMenuItem(
                                    text = { Text(locName) },
                                    onClick = {
                                        selectedLocationFilter = locName
                                        showLocationMenu = false
                                    },
                                    leadingIcon = if (selectedLocationFilter == locName) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Quick Suggestion Chips for top speakers
                if (selectedSpeakerFilter == null && availableSpeakers.isNotEmpty()) {
                    items(availableSpeakers.take(3), key = { "quick_spk_$it" }) { spk ->
                        SuggestionChip(
                            onClick = { selectedSpeakerFilter = spk },
                            label = { Text("👤 $spk") },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                // Quick Suggestion Chips for top locations
                if (selectedLocationFilter == null && availableLocations.isNotEmpty()) {
                    items(availableLocations.take(2), key = { "quick_loc_$it" }) { loc ->
                        SuggestionChip(
                            onClick = { selectedLocationFilter = loc },
                            label = { Text("📍 $loc") },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            // Search Bar Optimized for Mobile Thumb Reach
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search sessions, summaries, speakers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            )

            // Data Portability quick action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportAllJson,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export JSON", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matches for '$searchQuery'" else "No journal entries found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    groupedEntries.forEach { (headerTitle, entries) ->
                        item(key = "header_$headerTitle") {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(entries, key = { it.entryId }) { entry ->
                            when (entry) {
                                is JournalEntry.SessionGroup -> {
                                    val isExpanded = expandedEntryIds.contains(entry.entryId)
                                    val partIds = remember(entry) { entry.parts.map { it.id }.toSet() }
                                    val isAllSessionSelected = partIds.isNotEmpty() && selectedIds.containsAll(partIds)

                                    MasterSessionCard(
                                        session = entry,
                                        isExpanded = isExpanded,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isAllSessionSelected,
                                        dateFormat = dateFormat,
                                        defaultKeywords = defaultKeywords,
                                        expandedPartIds = expandedPartIds,
                                        onToggleExpand = {
                                            expandedEntryIds = if (isExpanded) {
                                                expandedEntryIds - entry.entryId
                                            } else {
                                                expandedEntryIds + entry.entryId
                                            }
                                        },
                                        onToggleSelect = {
                                            selectedIds = if (isAllSessionSelected) {
                                                selectedIds - partIds
                                            } else {
                                                selectedIds + partIds
                                            }
                                        },
                                        onTogglePartExpand = { partId ->
                                            expandedPartIds = if (expandedPartIds.contains(partId)) {
                                                expandedPartIds - partId
                                            } else {
                                                expandedPartIds + partId
                                            }
                                        },
                                        onPlaySession = {
                                            val firstPart = entry.parts.firstOrNull()
                                            if (firstPart != null) {
                                                onOpenPlayback(firstPart)
                                            }
                                        },
                                        onPlayPart = { part -> onOpenPlayback(part) },
                                        onExportSession = {
                                            onExportSessionJson(entry.sessionId, entry.sessionTitle, entry.parts)
                                        },
                                        onDeleteSession = { sessionToDelete = entry },
                                        speakers = uiState.speakers,
                                        onSpeakerClick = { activeSpeakerForDialog = it },
                                        audioPlayerContent = audioPlayerContent
                                    )
                                }
                                is JournalEntry.Single -> {
                                    val record = entry.record
                                    val isSelected = selectedIds.contains(record.id)
                                    val isExpanded = expandedEntryIds.contains(record.id)

                                    SingleRecordingCard(
                                        record = record,
                                        isExpanded = isExpanded,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        dateFormat = dateFormat,
                                        defaultKeywords = defaultKeywords,
                                        onToggleExpand = {
                                            expandedEntryIds = if (isExpanded) {
                                                expandedEntryIds - record.id
                                            } else {
                                                expandedEntryIds + record.id
                                            }
                                        },
                                        onToggleSelect = {
                                            selectedIds = if (isSelected) {
                                                selectedIds - record.id
                                            } else {
                                                selectedIds + record.id
                                            }
                                        },
                                        onUpdateSpeakerName = { name -> onUpdateSpeakerName(record, name) },
                                        onOpenPlayback = { onOpenPlayback(record) },
                                        onExportJson = { onExportJson(record) },
                                        speakers = uiState.speakers,
                                        onSpeakerClick = { activeSpeakerForDialog = it },
                                        audioPlayerContent = audioPlayerContent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Speaker Detail Dialog when any speaker chip is tapped
    if (activeSpeakerForDialog != null) {
        val spkName = activeSpeakerForDialog!!
        val matchedProfile = uiState.speakers.find { it.name.equals(spkName, ignoreCase = true) }
        SpeakerDetailDialog(
            speaker = matchedProfile,
            unrecognizedName = if (matchedProfile == null) spkName else null,
            onDismiss = { activeSpeakerForDialog = null },
            onSaveNewSpeaker = { newProfile ->
                onSaveSpeaker?.invoke(newProfile)
                activeSpeakerForDialog = null
            },
            onRemoveGoldenSample = { speakerId ->
                onRemoveGoldenSample?.invoke(speakerId)
            },
            onFilterBySpeaker = { filterName ->
                selectedSpeakerFilter = filterName
            }
        )
    }
}

/**
 * Visual Master Session Card for multi-part sequential audio recordings.
 */
@Composable
fun MasterSessionCard(
    session: JournalEntry.SessionGroup,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    defaultKeywords: List<String>,
    expandedPartIds: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onTogglePartExpand: (String) -> Unit,
    onPlaySession: () -> Unit,
    onPlayPart: (TranscriptionRecord) -> Unit,
    onExportSession: () -> Unit,
    onDeleteSession: () -> Unit,
    speakers: List<SpeakerProfile> = emptyList(),
    onSpeakerClick: (String) -> Unit = {},
    audioPlayerContent: @Composable (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode) onToggleSelect() else onToggleExpand()
            },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() }
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.sessionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormat.format(Date(session.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPlaySession,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play Session",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            // Chips Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "${session.parts.size} Linked Parts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                if (session.totalDurationMs > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = formatDuration(session.totalDurationMs),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!session.modelName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = session.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Consolidated Master AI Summary Highlight Box
            session.masterSummary?.let { summaryText ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Master AI Summary (Consolidated)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Session Speakers Active Roster Chips
            val sessionSpeakers = remember(session.parts) {
                session.parts.flatMap { part ->
                    val list = mutableListOf<String>()
                    val actCsv = part.activeSpeakersCsv
                    val spk = part.speakerName
                    if (!actCsv.isNullOrBlank()) {
                        list.addAll(actCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    } else if (!spk.isNullOrBlank()) {
                        list.add(spk.trim())
                    }
                    list
                }.distinct()
            }

            if (sessionSpeakers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Speakers",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Speakers:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sessionSpeakers) { spkName ->
                            val matchedProfile = speakers.find { it.name.equals(spkName, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onSpeakerClick(spkName) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (matchedProfile?.hasGoldenSample == true) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified Golden Clip",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = spkName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Detailed Section (Parts Sequence & Actions)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Session Actions Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlaySession,
                            modifier = Modifier.weight(1.2f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Full Session", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onExportSession,
                            modifier = Modifier.weight(1.1f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSON", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = onDeleteSession,
                            modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Session", modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = "Session Parts Sequence (${session.parts.size} total):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Individual Part Items
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        session.parts.forEachIndexed { index, part ->
                            val isPartExpanded = expandedPartIds.contains(part.id)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${(part.partIndex ?: index) + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = "Part ${(part.partIndex ?: index) + 1} of ${part.totalParts ?: session.parts.size}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (part.partDurationMs != null && part.partDurationMs > 0) {
                                                    Text(
                                                        text = formatDuration(part.partDurationMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = { onPlayPart(part) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.heightIn(min = 32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play Part", style = MaterialTheme.typography.labelSmall)
                                            }

                                            TextButton(
                                                onClick = { onTogglePartExpand(part.id) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.heightIn(min = 32.dp)
                                            ) {
                                                Text(if (isPartExpanded) "Hide" else "Transcript")
                                                Icon(
                                                    imageVector = if (isPartExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Part Transcript & Inline Audio Player
                                    AnimatedVisibility(
                                        visible = isPartExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            TranscriptView(part.transcription, defaultKeywords)
                                            part.audioFilePath?.let { uri ->
                                                audioPlayerContent(uri)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard single standalone recording card in Journal.
 */
@Composable
fun SingleRecordingCard(
    record: TranscriptionRecord,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    defaultKeywords: List<String>,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onUpdateSpeakerName: (String) -> Unit,
    onOpenPlayback: () -> Unit,
    onExportJson: () -> Unit,
    speakers: List<SpeakerProfile> = emptyList(),
    onSpeakerClick: (String) -> Unit = {},
    audioPlayerContent: @Composable (String) -> Unit
) {
    var editSpeakerMode by remember { mutableStateOf(false) }
    var tempSpeakerName by remember { mutableStateOf(record.speakerName ?: "") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode) onToggleSelect() else onToggleExpand()
            },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() }
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = dateFormat.format(Date(record.timestamp)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!record.modelName.isNullOrBlank()) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = record.modelName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    },
                                    modifier = Modifier.height(20.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    border = null
                                )
                            }
                        }
                        if (!record.speakerName.isNullOrBlank()) {
                            Text(
                                text = "Speaker: ${record.speakerName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!record.locationName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = record.locationName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!record.diarizationConfidence.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val conf = record.diarizationConfidence!!
                            val confColor = when {
                                conf.contains("High", true) -> MaterialTheme.colorScheme.primary
                                conf.contains("Med", true) -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (conf.contains("High", true)) Icons.Default.Verified else Icons.Default.Warning,
                                    contentDescription = "Confidence",
                                    tint = confColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Confidence: $conf",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = confColor
                                )
                            }
                        }

                        // Interactive Speakers Chips
                        val singleSpeakerList = remember(record.activeSpeakersCsv, record.speakerName) {
                            val list = mutableListOf<String>()
                            val actCsv = record.activeSpeakersCsv
                            val spk = record.speakerName
                            if (!actCsv.isNullOrBlank()) {
                                list.addAll(actCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
                            } else if (!spk.isNullOrBlank()) {
                                list.add(spk.trim())
                            }
                            list.distinct()
                        }

                        if (singleSpeakerList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Speakers",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Speakers:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(singleSpeakerList) { spkName ->
                                        val matchedProfile = speakers.find { it.name.equals(spkName, ignoreCase = true) }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.clickable { onSpeakerClick(spkName) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (matchedProfile?.hasGoldenSample == true) {
                                                    Icon(
                                                        imageVector = Icons.Default.Verified,
                                                        contentDescription = "Verified Golden Clip",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                                Text(
                                                    text = spkName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!record.mentionedPeopleCsv.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Mentions",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Mentions: ${record.mentionedPeopleCsv}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenPlayback,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (record.audioUri != null) "Play" else "Review",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary Snippet or Full Highlights
            record.summary?.let { summaryText ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Collapsible Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Edit Speaker Name Section
                    if (editSpeakerMode) {
                        OutlinedTextField(
                            value = tempSpeakerName,
                            onValueChange = { tempSpeakerName = it },
                            label = { Text("Speaker Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        onUpdateSpeakerName(tempSpeakerName)
                                        editSpeakerMode = false
                                    },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save Speaker")
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (record.speakerName.isNullOrBlank()) "No speaker assigned" else "Speaker: ${record.speakerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = { editSpeakerMode = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (record.speakerName.isNullOrBlank()) "+ Add Speaker" else "Edit")
                            }
                        }
                    }

                    // Full Transcript View
                    TranscriptView(record.text, defaultKeywords)

                    // Audio Player if available
                    record.audioUri?.let { uriString ->
                        audioPlayerContent(uriString)
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenPlayback,
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Playback Screen")
                        }

                        OutlinedButton(
                            onClick = onExportJson,
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                    }
                }
            }
        }
    }
}
