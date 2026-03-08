package com.example.twinmind.domain.repository

import com.example.twinmind.data.repository.SummaryState
import kotlinx.coroutines.flow.Flow

interface MeetingRepository {

    /**
     * Takes a raw audio file URI, sends it to the API, and saves the transcript.
     */
    suspend fun processChunk(fileUri: String, meetingId: String, chunkOrder: Int)

    /**
     * Called by the WorkManager to retry any chunks that failed due to network errors.
     */
    suspend fun retryFailedTranscriptions(meetingId: String)

    /**
     * Streams the LLM summary generation directly to the UI.
     */
    fun streamSummary(meetingId: String): Flow<SummaryState>

    /**
     * Clears the cached summary so it can be regenerated from the saved chunks.
     */
    suspend fun clearSummary(meetingId: String)

    /**
     * Clears the cached summary, then re-streams a new one from the saved audio chunks.
     */
    fun regenerateSummary(meetingId: String): Flow<SummaryState>

    suspend fun generateFinalSummarySync(meetingId: String)
}