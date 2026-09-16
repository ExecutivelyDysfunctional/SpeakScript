package com.example.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.db.SpeakerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val PRESET_SPEAKER_COLORS = listOf(
    "#2196F3" to "Blue",
    "#9C27B0" to "Purple",
    "#009688" to "Teal",
    "#FF9800" to "Amber",
    "#E91E63" to "Pink",
    "#4CAF50" to "Green",
    "#3F51B5" to "Indigo",
    "#F44336" to "Red"
)

fun parseHexColor(hex: String?, defaultColor: Color): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        defaultColor
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerManagementScreen(
    speakers: List<SpeakerProfile>,
    onSaveSpeaker: (SpeakerProfile) -> Unit,
    onDeleteSpeaker: (String) -> Unit,
    onRemoveGoldenSample: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, GOLDEN, FREQUENT
    var speakerToEdit by remember { mutableStateOf<SpeakerProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var speakerToDelete by remember { mutableStateOf<SpeakerProfile?>(null) }

    // Filter & sort logic
    val filteredSpeakers = remember(speakers, searchQuery, selectedFilter) {
        var list = if (searchQuery.isBlank()) {
            speakers
        } else {
            speakers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.relationshipOrRole?.contains(searchQuery, ignoreCase = true) == true ||
                        it.notes?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        when (selectedFilter) {
            "GOLDEN" -> list.filter { !it.goldenSampleAudioUri.isNullOrBlank() }
            "FREQUENT" -> list.sortedByDescending { it.totalRecordingsCount }
            else -> list.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    val totalGoldenCount = remember(speakers) {
        speakers.count { !it.goldenSampleAudioUri.isNullOrBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Known Speakers Roster",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${speakers.size} Profiles • $totalGoldenCount Golden Samples",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add Speaker") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by speaker name, role, or notes...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${speakers.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "GOLDEN",
                        onClick = { selectedFilter = "GOLDEN" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Golden Samples ($totalGoldenCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "FREQUENT",
                        onClick = { selectedFilter = "FREQUENT" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Most Frequent") }
                    )
                }
            }

            // Empty state
            if (filteredSpeakers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No speakers match \"$searchQuery\"" else "No speakers in this directory yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add team members, clients, or interviewers. As Gemini transcribes audio, recognized speakers are automatically counted and linked here!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add First Speaker")
                            }
                        }
                    }
                }
            } else {
                items(filteredSpeakers, key = { it.id }) { speaker ->
                    SpeakerCard(
                        speaker = speaker,
                        onEdit = { speakerToEdit = speaker },
                        onDelete = { speakerToDelete = speaker },
                        onRemoveGoldenSample = { onRemoveGoldenSample(speaker.id) }
                    )
                }
            }
        }
    }

    // Add or Edit Dialog
    if (showAddDialog || speakerToEdit != null) {
        SpeakerEditDialog(
            initialSpeaker = speakerToEdit,
            onDismiss = {
                showAddDialog = false
                speakerToEdit = null
            },
            onSave = { savedProfile ->
                onSaveSpeaker(savedProfile)
                showAddDialog = false
                speakerToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (speakerToDelete != null) {
        AlertDialog(
            onDismissRequest = { speakerToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Speaker Profile?") },
            text = {
                Text("Are you sure you want to remove \"${speakerToDelete?.name}\" from the known speakers directory? Associated past transcripts will keep their text labels.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        speakerToDelete?.let { onDeleteSpeaker(it.id) }
                        speakerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { speakerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SpeakerCard(
    speaker: SpeakerProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveGoldenSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = parseHexColor(speaker.colorHex, MaterialTheme.colorScheme.primary)
    val hasGoldenSample = !speaker.goldenSampleAudioUri.isNullOrBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Avatar, Name, Role, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = remember(speaker.name) {
                        speaker.name.trim().split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase(Locale.getDefault())
                    }
                    Text(
                        text = if (initials.isNotEmpty()) initials else "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Name and Role
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = speaker.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasGoldenSample) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Golden Sample Verified",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (!speaker.relationshipOrRole.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = speaker.relationshipOrRole,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Edit & Delete actions
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Speaker")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Speaker",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total recordings count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${speaker.totalRecordingsCount} recordings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Last heard timestamp
                speaker.lastHeardTimestamp?.let { timestamp ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val dateStr = remember(timestamp) {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                        }
                        Text(
                            text = "Last heard: $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Notes if any
            if (!speaker.notes.isNullOrBlank()) {
                Text(
                    text = speaker.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Golden Sample Audio Deck
            if (hasGoldenSample) {
                GoldenSampleAudioPlayer(
                    audioUri = speaker.goldenSampleAudioUri!!,
                    startMs = speaker.goldenSampleStartMs ?: 0,
                    endMs = speaker.goldenSampleEndMs ?: 15000,
                    recordingTitle = speaker.goldenSampleRecordingTitle ?: "Recording Reference",
                    accentColor = accentColor,
                    onRemove = onRemoveGoldenSample
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.StarOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No golden reference set. Bookmark a clip in Playback to train recognition.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * Embedded audio player dedicated to previewing the designated Golden Sample clip.
 */
@Composable
fun GoldenSampleAudioPlayer(
    audioUri: String,
    startMs: Int,
    endMs: Int,
    recordingTitle: String,
    accentColor: Color,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgressMs by remember { mutableIntStateOf(startMs) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val durationMs = (endMs - startMs).coerceAtLeast(1000)

    DisposableEffect(audioUri, startMs, endMs) {
        onDispose {
            player?.stop()
            player?.release()
            player = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val currentPos = player?.currentPosition ?: startMs
            currentProgressMs = currentPos
            if (currentPos >= endMs || !(player?.isPlaying ?: false)) {
                isPlaying = false
                player?.pause()
                player?.seekTo(startMs)
                currentProgressMs = startMs
                break
            }
            delay(100)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Golden Reference Clip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Text(
                    text = "${formatDuration(startMs)} - ${formatDuration(endMs)} (${durationMs / 1000}s)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Source: $recordingTitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Playback controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = {
                        try {
                            if (player == null) {
                                val p = MediaPlayer().apply {
                                    setDataSource(context, Uri.parse(audioUri))
                                    prepare()
                                    seekTo(startMs)
                                    setOnCompletionListener {
                                        isPlaying = false
                                        currentProgressMs = startMs
                                    }
                                }
                                player = p
                                p.start()
                                isPlaying = true
                            } else {
                                if (isPlaying) {
                                    player?.pause()
                                    isPlaying = false
                                } else {
                                    if ((player?.currentPosition ?: 0) !in startMs..endMs) {
                                        player?.seekTo(startMs)
                                    }
                                    player?.start()
                                    isPlaying = true
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not play golden clip: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (isPlaying) "Pause Golden Sample" else "Play Golden Sample",
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Progress Bar
                val progressFraction = remember(currentProgressMs, startMs, endMs) {
                    val rel = (currentProgressMs - startMs).toFloat()
                    (rel / durationMs).coerceIn(0f, 1f)
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f)
                )

                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove Golden Sample",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Add / Edit Speaker Profile Dialog
 */
@Composable
fun SpeakerEditDialog(
    initialSpeaker: SpeakerProfile?,
    onDismiss: () -> Unit,
    onSave: (SpeakerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialSpeaker?.name ?: "") }
    var relationshipOrRole by remember { mutableStateOf(initialSpeaker?.relationshipOrRole ?: "") }
    var selectedColorHex by remember { mutableStateOf(initialSpeaker?.colorHex ?: PRESET_SPEAKER_COLORS[0].first) }
    var notes by remember { mutableStateOf(initialSpeaker?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = initialSpeaker != null

    val roleSuggestions = listOf("Colleague", "Host", "Lead", "Manager", "Friend", "Family", "Interviewer", "Client")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Speaker Profile" else "Add Known Speaker",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError && it.isNotBlank()) nameError = false
                    },
                    label = { Text("Speaker Full Name *") },
                    placeholder = { Text("e.g. Austin Grindy, Sarah Chen") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = relationshipOrRole,
                    onValueChange = { relationshipOrRole = it },
                    label = { Text("Role or Relationship") },
                    placeholder = { Text("e.g. Product Lead, Interviewer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Suggestion Chips
                Text(
                    text = "Suggested Roles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(roleSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { relationshipOrRole = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Color Picker
                Text(
                    text = "Profile Badge Color",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRESET_SPEAKER_COLORS.forEach { (hex, _) ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val color = parseHexColor(hex, MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorHex = hex }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Bio (Optional)") },
                    placeholder = { Text("Context or project affiliation...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        val profile = initialSpeaker?.copy(
                            name = name.trim(),
                            relationshipOrRole = relationshipOrRole.trim().ifBlank { null },
                            colorHex = selectedColorHex,
                            notes = notes.trim().ifBlank { null }
                        ) ?: SpeakerProfile(
                            name = name.trim(),
                            relationshipOrRole = relationshipOrRole.trim().ifBlank { null },
                            colorHex = selectedColorHex,
                            notes = notes.trim().ifBlank { null }
                        )
                        onSave(profile)
                    }
                }
            ) {
                Text(if (isEditing) "Save Changes" else "Create Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Detail sheet or dialog shown when tapping on a speaker chip in a recording card.
 */
@Composable
fun SpeakerDetailDialog(
    speaker: SpeakerProfile?,
    unrecognizedName: String?,
    onDismiss: () -> Unit,
    onSaveNewSpeaker: ((SpeakerProfile) -> Unit)? = null,
    onRemoveGoldenSample: ((String) -> Unit)? = null,
    onFilterBySpeaker: ((String) -> Unit)? = null
) {
    if (speaker != null) {
        val accentColor = parseHexColor(speaker.colorHex, MaterialTheme.colorScheme.primary)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = speaker.name.trim().split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase(Locale.getDefault())
                        Text(
                            text = if (initials.isNotEmpty()) initials else "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = speaker.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!speaker.relationshipOrRole.isNullOrBlank()) {
                            Text(
                                text = speaker.relationshipOrRole,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Recordings: ${speaker.totalRecordingsCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        speaker.lastHeardTimestamp?.let {
                            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                            Text(
                                text = "Last heard: $dateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!speaker.notes.isNullOrBlank()) {
                        Text(
                            text = speaker.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!speaker.goldenSampleAudioUri.isNullOrBlank()) {
                        GoldenSampleAudioPlayer(
                            audioUri = speaker.goldenSampleAudioUri,
                            startMs = speaker.goldenSampleStartMs ?: 0,
                            endMs = speaker.goldenSampleEndMs ?: 15000,
                            recordingTitle = speaker.goldenSampleRecordingTitle ?: "Reference Clip",
                            accentColor = accentColor,
                            onRemove = { onRemoveGoldenSample?.invoke(speaker.id) }
                        )
                    } else {
                        Text(
                            text = "No golden reference clip assigned yet. Bookmark a clip in Playback to set one.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            },
            dismissButton = if (onFilterBySpeaker != null) {
                {
                    OutlinedButton(
                        onClick = {
                            onFilterBySpeaker(speaker.name)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Filter Journal")
                    }
                }
            } else null
        )
    } else if (!unrecognizedName.isNullOrBlank()) {
        // Quick dialog to add unrecognized speaker
        SpeakerEditDialog(
            initialSpeaker = SpeakerProfile(name = unrecognizedName),
            onDismiss = onDismiss,
            onSave = { newProfile ->
                onSaveNewSpeaker?.invoke(newProfile)
                onDismiss()
            }
        )
    }
}
