# Last Action History & Resolution Log

**Timestamp:** 2026-09-20T00:38:07-07:00  
**Status:** In-Depth Architecture Inspection & Verification Audit Complete (Awaiting Plan Approval)

---

## 1. Action Summary & Prompt Purpose
- Inspected the SpeakScript Android Kotlin codebase following the Firebase migration commit (`b029e495104902a048baee6edb7349827006f5ab`).
- Audited Firebase configurations, Authentication implementation, Firestore synchronization, Room database schema & migrations, residual Google references, and build/unit test health.
- Enforced strict read-only policy on application code; no application code files modified during this step.

---

## 2. Commit Inspected
- **Commit:** `b029e495104902a048baee6edb7349827006f5ab`
- **Message:** "refactor: replace Google Drive with Firebase sync"

---

## 3. Key Findings

1. **Firebase Configuration**:
   - `firebase-bom` (34.17.0), `firebase-auth`, `firebase-firestore`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug`, and `firebase-ai` are present.
   - `firebase-storage` is not yet declared in `libs.versions.toml` or `app/build.gradle.kts`.
   - `google-services.json` is missing from the workspace, but properly ignored in `/.gitignore`. Fallback properties (`MissingGoogleServicesStrategy.WARN`, `googleServices.missing.passthrough=true`) and safe lazy wrappers in Kotlin allow the app to compile and run without crashing when Firebase configuration is unavailable.

2. **Authentication Implementation**:
   - **Guest / Anonymous mode**: Fully functional offline fallback with anonymous Firebase session initialization on launch.
   - **Email/Password & Account Linking**: ViewModel has `signInWithEmail`, `registerWithEmail`, and `currentUser.linkWithCredential()` to convert anonymous accounts while preserving UID and local data.
   - **UI Disconnect**: `SettingsScreen.kt` currently instantiates `FirebaseAuth` directly inside its dialog, bypassing `MainViewModel` and skipping the account-linking and data re-keying logic.
   - **Missing UI / Auth Features**: Password reset is implemented in ViewModel but has no button in `SettingsScreen.kt`. Email verification is not implemented.

3. **Firestore Synchronization**:
   - Collections are correctly organized under `users/{uid}/transcriptions`.
   - Anonymous users are prevented from syncing to Firestore.
   - Synchronization is both automatic (on recording save/edit) and manual (via "Sync Now").
   - **Critical Bug Identified**: `MainViewModel.deleteTranscriptions` currently executes `firestore.collection(...).document(id).delete()`. This violates the requirement that deleting a local record must NOT delete its Firestore record.
   - Only `Transcription` records are synced to Firestore; `SpeakerProfile` and `LocationProfile` are not yet included in cloud sync.

4. **Room Database & Migrations**:
   - Current database version: 12 (`transcriptions`, `speaker_profiles`, `location_profiles`).
   - `transcriptions` has `user_id` column (added in migration 11->12).
   - `speaker_profiles` and `location_profiles` currently lack `user_id` columns.
   - Local audio paths (`audio_file_path`, `golden_sample_audio_uri`, `avatar_uri`) store device URIs.

5. **Google Removal Audit**:
   - **Required Firebase / Google Infra**: Google services Gradle plugin, Google tasks await utility, Firebase Auth/Firestore packages.
   - **Gemini / AI Features**: Gemini API client in `GeminiApiService.kt`.
   - **Obsolete Google Sign-In**: Unused `GoogleAuthProvider` import in `MainActivity.kt`; inactive commented-out Gradle dependencies.
   - **Obsolete Google Drive**: `drive_file_id` column preserved for backward compatibility in Room; legacy unused default parameters in `MainViewModel.kt`.

---

## 4. Verification Results
- `compile_applet`: **SUCCESS** (Compiled cleanly).
- `gradle :app:testDebugUnitTest`: **RAN** (20 tests: 19 passed, 1 failed).
  - Failing test: `ApiKeySecurityTest > testGitignoreProtectsSensitiveFiles` (failed because candidate check checked `app/.gitignore` instead of root `/.gitignore`).

---

## 5. Blockers
- None. Awaiting user approval before applying application code and test adjustments.

---

## 6. Proposed Next Step
Upon user approval:
1. Connect `SettingsScreen.kt` authentication dialog and actions directly to `MainViewModel` (`signInWithEmail`, `registerWithEmail`, `signOut`, `sendPasswordResetEmail`).
2. Remove remote Firestore deletion from `MainViewModel.deleteTranscriptions` so local deletions preserve cloud data.
3. Add `firebase-storage` dependency and prepare configurable cloud audio storage setting.
4. Add Room migration (v12 -> v13) adding `user_id` to `speaker_profiles` and `location_profiles`, and extend Firestore sync to include speaker and location profiles.
5. Fix `ApiKeySecurityTest.kt` gitignore search path and remove unused `GoogleAuthProvider` import in `MainActivity.kt`.
