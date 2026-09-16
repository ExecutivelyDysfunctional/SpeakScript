# Application State Registry (APP_STATE.md)

## Mode Confirmation
- **Architecture Mode**: Multi-File Mode (Native Android application using Kotlin, Jetpack Compose, Room DB, Retrofit, and Firebase).

---

## [Implemented]
- **Multi-Part Sequential Audio Session Import & Pipeline (Phase 1)**:
  - Multi-file audio picker allowing selection of single or multiple sequential recording parts.
  - Smart natural file sequence analysis (`analyzeSelectedFilesForSequence`) with natural numeric sorting and timestamp detection.
  - Interactive **Sequence Confirmation Bottom Sheet**:
    - Displays detected parts with part numbers, file names, and durations.
    - Customizable Session Title input.
    - Quick Up/Down reorder controls to verify and adjust the exact playback sequence.
  - Sequential streaming transcription engine processing each part in chronological order with live progress feedback.
  - Consolidated **Master AI Summary** synthesizing high-level executive overview, chronological discussion points, and action items across all parts.
  - Room database schema version 7 (`Transcription.kt`, `TranscriptionDao.kt`, `AppDatabase.kt`) with `sessionId`, `partIndex`, `totalParts`, `sessionTitle`, and `partDurationMs` fields.
- **Grouped Multi-Part Session Cards in Journal/History (Phase 2)**:
  - Unified `JournalEntry` abstraction supporting standalone recordings and connected session groups.
  - **Master Session Card (`MasterSessionCard`)**:
    - Distinct visual identity with `QueueMusic` badge and bold session title.
    - Status pills indicating total linked parts, aggregated playback duration, and Gemini model used.
    - Highlighted **Master AI Summary** card surface presenting the unified multi-part synthesis at a glance.
    - Session-level action suite: "Play Session", "Export Session JSON", and "Delete Session" with confirmation dialog.
    - Interactive **Parts Sequence List** showing each part with part number badges ("Part 1 of 3"), individual duration, speaker labels, quick "Play Part" action, and expandable inline transcript views with audio players.
    - Integrated multi-select mode: selecting a master session automatically batches all linked part records.
  - Full backward compatibility for standalone single-recording cards (`SingleRecordingCard`).
  - Session JSON Export & Import parsing supporting multi-part session files and standard backups.
- **Audio File Selection & Local Caching**: File picker for audio files (AAC, M4A, MP3, WAV) with local cache copying for persistent playback access.
- **Metadata & Timestamp Parsing**: Extracts recording timestamps from audio file metadata or file naming conventions (e.g. `YYYY-MM-DD_HH-MM-SS`).
- **Real-Time Streaming Transcription (Gemini AI)**: Live streaming speech-to-text using `gemini-3.5-flash` with Server-Sent Events (SSE) and incremental UI updates.
- **Speaker Diarization & Labeling**: Automatic parsing and badge coloring of distinct speaker turns (`Speaker A:`, `Speaker B:`) with timestamps.
- **Automated AI Highlights & Summary**: Generates concise executive summaries immediately after transcription completion.
- **Dynamic Waveform Visualizers**:
  - Processing visualizer: Animated traveling sinusoidal waveform during audio upload and processing.
  - Playback visualizer: Canvas-based interactive audio waveform representing playback position.
- **Dedicated Audio Playback Screen & Interactive Waveform Scrubbing**:
  - Distinct, focused full-screen playback space (`PlaybackScreen.kt`) with back navigation and clipboard export.
  - Interactive touch-to-seek and horizontal drag scrubbing on a 52-bar dynamic waveform canvas (`InteractiveWaveformVisualizer`).
  - Real-time scrubbing progress feedback with timestamp indicators, scrubber playhead line, and indicator pin.
  - Comprehensive player deck: Play/Pause hero button, ±10s fast-forward/rewind, speed toggle (0.75x, 1.0x, 1.25x, 1.5x, 2.0x), and restart.
  - Synchronized interactive transcript with **Real-Time Line-by-Line Timestamp Highlighting**:
    - Automatic identification and visual highlighting of the exact active transcript line matching the current audio timestamp.
    - Prominent active line styling: primary container background glow, left vertical accent indicator bar, high-contrast typography, live badge ("PLAYING" / "CURRENT"), and intra-line linear playback progress indicator.
    - Smart line parser (`parseTranscriptLines`) handling explicit timestamps, speaker tags, and intelligent time interpolation across lines.
    - Smooth auto-scrolling synchronization keeping the active transcript line centered in view as playback progresses, with an "Auto-Scroll ON/OFF" toggle chip.
    - Interactive tap-to-seek on any transcript line or timestamp pill to jump playback directly.
    - In-transcript search filter with real-time keyword highlighting.
  - Seamless entry points: "Play / Review" pill buttons on history cards, expanded card action, and direct "Open Player" button on the latest transcription card.
