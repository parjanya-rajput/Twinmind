package com.example.twinmind.data.local.dao

import androidx.room.*
import com.example.twinmind.data.local.entity.AudioChunkEntity

@Dao
interface AudioChunkDao {
    @Insert
    suspend fun insert(chunk: AudioChunkEntity): Long

    @Query("UPDATE audio_chunks SET transcript = :transcript, isTranscribed = 1 WHERE id = :id")
    suspend fun updateTranscript(id: Int, transcript: String): Int

    @Query("SELECT transcript FROM audio_chunks WHERE meetingId = :meetingId AND isTranscribed = 1 AND transcript IS NOT NULL ORDER BY chunkOrder ASC")
    suspend fun getOrderedTranscripts(meetingId: String): List<String>

    @Query("SELECT * FROM audio_chunks WHERE isTranscribed = 0 AND meetingId = :meetingId")
    suspend fun getUntranscribedChunks(meetingId: String): List<AudioChunkEntity>

    @Query("UPDATE audio_chunks SET transcript = :transcript, isTranscribed = 1 WHERE meetingId = :meetingId AND chunkOrder = :chunkOrder")
    suspend fun updateTranscriptByOrder(meetingId: String, chunkOrder: Int, transcript: String): Int

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId AND isTranscribed = 1 AND transcript IS NOT NULL AND transcript != '' ORDER BY chunkOrder ASC")
    suspend fun getTranscribedChunks(meetingId: String): List<AudioChunkEntity>
}
