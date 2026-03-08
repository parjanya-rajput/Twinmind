package com.example.twinmind.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.twinmind.domain.repository.MeetingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meetingRepository: MeetingRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString("MEETING_ID") ?: return Result.failure()

        return try {
            // 1. Tell repository to find any untranscribed chunks for this meeting and transcribe them
            meetingRepository.retryFailedTranscriptions(meetingId)

            // 2. Generate the final summary
            meetingRepository.generateFinalSummarySync(meetingId)

            Result.success()
        } catch (e: Exception) {
            // If it fails, WorkManager will back off and retry later based on constraints
            Result.retry()
        }
    }
}