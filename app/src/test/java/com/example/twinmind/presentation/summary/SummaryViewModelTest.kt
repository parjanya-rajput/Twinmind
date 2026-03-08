package com.example.twinmind.presentation.summary

import androidx.lifecycle.SavedStateHandle
import com.example.twinmind.domain.repository.MeetingRepository
import com.example.twinmind.data.repository.SummaryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    class FakeMeetingRepository : MeetingRepository {
        var emitStates: List<SummaryState> = emptyList()

        override fun streamSummary(meetingId: String): Flow<SummaryState> = flow {
            emitStates.forEach { emit(it) }
        }

        override suspend fun clearSummary(meetingId: String) { }

        override fun regenerateSummary(meetingId: String): Flow<SummaryState> = flow {
            emitStates.forEach { emit(it) }
        }
        
        override suspend fun processChunk(fileUri: String, meetingId: String, chunkOrder: Int) { }
        override suspend fun retryFailedTranscriptions(meetingId: String) { }
        override suspend fun generateFinalSummarySync(meetingId: String) { }
    }

    class FakeMeetingDao : com.example.twinmind.data.local.dao.MeetingDao {
        override suspend fun insert(meeting: com.example.twinmind.data.local.entity.MeetingEntity): Long = 1L
        override fun getAllMeetings(): Flow<List<com.example.twinmind.data.local.entity.MeetingEntity>> = flowOf(emptyList())
        override suspend fun getMeetingById(id: String): com.example.twinmind.data.local.entity.MeetingEntity? = null
        override suspend fun updateSummary(id: String, summary: String, actionItems: String): Int = 1
        override suspend fun updateTitle(id: String, title: String): Int = 1
        override suspend fun clearSummary(id: String): Int = 1
    }

    class FakeAudioChunkDao : com.example.twinmind.data.local.dao.AudioChunkDao {
        override suspend fun insert(chunk: com.example.twinmind.data.local.entity.AudioChunkEntity): Long = 1L
        override suspend fun updateTranscript(id: Int, transcript: String): Int = 1
        override suspend fun getOrderedTranscripts(meetingId: String): List<String> = emptyList()
        override suspend fun getUntranscribedChunks(meetingId: String): List<com.example.twinmind.data.local.entity.AudioChunkEntity> = emptyList()
        override suspend fun updateTranscriptByOrder(meetingId: String, chunkOrder: Int, transcript: String): Int = 1
        override suspend fun getTranscribedChunks(meetingId: String): List<com.example.twinmind.data.local.entity.AudioChunkEntity> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `generateSummary updates UI state normally from Repository Flow`() = runTest(testDispatcher) {
        val fakeRepo = FakeMeetingRepository()
        fakeRepo.emitStates = listOf(SummaryState.Loading, SummaryState.Success("Final Testing Summary"))
        
        val savedState = SavedStateHandle(mapOf("meetingId" to "m1"))
        
        val viewModel = SummaryViewModel(fakeRepo, FakeMeetingDao(), FakeAudioChunkDao(), savedState)
        
        advanceUntilIdle() // let init and flow collection run
        
        val currentState = viewModel.summaryState.value as SummaryState.Success
        assertEquals("Final Testing Summary", currentState.finalSummary)
    }

    @Test
    fun `retry sets Loading state and calls API again`() = runTest(testDispatcher) {
        val fakeRepo = FakeMeetingRepository()
        fakeRepo.emitStates = listOf(SummaryState.Error("Network Failed"))
            
        val savedState = SavedStateHandle(mapOf("meetingId" to "m1"))
        val viewModel = SummaryViewModel(fakeRepo, FakeMeetingDao(), FakeAudioChunkDao(), savedState)
        
        advanceUntilIdle()
        assert(viewModel.summaryState.value is SummaryState.Error)
        
        // Setup retry
        fakeRepo.emitStates = listOf(SummaryState.Success("Retry Succeeded"))
        viewModel.retry()
        advanceUntilIdle()
        
        val currentState = viewModel.summaryState.value as SummaryState.Success
        assertEquals("Retry Succeeded", currentState.finalSummary)
    }
}
