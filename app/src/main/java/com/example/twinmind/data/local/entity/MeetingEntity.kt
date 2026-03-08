package com.example.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val dateStarted: Long,
    val title: String? = null,
    val summary: String? = null,
    val actionItems: String? = null, // Store as JSON string or use TypeConverter
    val isCompleted: Boolean = false
)

