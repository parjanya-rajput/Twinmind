package com.example.twinmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.twinmind.data.local.entity.*
import com.example.twinmind.data.local.dao.*

@Database(entities = [MeetingEntity::class, AudioChunkEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun audioChunkDao(): AudioChunkDao
}