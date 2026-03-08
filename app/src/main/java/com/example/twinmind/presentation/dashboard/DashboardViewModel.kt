package com.example.twinmind.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind.data.local.dao.MeetingDao
import com.example.twinmind.data.local.entity.MeetingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    meetingDao: MeetingDao
) : ViewModel() {

    // stateIn converts the cold Flow from Room into a hot StateFlow for Compose.
    // SharingStarted.WhileSubscribed(5000) keeps the flow active for 5 seconds
    // after the UI is hidden, preventing unnecessary database queries on quick rotations.
    val meetings: StateFlow<List<MeetingEntity>> = meetingDao.getAllMeetings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recordingState: StateFlow<com.example.twinmind.service.RecordingState> = 
        com.example.twinmind.service.AudioRecorderService.recordingState
}