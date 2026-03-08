package com.example.twinmind.presentation.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.MeetingDao
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.MeetingEntity
import com.example.twinmind.domain.repository.MeetingRepository
import com.example.twinmind.data.repository.SummaryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val meetingId: String = checkNotNull(savedStateHandle["meetingId"])

    private val _summaryState = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val summaryState: StateFlow<SummaryState> = _summaryState.asStateFlow()

    private val _meeting = MutableStateFlow<MeetingEntity?>(null)
    val meeting: StateFlow<MeetingEntity?> = _meeting.asStateFlow()

    private val _transcriptChunks = MutableStateFlow<List<AudioChunkEntity>>(emptyList())
    val transcriptChunks: StateFlow<List<AudioChunkEntity>> = _transcriptChunks.asStateFlow()

    init {
        loadMeeting()
        loadTranscript()
        generateSummary()
    }

    private fun loadMeeting() {
        viewModelScope.launch {
            _meeting.value = meetingDao.getMeetingById(meetingId)
        }
    }

    private fun loadTranscript() {
        viewModelScope.launch {
            _transcriptChunks.value = audioChunkDao.getTranscribedChunks(meetingId)
        }
    }

    fun generateSummary() {
        viewModelScope.launch {
            meetingRepository.streamSummary(meetingId).collect { state ->
                _summaryState.value = state
                // Refresh meeting data when summary is generated
                if (state is SummaryState.Success) {
                    _meeting.value = meetingDao.getMeetingById(meetingId)
                }
            }
        }
    }

    fun retry() {
        _summaryState.value = SummaryState.Loading
        generateSummary()
    }

    /**
     * Regenerates the summary from saved audio chunks.
     * Clears the old cached summary and calls the LLM fresh.
     */
    fun regenerate() {
        _summaryState.value = SummaryState.Loading
        viewModelScope.launch {
            meetingRepository.regenerateSummary(meetingId).collect { state ->
                _summaryState.value = state
                if (state is SummaryState.Success) {
                    _meeting.value = meetingDao.getMeetingById(meetingId)
                }
            }
        }
    }
}