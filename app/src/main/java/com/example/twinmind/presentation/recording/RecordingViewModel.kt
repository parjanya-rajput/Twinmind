package com.example.twinmind.presentation.recording

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind.data.local.dao.MeetingDao
import com.example.twinmind.data.local.entity.MeetingEntity
import com.example.twinmind.service.AudioRecorderService
import com.example.twinmind.service.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val application: Application,
    private val meetingDao: MeetingDao
) : AndroidViewModel(application) {

    // Observing the static StateFlows from your Foreground Service
    val recordingState: StateFlow<RecordingState> = AudioRecorderService.recordingState

    // To track the current active meeting ID natively across the singleton service
    val currentMeetingId: StateFlow<String?> = AudioRecorderService.currentMeetingId

    // 10s silence detection flag
    val isSilent: StateFlow<Boolean> = AudioRecorderService.isSilentState

    val timer: StateFlow<Long> = AudioRecorderService.timer

    fun startRecording(title: String = "New Meeting") {
        if (recordingState.value != RecordingState.IDLE) return // Prevent restart if already running
        // We only generate a UUID here to give to the Service as a startup command. The Service will push it to its companion Flow.
        val newMeetingId = UUID.randomUUID().toString()

        // 1. Save the new meeting to the database immediately
        viewModelScope.launch(Dispatchers.IO) {
            val newMeeting = MeetingEntity(
                id = newMeetingId,
                dateStarted = System.currentTimeMillis(),
                title = title
            )
            meetingDao.insert(newMeeting)
        }

        // 2. Start the Foreground Service and pass the Meeting ID
        val intent = Intent(application, AudioRecorderService::class.java).apply {
            action = "START"
            putExtra("MEETING_ID", newMeetingId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        }
    }

    fun pauseRecording() {
        sendCommandToService("PAUSE")
    }

    fun resumeRecording() {
        sendCommandToService("RESUME")
    }

    fun stopRecording() {
        sendCommandToService("STOP")
    }

    private fun sendCommandToService(action: String) {
        val intent = Intent(application, AudioRecorderService::class.java).apply {
            this.action = action
        }
        application.startService(intent)
    }
}