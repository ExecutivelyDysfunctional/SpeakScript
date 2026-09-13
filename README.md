# Transcribe AI

**Transcribe AI** is an intelligent Android voice recording and speech analysis application built with **Jetpack Compose**, **Kotlin Coroutines**, **Room Local Database**, and **Gemini 3.5 Flash**. It provides real-time streaming audio transcription, automatic speaker diarization, AI-generated highlights, deep contextual Q&A using Gemini High Thinking mode, and a responsive custom audio waveform player.

---

## 📱 Features Overview

### 1. High-Fidelity Audio Recording & Audio File Picker
- **One-Tap Live Recording**: Record microphone audio in compressed AAC format with runtime permission handling.
- **Audio File Import**: Select any local `.aac` / `.m4a` / audio file from device storage to transcribe pre-recorded meetings, interviews, or lectures.
- **Processing Waveform Visualizer**: Features a dynamic animated multi-bar harmonic waveform visualizer that undulates with dual-frequency wave motion while an audio file is being uploaded and processed by Gemini.

### 2. Gemini Real-Time Streaming Transcription & Speaker Diarization
- **Live Streaming Output**: Displays streaming transcript text dynamically in real time via Server-Sent Events (SSE) from the Gemini API.
- **Multi-Speaker Recognition**: Automatically detects speech patterns and labels speakers (e.g., `Speaker 1:`, `Speaker 2:`) with distinct color-coded badges and highlight chips.
- **Domain Keyword Highlighting**: Visually highlights high-value keywords (e.g., *deadline*, *action item*, *meeting*, *cloud*, *API*, *architecture*, *roadmap*) directly in the transcript text for rapid scanning.

### 3. Automated AI Highlights & Summaries
- **Executive Summaries**: After transcription completes, Gemini automatically synthesizes the discussion into structured highlights and actionable takeaways.
- **Dedicated Highlights Card**: Highlight callouts appear with an accent badge above each transcription in the details card and history feed.

### 4. Interactive Waveform Audio Player
- **Visual Waveform**: Renders audio amplitude bars across a custom Compose Canvas with real-time playback progress tracking.
- **Seek & Skip**: 10-second fast-forward and 10-second rewind controls.
- **Variable Playback Speeds**: Toggle between `0.5x`, `1.0x`, `1.5x`, and `2.0x` speeds for efficient review.
- **Audio Lifecycle Management**: Gracefully initializes and releases `MediaPlayer` instances when scrolling or switching screens.

### 5. Contextual Follow-Up Q&A (Gemini High Thinking)
- **"Ask Gemini" Query Engine**: Ask complex questions about the latest transcript (e.g., *"What were the key decisions made?"*, *"Who was assigned to the API task?"*).
- **High Thinking Mode**: Utilizes an extended thinking budget for deep reasoning, synthesis, and nuanced extraction of meeting details.

### 6. Offline-First Room Persistence & Cloud Synchronization
- **Local Room Database**: All recordings, transcripts, summaries, speaker labels, and categories are saved locally on device using SQLite Room for instant offline access.
- **Optional Firebase Cloud Sync**: Supports syncing transcripts across devices via Cloud Firestore.
- **Google Sign-In**: Integrated with modern Android Credential Manager and Firebase Authentication (with automatic anonymous fallback for offline and guest use).

### 7. Organization, Search & Batch Management
- **Speaker Tagging & Renaming**: Easily edit or rename speaker tags (e.g., replace *"Speaker 1"* with *"Alex"*).
- **Categories**: Tag items as **Work**, **Personal**, or **Meeting** with quick-toggle filter chips.
- **Multi-Field Search**: Real-time search bar filtering across dates, speakers, categories, summary points, and transcript content.
- **Batch Deletion**: Enter selection mode to check multiple recordings and delete them in bulk.
- **JSON Export**: Export any transcription with full metadata (timestamps, speakers, categories, and summary) as a standard JSON file via Android Storage Access Framework (`CreateDocument`).

### 8. Adaptive UI & Theme Switching
- **Material 3 Design**: Clean typography, standard 8dp grid spacing, and accessible touch targets.
- **Theme Modes**: Configure **Light**, **Dark**, or **System Default** themes with dedicated segmented buttons and a dark mode toggle located on the Settings screen.

