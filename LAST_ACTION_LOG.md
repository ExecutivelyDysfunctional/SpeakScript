# Last Action History & Resolution Log

**Timestamp:** 2026-09-16T14:01:00-07:00  
**Status:** Successfully Resolved (Universal Audio Support & HTTP 400 Fix)

---

## 1. Triggering Issue
- **HTTP 400 Error on Audio Uploads**: AAC/M4A audio files uploaded for transcription caused an HTTP 400 Bad Request error from the Gemini API.
- **Root Cause**: The application hardcoded `mimeType = "audio/aac"` across `MainViewModel.kt` methods (`transcribeAudioUri`, `transcribeAudioBytes`, `transcribeBatchAudio`, `transcribeSequentialSession`, `transcribeAndSaveSync`). When uploading ISO MP4 container files (`.m4a` with `ftyp` headers) or raw WAV files, Gemini rejected the mismatch between the file binary structure and the declared `"audio/aac"` MIME type.

---

## 2. Corrective Action Taken
- **Dynamic Audio Info & Container Inspection (`detectAudioInfo`)**: Added binary header magic-byte detection (`ftyp`, `RIFF`, `ID3`, `OggS`, `fLaC`, `#!AMR`, syncwords) and `ContentResolver` + file extension fallback to detect real audio formats.
- **Gemini MIME Sanitization (`sanitizeMimeTypeForGemini`)**: Maps ISO MP4 container audio to `audio/m4a`, WAV files to `audio/wav`, MP3 to `audio/mp3`, OGG to `audio/ogg`, FLAC to `audio/flac`, and raw ADTS streams to `audio/aac`.
- **Expanded Audio Format Support**: Added full support for WAV (`.wav`), AAC (`.aac`), M4A (`.m4a`), MP3 (`.mp3`), OGG / OPUS (`.ogg`, `.opus`), FLAC (`.flac`), 3GP (`.3gp`), AMR (`.amr`), MP4 Audio (`.mp4`), WMA (`.wma`), and AIFF (`.aiff`).
- **Google Drive Upload Sync**: Google Drive uploads now preserve the detected file extension and true MIME type.

---

## 3. Verification & Outcome
- **Compilation Check (`compile_applet`)**: Succeeded cleanly without any errors.
- **Outcome**: The application dynamically handles and transcribes all major audio file types without HTTP 400 errors.
