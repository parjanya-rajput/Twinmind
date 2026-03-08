package com.example.twinmind.di

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import androidx.room.Room
import com.example.twinmind.data.local.AppDatabase
import com.example.twinmind.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(app, AppDatabase::class.java, "twinmind_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMeetingDao(db: AppDatabase) = db.meetingDao()

    @Provides
    fun provideAudioChunkDao(db: AppDatabase) = db.audioChunkDao()
}