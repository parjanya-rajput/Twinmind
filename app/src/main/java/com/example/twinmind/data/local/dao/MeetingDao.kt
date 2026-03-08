package com.example.twinmind.data.local.dao

import androidx.room.*
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity): Long

    @Query("SELECT * FROM meetings ORDER BY dateStarted DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: String): MeetingEntity?

    @Query("UPDATE meetings SET summary = :summary, actionItems = :actionItems, isCompleted = 1 WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, actionItems: String): Int

    @Query("UPDATE meetings SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String): Int

    @Query("UPDATE meetings SET summary = null, actionItems = null, isCompleted = 0 WHERE id = :id")
    suspend fun clearSummary(id: String): Int
}