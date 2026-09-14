package com.example

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.UiState
import com.example.ui.TranscriptionRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionHistoryScreen(
    uiState: UiState,
    onUpdateCategory: (TranscriptionRecord, String) -> Unit,
    onUpdateSpeakerName: (TranscriptionRecord, String) -> Unit,
    onDeleteTranscriptions: (List<String>) -> Unit,
    onExportJson: (TranscriptionRecord) -> Unit,
    onSync: () -> Unit,
    defaultKeywords: List<String>,
    audioPlayerContent: @Composable (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var expandedCardIds by remember { mutableStateOf(setOf<String>()) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val todayStr = dayFormat.format(Date())
    val yesterdayStr = dayFormat.format(Date(System.currentTimeMillis() - 86400000L))

    // Filter history
    val filteredHistory = uiState.history.filter { record ->
        val dateString = dateFormat.format(Date(record.timestamp))
        val query = searchQuery.lowercase()
        val matchesQuery = query.isBlank() ||
                dateString.lowercase().contains(query) ||
                record.text.lowercase().contains(query) ||
                (record.summary?.lowercase()?.contains(query) == true) ||
                (record.speakerName?.lowercase()?.contains(query) == true)

        matchesQuery
    }

    // Group by Date Header (Today, Yesterday, This Week, Older)
    val groupedHistory = filteredHistory.groupBy { record ->
        val recordDay = dayFormat.format(Date(record.timestamp))
        when {
            recordDay == todayStr -> "Today"
            recordDay == yesterdayStr -> "Yesterday"
            System.currentTimeMillis() - record.timestamp < 7 * 86400000L -> "This Week"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription Journal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                actions = {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud")
                    }
                    if (uiState.history.isNotEmpty()) {
                        TextButton(onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedIds = emptySet()
                        }) {
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
            
            // Search Bar Optimized for Mobile Thumb Reach
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search summaries, speakers, keywords...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (filteredHistory.isEmpty()) {
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
                            text = "No transcriptions found",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    groupedHistory.forEach { (headerTitle, records) ->
                        item(key = "header_$headerTitle") {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        items(records, key = { it.id }) { record ->
                            val isSelected = selectedIds.contains(record.id)
                            val isExpanded = expandedCardIds.contains(record.id)
                            var editSpeakerMode by remember { mutableStateOf(false) }
                            var tempSpeakerName by remember { mutableStateOf(record.speakerName ?: "") }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - record.id else selectedIds + record.id
                                        } else {
                                            expandedCardIds = if (isExpanded) expandedCardIds - record.id else expandedCardIds + record.id
                                        }
                                    },
                                colors = if (isSelected) {
                                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                } else {
                                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                },
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
                                                    onCheckedChange = { checked ->
                                                        selectedIds = if (checked) selectedIds + record.id else selectedIds - record.id
                                                    }
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
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                expandedCardIds = if (isExpanded) expandedCardIds - record.id else expandedCardIds + record.id
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                                            )
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
                                                    maxLines = if (isExpanded) Int.MAX_VALUE else 2
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
                                                        IconButton(onClick = {
                                                            onUpdateSpeakerName(record, tempSpeakerName)
                                                            editSpeakerMode = false
                                                        }) {
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
                                                    TextButton(onClick = { editSpeakerMode = true }) {
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

                                            // Export JSON Action
                                            OutlinedButton(
                                                onClick = { onExportJson(record) },
                                                modifier = Modifier.align(Alignment.End)
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
                }
            }
        }
    }
}
