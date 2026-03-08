package com.example.twinmind.data.remote.model

import com.google.gson.annotations.SerializedName

// --- Requests ---

data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val role: String = "user",
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String // This is where the Base64 audio string goes
)

// --- Responses ---

data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

data class Candidate(
    val content: Content
)

data class GeminiError(
    val code: Int,
    val message: String
)