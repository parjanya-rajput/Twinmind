package com.example.twinmind.data.remote

import com.example.twinmind.data.remote.model.GeminiRequest
import com.example.twinmind.data.remote.model.GeminiResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    /**
     * Standard non-streaming call for Transcribing Audio.
     * We wait for the full transcription to return before saving to Room.
     */
    @POST("models/gemini-2.5-flash:generateContent")
    suspend fun transcribeAudio(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    /**
     * Streaming call for the Summary.
     * Returns a raw ResponseBody so we can read the Server-Sent Events (SSE) chunk by chunk.
     */
    @Streaming
    @POST("models/gemini-2.5-flash:streamGenerateContent?alt=sse")
    suspend fun streamSummary(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): ResponseBody
}