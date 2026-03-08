package com.example.twinmind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.twinmind.MainActivity
import com.example.twinmind.R
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.domain.repository.MeetingRepository
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class AudioRecorderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var isCurrentlySilent = false
    private var currentMeetingId: String? = null
    private var chunkCounter = 0

    @Inject lateinit var audioManager: AudioManager
    @Inject lateinit var telephonyManager: TelephonyManager
    @Inject lateinit var audioChunkDao: AudioChunkDao
    @Inject lateinit var meetingRepository: MeetingRepository

    companion object {
        const val CHANNEL_ID = "RecordingChannel"
        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.IDLE)
        val recordingState = _recordingState.asStateFlow()

        private val _isSilentState = MutableStateFlow(false)
        val isSilentState = _isSilentState.asStateFlow()

        private val _currentMeetingId = MutableStateFlow<String?>(null)
        val currentMeetingId = _currentMeetingId.asStateFlow()

        private val _timer = MutableStateFlow(0L)
        val timer = _timer.asStateFlow()

        const val SAMPLE_RATE = 44100
        const val CHUNK_DURATION_MS = 30_000L
        const val OVERLAP_DURATION_MS = 2_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                _currentMeetingId.value = intent.getStringExtra("MEETING_ID")
                chunkCounter = 0
                _timer.value = 0L
                startRecording()
            }
            "PAUSE" -> pauseRecording()
            "RESUME" -> resumeRecording()
            "STOP" -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (!checkStorage()) {
            updateState(RecordingState.ERROR("Recording stopped - Low storage"))
            stopSelf()
            return
        }

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateState(RecordingState.ERROR("Recording stopped - Permission denied"))
            stopSelf()
            return
        }

        setupEdgeCaseListeners()
        startForeground(1, createNotification("Recording..."))

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        updateState(RecordingState.RECORDING)

        // Live notification timer update
        serviceScope.launch {
            while (isRecording) {
                if (!isPaused) {
                    val seconds = _timer.value
                    val mm = seconds / 60
                    val ss = seconds % 60
                    val timeStr = String.format(java.util.Locale.getDefault(), "%d:%02d", mm, ss)
                    
                    if (isCurrentlySilent) {
                        updateNotification("No audio detected - Check microphone ($timeStr)")
                    } else {
                        updateNotification("Recording... $timeStr")
                    }
                    _timer.value += 1
                }
                delay(1000)
            }
        }

        serviceScope.launch {
            processAudioChunks(bufferSize)
        }
    }

    private suspend fun processAudioChunks(bufferSize: Int) {
        val audioData = ShortArray(bufferSize)
        var silenceTimer = 0L
        var chunkFile = createNewChunkFile()
        var outputStream = FileOutputStream(chunkFile)
        var pcmBytesWritten = 0L
        var startTime = System.currentTimeMillis()

        // Write WAV header placeholder (will be updated when chunk is finalized)
        val wavHeaderSize = 44
        outputStream.write(ByteArray(wavHeaderSize))

        // Overlap buffer (2 seconds worth of samples)
        val overlapBytes = SAMPLE_RATE * 2
        val overlapBuffer = CircularShortArray(overlapBytes)

        while (isRecording) {
            if (isPaused) {
                delay(100)
                continue
            }

            val readResult = audioRecord?.read(audioData, 0, bufferSize) ?: 0
            if (readResult > 0) {
                // 1. Detect Silence
                if (isSilent(audioData, readResult)) {
                    silenceTimer += (readResult * 1000L / SAMPLE_RATE)
                    if (silenceTimer > 10_000) {
                        isCurrentlySilent = true
                        _isSilentState.value = true
                    }
                } else {
                    silenceTimer = 0
                    isCurrentlySilent = false
                    _isSilentState.value = false
                }

                // 2. Check Storage Space (periodically block if low)
                if ((pcmBytesWritten % (SAMPLE_RATE * 5)) == 0L) { // Check roughly every few seconds
                    if (!checkStorage()) {
                        updateState(RecordingState.ERROR("Recording stopped - Low storage"))
                        stopRecording()
                        break
                    }
                }

                // Write PCM data to file (after WAV header)
                val byteData = shortArrayToByteArray(audioData, readResult)
                outputStream.write(byteData)
                pcmBytesWritten += byteData.size
                overlapBuffer.add(audioData, readResult)

                // 2. Chunking Logic (30s chunks)
                if (System.currentTimeMillis() - startTime >= CHUNK_DURATION_MS) {
                    outputStream.close()
                    finalizeWavHeader(chunkFile, pcmBytesWritten)
                    uploadChunkReady(chunkFile)

                    // Start new chunk with overlap
                    chunkFile = createNewChunkFile()
                    outputStream = FileOutputStream(chunkFile)
                    outputStream.write(ByteArray(wavHeaderSize)) // WAV header placeholder
                    val overlapData = shortArrayToByteArray(overlapBuffer.get(), overlapBuffer.size)
                    outputStream.write(overlapData)
                    pcmBytesWritten = overlapData.size.toLong()
                    startTime = System.currentTimeMillis()
                }
            }
        }
        outputStream.close()
        finalizeWavHeader(chunkFile, pcmBytesWritten)
        uploadChunkReady(chunkFile) // Finalize last chunk
    }

    // --- Edge Case Handlers ---

    private fun setupEdgeCaseListeners() {
        // 1. Phone Calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK -> {
                            pauseRecording()
                            updateState(RecordingState.PAUSED_PHONE)
                        }
                        TelephonyManager.CALL_STATE_IDLE -> resumeRecording()
                    }
                }
            })
        }

        // 2. Audio Focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener { focusChange: Int ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            pauseRecording()
                            updateState(RecordingState.PAUSED_FOCUS)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> resumeRecording()
                    }
                }.build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange: Int ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            pauseRecording()
                            updateState(RecordingState.PAUSED_FOCUS)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> resumeRecording()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        // 3. Audio Device Changes (Headsets)
        audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateNotification("Audio source changed (Connected)")
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateNotification("Audio source changed (Disconnected)")
            }
        }, null)
    }

    private fun createNotification(statusText: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Create Channel (Required for Android O and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW // Low importance so it doesn't make a sound every time it updates
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Intent to open the app when tapping the notification itself
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Action Intents (Stop, Pause, Resume)
        val stopIntent = Intent(this, AudioRecorderService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, AudioRecorderService::class.java).apply { action = "PAUSE" }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resumeIntent = Intent(this, AudioRecorderService::class.java).apply { action = "RESUME" }
        val resumePendingIntent = PendingIntent.getService(
            this, 3, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Build the Notification based on current state
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TwinMind Recording")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your actual drawable
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Add dynamic buttons based on whether we are recording or paused
        if (isRecording && !isPaused) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else if (isRecording && isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }

        // Stop button is always available
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        return builder.build()
    }

    // Helper to update the notification text dynamically (e.g., when a phone call pauses it)
    private fun updateNotification(statusText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification(statusText))
    }

    private fun isSilent(audioData: ShortArray, readSize: Int): Boolean {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += audioData[i] * audioData[i]
        }
        val rms = sqrt(sum / readSize)
        // 50 is too low for most phone mics. A threshold of 250-300 catches "quiet rooms"
        return rms < 50.0
    }

    private fun checkStorage(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val megAvailable = bytesAvailable / (1024 * 1024)
        return megAvailable > 100 // Stop if less than 100MB
    }

    private fun updateState(state: RecordingState) {
        _recordingState.value = state
    }

    private fun pauseRecording() {
        if (isRecording && !isPaused) {
            isPaused = true
            updateState(RecordingState.PAUSED)
            updateNotification("Recording Paused")
        }
    }

    private fun resumeRecording() {
        if (isRecording && isPaused) {
            isPaused = false
            updateState(RecordingState.RECORDING)
            updateNotification("Recording...")
        }
    }

    private fun stopRecording() {
        if (!isRecording && !isPaused) return
        isRecording = false
        isPaused = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _isSilentState.value = false

        // Enqueue the SummaryWorker to transcribe chunks and generate summary
        _currentMeetingId.value?.let { meetingId ->
            val workRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf("MEETING_ID" to meetingId))
                .build()
            WorkManager.getInstance(this).enqueue(workRequest)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        updateState(RecordingState.IDLE)
        _currentMeetingId.value = null
        _timer.value = 0L
    }

    private fun createNewChunkFile(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Chunks")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "chunk_${System.currentTimeMillis()}.wav")
    }

    private fun uploadChunkReady(file: File) {
        val meetingId = _currentMeetingId.value ?: return
        val chunkOrder = chunkCounter++
        serviceScope.launch {
            val chunk = AudioChunkEntity(
                meetingId = meetingId,
                fileUri = file.absolutePath,
                chunkOrder = chunkOrder
            )
            audioChunkDao.insert(chunk)
            // Transcribe immediately in the background
            try {
                meetingRepository.processChunk(file.absolutePath, meetingId, chunkOrder)
            } catch (e: Exception) {
                android.util.Log.e("AudioRecorderService", "Chunk transcription failed, will retry later", e)
            }
        }
    }

    /**
     * Writes a complete WAV header to the beginning of the file.
     * Must be called after all PCM data has been written.
     */
    private fun finalizeWavHeader(file: File, pcmDataSize: Long) {
        val raf = RandomAccessFile(file, "rw")
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + 36 // 44 - 8

        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE(totalDataLen.toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16) // PCM chunk size
        raf.writeShortLE(1) // Audio format: PCM
        raf.writeShortLE(channels)
        raf.writeIntLE(SAMPLE_RATE)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(blockAlign)
        raf.writeShortLE(bitsPerSample)
        raf.writeBytes("data")
        raf.writeIntLE(pcmDataSize.toInt())
        raf.close()
    }

    /** Write a 32-bit int in little-endian order */
    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    /** Write a 16-bit short in little-endian order */
    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun shortArrayToByteArray(shortArray: ShortArray, size: Int): ByteArray {
        val byteArray = ByteArray(size * 2)
        for (i in 0 until size) {
            val s = shortArray[i].toInt()
            byteArray[i * 2] = (s and 0x00FF).toByte()
            byteArray[i * 2 + 1] = (s shr 8).toByte()
        }
        return byteArray
    }

    class CircularShortArray(val capacity: Int) {
        private val buffer = ShortArray(capacity)
        private var head = 0
        private var tail = 0
        private var isFull = false

        val size: Int
            get() = if (isFull) capacity else if (tail >= head) tail - head else capacity - head + tail

        fun add(data: ShortArray, length: Int) {
            for (i in 0 until length) {
                buffer[tail] = data[i]
                tail = (tail + 1) % capacity
                if (tail == head) {
                    isFull = true
                    head = (head + 1) % capacity
                }
            }
        }

        fun get(): ShortArray {
            val count = size
            if (count == 0) return ShortArray(0)
            val result = ShortArray(count)
            var curr = head
            for (i in 0 until count) {
                result[i] = buffer[curr]
                curr = (curr + 1) % capacity
            }
            return result
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Process death recovery: When the app is swiped away from Recents, finalize chunk
        if (isRecording) {
            stopRecording()
        } else {
            stopSelf()
        }
    }
}

// State Wrapper
sealed class RecordingState {
    object IDLE : RecordingState()
    object RECORDING : RecordingState()
    object PAUSED : RecordingState()
    object PAUSED_PHONE : RecordingState()
    object PAUSED_FOCUS : RecordingState()
    data class ERROR(val message: String) : RecordingState()
}