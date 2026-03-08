package com.example.twinmind.data.repository

import android.util.Base64
import android.util.Log
import com.example.twinmind.BuildConfig
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.MeetingDao
import com.example.twinmind.data.remote.ApiService
import com.example.twinmind.data.remote.model.*
import com.example.twinmind.domain.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class MeetingRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val audioChunkDao: AudioChunkDao,
    private val meetingDao: MeetingDao
) : MeetingRepository {

    // Ideally, inject this via a config class or Interceptor, but keeping it here for clarity
    private val apiKey = BuildConfig.GEMINI_API_KEY

    override suspend fun processChunk(fileUri: String, meetingId: String, chunkOrder: Int) = withContext(Dispatchers.IO) {
        // 1. Convert Audio to Base64
        val audioFile = File(fileUri)
        if (!audioFile.exists()) return@withContext

        val base64Audio = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)

        // 2. Build Gemini Multimodal Request
        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Transcribe the following audio accurately. Reply ONLY with the transcription. If there is no speech, reply with [Silence]."),
                        Part(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio))
                    )
                )
            )
        )

        try {
            // 3. Execute Network Call
            val response = apiService.transcribeAudio(apiKey, request)
            val transcript = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

            // 4. Update Room Database (Single Source of Truth)
            if (!transcript.isNullOrEmpty() && transcript != "[Silence]") {
                // Assuming you have a method to find the specific chunk by meetingId and order, or you pass the Chunk ID
                audioChunkDao.updateTranscriptByOrder(meetingId, chunkOrder, transcript)
            } else {
                // Mark as transcribed even if silent so the Worker doesn't keep retrying
                audioChunkDao.updateTranscriptByOrder(meetingId, chunkOrder, "")
            }
        } catch (e: Exception) {
            Log.e("MeetingRepository", "Transcription failed for chunk $chunkOrder", e)
            // We do NOT update isTranscribed to true.
            // The SummaryWorker will pick this up later and retry.
        }
    }

    override suspend fun retryFailedTranscriptions(meetingId: String) {
        val failedChunks = audioChunkDao.getUntranscribedChunks(meetingId)
        failedChunks.forEach { chunk ->
            processChunk(chunk.fileUri, meetingId, chunk.chunkOrder)
        }
    }

    override fun streamSummary(meetingId: String): Flow<SummaryState> = flow {
        // 0. Check if summary is already generated and cached in the DB
        val existingMeeting = meetingDao.getMeetingById(meetingId)
        if (existingMeeting != null && !existingMeeting.summary.isNullOrBlank()) {
            emit(SummaryState.Success(existingMeeting.summary))
            return@flow
        }

        emit(SummaryState.Loading)

        try {
            // 1. Fetch ordered transcripts from Room
            val transcriptsList = audioChunkDao.getOrderedTranscripts(meetingId)
            if (transcriptsList.isEmpty() || transcriptsList.all { it.isBlank() }) {
                emit(SummaryState.Error("No audio transcribed to summarize."))
                return@flow
            }

            val fullTranscript = transcriptsList.joinToString(" ")

            // 2. Build Prompt for Structured Summary
            val prompt = """
                You are a meeting summarizer. Analyze the transcript and respond using ONLY this exact format with NO markdown symbols (no **, no #, no *):

                TITLE: [A concise 5-10 word title describing the main topic]
                SUMMARY: [A brief 2-3 sentence overview of what was discussed]
                KEY POINTS:
                - [Key point 1]
                - [Key point 2]
                - [Key point 3 if applicable]
                ACTION ITEMS:
                - [Task or next step 1]
                - [Task or next step 2 if applicable]

                Transcript: $fullTranscript

                Important: Use ONLY plain text. No bold, no asterisks, no markdown formatting whatsoever.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )

            // 3. Start Network Stream
            val responseBody = apiService.streamSummary(apiKey, request)

            val inputStream = responseBody.byteStream()
            val reader = inputStream.bufferedReader()

            var fullSummaryBuilder = StringBuilder()
            var line: String?

            // 4. Parse Server-Sent Events (SSE) stream
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("data: ")) {
                    val jsonStr = line!!.substring(6).trim()

                    // Gemini sends "data: [DONE]" or similar at the very end occasionally, catch it
                    if (jsonStr == "[DONE]") break

                    try {
                        val jsonObject = JSONObject(jsonStr)
                        val candidates = jsonObject.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val textChunk = parts.getJSONObject(0).optString("text", "")

                                fullSummaryBuilder.append(textChunk)
                                // Emit the continuously growing string to the UI
                                emit(SummaryState.Streaming(fullSummaryBuilder.toString()))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MeetingRepository", "Error parsing SSE chunk", e)
                    }
                }
            }

            reader.close()

            val finalSummaryText = fullSummaryBuilder.toString().trim()

            // 5. Extract title and save to DB
            val extractedTitle = extractTitle(finalSummaryText)
            if (extractedTitle.isNotEmpty()) {
                meetingDao.updateTitle(meetingId, extractedTitle)
            }

            // 6. Save the final summary to Room Database
            meetingDao.updateSummary(
                id = meetingId,
                summary = finalSummaryText,
                actionItems = extractSection(finalSummaryText, "ACTION ITEMS")
            )

            // 7. Emit Final Success State
            emit(SummaryState.Success(finalSummaryText))

        } catch (e: Exception) {
            Log.e("MeetingRepository", "Summary generation failed", e)
            emit(SummaryState.Error("Failed to generate summary. Please check your connection and try again."))
        }
    }.flowOn(Dispatchers.IO) // Ensure the flow runs on a background thread


    // --- Extracts the meeting title from LLM output ---
    private fun extractTitle(fullSummary: String): String {
        val lines = fullSummary.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.uppercase().startsWith("TITLE:")) {
                return trimmed.substringAfter(":").trim()
                    .removePrefix("**").removeSuffix("**").trim()
            }
        }
        return ""
    }

    // --- Extracts a specific named section from LLM output ---
    private fun extractSection(fullSummary: String, sectionName: String): String {
        val lines = fullSummary.lines()
        val builder = StringBuilder()
        var inSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.uppercase().startsWith("$sectionName:")) {
                inSection = true
                continue
            }
            // Stop at next UPPERCASE: section heading
            if (inSection && trimmed.matches(Regex("[A-Z ]+:.*"))) break
            if (inSection && trimmed.isNotEmpty()) builder.appendLine(trimmed)
        }
        return builder.toString().trim()
    }

    // --- Legacy helper kept for generateFinalSummarySync ---
    private fun extractActionItems(fullSummary: String): String {
        return extractSection(fullSummary, "ACTION ITEMS")
    }

    override suspend fun clearSummary(meetingId: String) {
        meetingDao.clearSummary(meetingId)
    }

    override fun regenerateSummary(meetingId: String): Flow<SummaryState> = flow {
        // 1. Clear the cached summary so streamSummary doesn't return it
        meetingDao.clearSummary(meetingId)
        // 2. Re-run the full summary generation from saved audio chunks
        streamSummary(meetingId).collect { state ->
            emit(state)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateFinalSummarySync(meetingId: String) {
        // 1. Fetch ordered transcripts from Room
        val transcriptsList = audioChunkDao.getOrderedTranscripts(meetingId)
        if (transcriptsList.isEmpty() || transcriptsList.all { it.isBlank() }) {
            return // Nothing to summarize
        }

        val fullTranscript = transcriptsList.joinToString(" ")

        // 2. Build the exact same prompt
        val prompt = """
            Read the following transcript and generate a structured summary.
            You MUST use the following format:
            **Title:** [Create a short title]
            **Summary:** [A brief 2-3 sentence overview]
            **Key Points:** [Bullet points of main ideas]
            **Action Items:** [Bullet points of tasks or next steps, if any]
            
            Transcript: $fullTranscript
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            // 3. Make the NON-streaming API call (reusing the same endpoint used for chunks)
            val response = apiService.transcribeAudio(apiKey, request)
            val finalSummaryText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

            if (!finalSummaryText.isNullOrEmpty()) {
                // 4. Save directly to Room Database
                meetingDao.updateSummary(
                    id = meetingId,
                    summary = finalSummaryText,
                    actionItems = extractActionItems(finalSummaryText)
                )
            } else {
                throw Exception("Received empty summary from LLM")
            }
        } catch (e: Exception) {
            Log.e("MeetingRepository", "Sync summary generation failed in background", e)
            // Rethrow the exception so WorkManager knows it failed and will retry
            throw e
        }
    }
}

// Sealed class representing the UI states for the Summary Screen
sealed class SummaryState {
    object Loading : SummaryState()
    data class Streaming(val partialSummary: String) : SummaryState()
    data class Success(val finalSummary: String) : SummaryState()
    data class Error(val message: String) : SummaryState()
}