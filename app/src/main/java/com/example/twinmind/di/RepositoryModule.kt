package com.example.twinmind.di

import com.example.twinmind.data.repository.MeetingRepositoryImpl
import com.example.twinmind.domain.repository.MeetingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMeetingRepository(
        meetingRepositoryImpl: MeetingRepositoryImpl
    ): MeetingRepository
}