---

## 📁 Architecture & Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt               # Main entry point, UI composables, audio player & layout
├── api/
│   └── GeminiApiService.kt       # Retrofit service definitions & Gemini streaming client
├── audio/
│   └── AudioRecorder.kt          # Android MediaRecorder wrapper producing AAC files
├── db/
│   ├── AppDatabase.kt            # Room database definition with schema migrations
│   ├── TranscriptionDao.kt       # Room DAO (insert, query, bulk delete)
│   └── TranscriptionRepository.kt# Repository layer exposing Flow streams
└── ui/
    ├── MainViewModel.kt          # ViewModel managing audio recording, Gemini calls, and state
    ├── SettingsScreen.kt         # Account authentication and settings management
    └── theme/
        ├── Color.kt              # M3 color palettes
        ├── Theme.kt              # Theme definitions (Light/Dark/System)
        └── Type.kt               # Typography configurations
```

---

## 🚀 How to Use the Features

### Recording & Transcribing Audio
1. Tap the **Record Audio** floating action button. If prompted, grant microphone access.
2. Speak clearly into the microphone. Tap **Stop Recording** when finished.
3. Transcribe AI will automatically stream the audio to Gemini, displaying real-time transcription chunks with speaker labels and generating meeting highlights upon completion.

### Transcribing an Existing Audio File
1. Tap **Pick AAC Audio** next to the record button.
2. Select any compatible audio file (`.aac`, `.m4a`, etc.) from your device's file manager.
3. The app will process, upload, transcribe, and persist the recording automatically.

### Asking Questions About a Transcription
1. In the **Ask a complex query (High Thinking)** text field, enter any question regarding the transcription (e.g., *"Summarize next steps in 3 bullet points"*).
2. Tap **Ask Gemini**.
3. View the synthesized answer generated with Gemini's reasoning engine.

### Playing Back Audio with the Waveform Player
1. In the **History** list, find the recording card.
2. Tap the circular **Play** button to begin audio playback.
3. Tap **Fast Forward (10s)** or **Rewind (10s)** to skip through the audio.
4. Tap the **Speed button** (e.g., `1.0x`) to cycle playback speeds: `1.0x` ➔ `1.5x` ➔ `2.0x` ➔ `0.5x`.

### Renaming Speakers & Adding Categories
1. **Edit Speaker**: On any history card, tap **Add Speaker** or **Edit** next to the speaker line, enter the person's name, and tap the checkmark icon.
2. **Assign Category**: Tap any category pill (**Work**, **Personal**, or **Meeting**) to tag or untag the entry.

### Searching & Filtering Transcriptions
- Type into the **Search by date or speaker/keyword...** bar to instantly filter the list by speaker name, date (e.g., *"Sep 12"*), category, or spoken phrases.

### Batch Deleting Transcriptions
1. In the **History** header, tap **Select**.
2. Check the boxes next to the items you want to remove.
3. Tap **Delete (N)** to permanently remove the selected entries from local and cloud storage. Tap **Cancel** to exit selection mode.

### Exporting to JSON
1. Tap **Export JSON** at the bottom of any history card.
2. Choose a destination folder and file name in the Android system file picker.
3. The exported file contains the full record schema including raw text, highlights, timestamps, and speaker metadata.

### Account & Sync Settings
1. Tap the **Settings (gear)** icon in the top app bar.
2. View your current sign-in status. Tap **Sign In with Google** to connect your account and enable cross-device cloud sync.
3. Tap the back button to return to the main transcription dashboard.

### Changing Themes
1. Tap the **Settings (gear)** icon in the top app bar.
2. In the **Appearance** section, select between **System**, **Light**, or **Dark** mode using the segmented buttons, or toggle the **Dark Theme** switch directly.

---

## ⚙️ Configuration & Requirements

- **Minimum SDK**: Android 24+ (Android 7.0 Nougat)
- **Target SDK**: Android 34 (Android 14)
- **Gemini API Key**: Configured securely via `BuildConfig.GEMINI_API_KEY` (injected via `.env`).
- **Permissions**:
  - `android.permission.RECORD_AUDIO`: Required for live microphone recording.
  - `android.permission.INTERNET`: Required for Gemini API transcription and cloud sync.
