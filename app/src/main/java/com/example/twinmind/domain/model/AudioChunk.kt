package com.example.twinmind.domain.model

data class AudioChunk(
    val id: Int,
    val meetingId: String,
    val fileUri: String,
    val transcript: String?,
    val isTranscribed: Boolean,
    val chunkOrder: Int
)