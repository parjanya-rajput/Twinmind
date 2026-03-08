# TwinMind Android Application

## Project Overview
TwinMind is a voice recording and intelligent notes application that records meetings, meetings, and daily chats, transcribes them in real-time or near real-time, and generates structured summaries using Google's Gemini LLM. 

**Minimum SDK:** API 24 (Android 7.0)
**Tech Stack:** Kotlin, Jetpack Compose, MVVM, Coroutines & Flow, Hilt, Room, Retrofit

## Core Features
1. **Robust Audio Recording:** 
    - Uses a `ForegroundService` to continuously record audio.
    - Splits audio into smaller ~30-second chunks for progressive processing.
    - Handles critical edge cases:
        - **Phone calls:** Automatically pauses recording during incoming/outgoing calls using `TelephonyCallback` / `PhoneStateListener`.
        - **Audio Focus Loss:** Pauses or reacts gracefully when other apps (like music players) hijack the microphone.
2. **Transcription (Gemini API):**
    - Audio chunks are securely encoded into Base64 and shipped to the Gemini Multimodal API (using `gemini-1.5-flash` or similar models).
    - Returns structured text which is persisted progressively per chunk.
3. **Smart Summary (Gemini API):**
    - Concatenates ordered, transcribed chunks.
    - Instructs the LLM to provide a clean, structured output (Title, Summary, Key Points, Action Items).
    - Uses SSE (Server-Sent Events) to stream the summary generation live to the UI as it's being produced.
    - Supports **Retrieval/Regeneration:** If the LLM generates a poor summary, users can explicitly regenerate it without re-transcribing the underlying audio.

## Architecture Pattern: MVVM + Clean Architecture principles
The app follows the recommended Android Architecture guidelines, employing **MVVM** and **Unidirectional Data Flow (UDF)**.

### Layers

#### 1. Presentation Layer (`presentation/`)
- Built entirely with **Jetpack Compose**.
- Screens (`DashboardScreen`, `RecordingScreen`, `SummaryScreen`) observe state from their respective ViewModels.
- ViewModels expose state via `StateFlow` and handle user intents/events. Navigation is handled centrally via `AppNavigation` using Compose Navigation.

#### 2. Domain Layer (`domain/`)
- Contains interface contracts such as `MeetingRepository`.
- Acts as the abstraction layer so the presentation layer doesn't need to know about the REST APIs or local Databases directly.

#### 3. Data Layer (`data/`)
- **Local (`data/local/`)**: Uses **Room** database.
    - `MeetingEntity`: Stores high-level meeting metadata, final generated titles, and the final streamed summary text.
    - `AudioChunkEntity`: Stores chunk metadata (URI paths, exact ordering index, completion flags) and the specific transcript sub-text for that chunk.
- **Remote (`data/remote/`)**: Uses **Retrofit** and OkHttp.
    - Handles asynchronous REST calls and SSE streaming via API interfaces.
- **Repository Implementations**: `MeetingRepositoryImpl` stitches together Room (Single Source of Truth) and Retrofit. It fetches audio, hits the API, and caches the results in Room.

#### 4. Services Layer (`service/`)
- Contains the `AudioRecorderService`, extending Android's `Service`.
- Runs as a Foreground Service showing an ongoing notification with play/pause/stop actions and a live timer.
- Bound to the RecordingViewModel to transmit real-time states (e.g., `RECORDING`, `PAUSED_PHONE`, `PAUSED_FOCUS`).

## Data Flow Example (Recording & Summarizing)
1. **User taps Record:** `RecordingViewModel` sends a start command to `AudioRecorderService`.
2. **Service runs:** Captures raw bytes, writes them to internal storage in chunks. 
3. **Database insertion:** For each completed chunk file, it inserts an `AudioChunkEntity` into Room.
4. **Transcription:** Once a chunk is written, `MeetingRepository.processChunk` fires up Retrofit to send it to Gemini.
5. **UI Updates:** The chunk transcript is returned and saved to Room. Any active UI listening to the database emits the updated transcript.
6. **Stop & Summarize:** On stop, the UI navigates to `SummaryScreen`. `SummaryViewModel` triggers `MeetingRepository.streamSummary()`.
7. **Streaming response:** The Repository streams SSE tokens dynamically to the UI and eventually saves the finalized structured response to the `MeetingEntity` in Room.

## Fallback & Edge Case Strategies
- **Process Death:** Ongoing persistence via the Room DB ensures audio chunks are safely retained.
- **Network Failures during Transcription:** Chunk transitions fail gracefully. They are left mathematically marked as `isTranscribed = false`. A `retryFailedTranscriptions` block logic easily picks them back up later.
- **Bad LLM outputs:** A manual hard-reset (`regenerateSummary`) strips the `MeetingEntity`'s summary fields and forces a fresh generation directly from the underlying cached `AudioChunkEntity` transcripts.