- **Built-in Audio Player**: Play, pause, 10s forward/rewind, and adjustable playback speeds (0.5x, 1.0x, 1.5x, 2.0x).
- **Keyword Highlighting**: Automatically highlights meeting and domain keywords (e.g. action items, deadlines, technical terms).
- **Complex Query ("High Thinking")**: Dedicated query field powered by `gemini-3.1-pro-preview` with high thinking configuration for deep analysis of transcripts.
- **Local Persistence (Room Database & Transcription Entity)**: Dedicated Room database (`AppDatabase`, `TranscriptionDao`, `TranscriptionRepository`) with the `Transcription` entity (`tableName = "transcriptions"`) storing transcription text (`transcription`), speaker labels (`speakerLabels`), and associated audio file paths (`audioFilePath`), along with ID, timestamps, AI summaries, category, and model metadata.
- **Journal History Screen**: Organized list with chronological groupings ("Today", "Yesterday", "This Week", "Older").
- **Real-Time Search & Filtering**: Instant filter across transcript text, summaries, speaker names, and dates.
- **Batch Deletion**: Multi-select mode with checkboxes to batch-delete records.
- **Speaker Renaming**: Inline editor to assign custom names to speakers.
- **Single Record JSON Export**: Export individual transcription records as formatted JSON files via Android Storage Access Framework.
- **Full Database Export & Import (JSON Backup & Restore)**: Export complete database to portable JSON (`Download JSON`) and restore/import records (`Upload JSON`) supporting single or batch arrays for lossless offline migration.
- **Visual Error & Success Banners (Zero Silent Failures)**: Persistent, dismissible Material 3 visual error and info banners directly on-screen, completely eliminating silent console-only exceptions.
- **Mobile Viewport & Safe Drawing (`viewport-fit=cover`)**: Full edge-to-edge drawing under system gesture bars with `WindowInsets.safeDrawing` in Scaffold.
- **Pixel 8 Pro Touch Ergonomics**: Enforced minimum 48dp x 48dp touch targets across all buttons, icon actions, search inputs, and chips.
- **Multi-Provider AI Settings (BYOK)**: Key management and provider selection for Google Gemini, OpenRouter, and Groq.
- **Theme Customization**: Full Material 3 support for System Default, Light Mode, and Dark Mode.
- **Optional Cloud & Account Sync**: Google Sign-In via Credential Manager, Email/Password sign-in, and Firestore syncing.

---

## [Next Up]
- **Multi-Part Sessions Phase 3 (Gapless Playback & Cumulative Transcript Engine)**: Seamless auto-advance across sequential parts in the player with unified cumulative waveform and continuous transcript line tracking.
- **OpenRouter & Groq Provider Execution**: Connect the configured OpenRouter and Groq API keys to the transcription and query execution pipeline.
- **[Workshop / On Hold] Personal Audio Tagging & Filtering**: Design a taxonomy of tags customized specifically for personal recordings and notes (excluding unneeded work/meeting/lecture categories) to be refined later.

---

## [Out of Scope]
- **Direct Microphone Voice Recording**: In-app live microphone recording is excluded; the app focuses on importing and analyzing stored audio files.
- **Custom Secondary Backend / Node.js Server**: Keep the app entirely client-side and offline-first with optional direct Firebase sync.
- **Complex Multi-Track Audio Editing / DAW Features**: Keep the focus on speech transcription, AI intelligence, and journaling rather than audio mastering.
- **Closed Third-Party Telemetry SDKs**: Strictly use direct APIs and client-side privacy-first storage.

---

## [Files]
- `metadata.json`: Application metadata and platform capabilities for AI Studio.
- `app/build.gradle.kts`: Gradle build script with dependencies for Compose, Room, Retrofit, and Firebase.
- `app/src/main/AndroidManifest.xml`: Android application manifest declaring permissions and activities.
- `app/src/main/java/com/example/MainActivity.kt`: Primary entry activity, top-level navigation tabs, audio picker, player, and transcript viewer.
- `app/src/main/java/com/example/TranscriptionHistoryScreen.kt`: Journal history UI, timeline grouping, search, batch deletion, and JSON export.
- `app/src/main/java/com/example/ui/PlaybackScreen.kt`: Dedicated audio playback and review screen with interactive waveform touch scrubbing, timestamp jumping, and keyword search.
- `app/src/main/java/com/example/ui/MainViewModel.kt`: Central state manager, Gemini streaming transcription logic, AI queries, and data coordination.
- `app/src/main/java/com/example/ui/SettingsScreen.kt`: Settings screen for AI provider keys (Gemini, OpenRouter, Groq), appearance theme mode, and authentication.
- `app/src/main/java/com/example/api/GeminiApiService.kt`: Retrofit client interfaces, data transfer models, and network clients for Gemini, OpenRouter, and Groq.
- `app/src/main/java/com/example/db/Transcription.kt`: Room Entity for audio transcriptions, speaker labels, and audio file paths.
- `app/src/main/java/com/example/db/AppDatabase.kt`: Room database initialization and migration definitions.
- `app/src/main/java/com/example/db/TranscriptionDao.kt`: Data access object for Room database queries and mutations.
- `app/src/main/java/com/example/db/TranscriptionRepository.kt`: Repository layer mediating between Room DAO and the ViewModel.
- `app/src/main/java/com/example/ui/theme/Color.kt`: M3 color definitions.
- `app/src/main/java/com/example/ui/theme/Theme.kt`: Material 3 theme wrapper and dynamic color handling.
- `app/src/main/java/com/example/ui/theme/Type.kt`: Typography definitions.
