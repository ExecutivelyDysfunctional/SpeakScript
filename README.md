# Transcribe AI

**Transcribe AI** is an intelligent voice recording and speech analysis application powered by **Gemini**. It provides real-time streaming audio transcription, automatic speaker recognition, AI-generated highlights, contextual Q&A, and an interactive waveform audio player.

---

# 📖 User Guide

### What Transcribe AI Does for You
- **Record or Import Audio**: Capture live microphone notes or import existing audio files (`.aac`, `.m4a`) from your device.
- **Real-Time Transcription**: Watch transcriptions stream in live with automatic speaker identification (`Speaker 1`, `Speaker 2`) and keyword highlighting.
- **Smart Summaries & Highlights**: Automatically get executive summaries and key takeaways for every conversation.
- **Interactive Audio Player**: Review recordings using a visual waveform player with variable playback speeds (`0.5x`, `1.0x`, `1.5x`, `2.0x`) and skip controls.
- **Ask Gemini Q&A**: Ask complex questions about your transcripts and get deep reasoning answers instantly.
- **Organize & Search**: Tag conversations by category (**Work**, **Personal**, **Meeting**), rename speakers, search across text or dates, batch delete, and export records as JSON.
- **Appearance Settings**: Easily switch between Light, Dark, or System themes.

### How to Use the App
1. **Record Audio**: Tap the **Record Audio** button to start recording. Tap **Stop Recording** when finished to begin transcription.
2. **Import Audio**: Tap **Pick AAC Audio** to select an audio file from your device.
3. **Playback**: Tap the **Play** button on any history card to listen to the recording with waveform progress tracking and speed controls.
4. **Ask Questions**: Type your question in the query box under a transcript and tap **Ask Gemini** for deep insights.
5. **Organize**: Tap speaker names to rename them, tap category pills to tag notes, or use the search bar to find past transcriptions instantly.
6. **Settings**: Tap the gear icon in the top bar to manage your account sync and theme preferences.

---

# 🛠️ Developer Guide

### Architecture & Project Structure
The application follows modern Android architecture (MVVM, Kotlin Coroutines, Jetpack Compose, Room):
```
app/src/main/java/com/example/
├── MainActivity.kt               # Main entry point and UI screens
├── api/
│   └── GeminiApiService.kt       # Retrofit service & Gemini streaming client
├── audio/
│   └── AudioRecorder.kt          # Android MediaRecorder wrapper
├── db/
│   ├── AppDatabase.kt            # Room database definition
│   ├── TranscriptionDao.kt       # Room data access object
│   └── TranscriptionRepository.kt# Repository managing local/cloud data flows
└── ui/
    ├── MainViewModel.kt          # ViewModel managing app state & API calls
    ├── SettingsScreen.kt         # Settings & authentication UI
    └── theme/
        ├── Color.kt              # M3 color palettes
        ├── Theme.kt              # Theme definitions
        └── Type.kt               # Typography configurations
```

### Tech Stack & Dependencies
- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Local Persistence**: Room SQLite Database
- **Networking**: Retrofit, Kotlinx Serialization
- **AI Integration**: Gemini API (Streaming SSE & High Thinking mode)

### Configuration & Build Requirements
- **Minimum SDK**: Android 24+
- **Target SDK**: Android 34
- **API Key**: Configured securely via `BuildConfig.GEMINI_API_KEY` (managed through AI Studio Secrets).

### CI/CD & GitHub Actions
Automated APK builds are configured via `.github/workflows/build-apk.yml`. Builds run on manual dispatch (`workflow_dispatch`) or when a push includes the `[build]` or `[build-apk]` tag in the commit message.
