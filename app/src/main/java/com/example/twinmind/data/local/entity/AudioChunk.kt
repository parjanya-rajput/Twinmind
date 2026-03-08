package com.example.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_chunks")
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: String,
    val fileUri: String,
    val transcript: String? = null,
    val isTranscribed: Boolean = false,
    val chunkOrder: Int // Important for maintaining order
)
