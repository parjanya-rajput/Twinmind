package com.example.twinmind.presentation.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmind.service.RecordingState
import com.example.twinmind.ui.theme.*

@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel = hiltViewModel(),
    onNavigateToSummary: (String) -> Unit = {}
) {
    val state by viewModel.recordingState.collectAsState()
    val time by viewModel.timer.collectAsState()
    val meetingId by viewModel.currentMeetingId.collectAsState()
    val isSilent by viewModel.isSilent.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Scaffold(containerColor = BackgroundLight) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = when (state) {
                        RecordingState.RECORDING -> "I'm listening and taking notes..."
                        RecordingState.PAUSED, RecordingState.PAUSED_PHONE, RecordingState.PAUSED_FOCUS -> "Recording paused"
                        else -> "Ready to record"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold, color = TealPrimary, lineHeight = 32.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getCurrentDateTime(),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transcript Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(0.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                            Text("Transcript", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TealPrimary))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state == RecordingState.RECORDING) {
                                Box(modifier = Modifier.size(10.dp).scale(pulseScale).clip(CircleShape).background(RecordingRed))
                            }
                            Text(formatTime(time), style = MaterialTheme.typography.bodyMedium.copy(color = if (state == RecordingState.RECORDING) TextPrimary else TextSecondary))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (state) {
                            RecordingState.RECORDING -> "The transcript and summary will be shown once the meeting is stopped. Tap Stop to stop the Recording "
                            RecordingState.PAUSED, RecordingState.PAUSED_PHONE, RecordingState.PAUSED_FOCUS -> "Recording is paused. Tap Resume to continue."
                            else -> "Tap the Record button below to start capturing audio."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 22.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Card for edge cases
            if (state is RecordingState.PAUSED_PHONE || state is RecordingState.PAUSED_FOCUS || state is RecordingState.ERROR || isSilent) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            state is RecordingState.ERROR -> RecordingRed.copy(alpha = 0.08f)
                            isSilent -> RecordingRed.copy(alpha = 0.08f)
                            else -> WarningYellow.copy(alpha = 0.08f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = when {
                                state is RecordingState.ERROR -> Icons.Outlined.ErrorOutline
                                isSilent -> Icons.Outlined.MicOff
                                else -> Icons.Outlined.PauseCircleOutline
                            },
                            contentDescription = null,
                            tint = when {
                                state is RecordingState.ERROR -> RecordingRed
                                isSilent -> RecordingRed
                                else -> OrangeAccent
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = when {
                                isSilent -> "No audio detected — Check microphone"
                                state is RecordingState.PAUSED_PHONE -> "Paused \u2014 Phone call detected"
                                state is RecordingState.PAUSED_FOCUS -> "Paused \u2014 Audio focus lost"
                                state is RecordingState.ERROR -> (state as RecordingState.ERROR).message
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    state is RecordingState.ERROR -> RecordingRed
                                    isSilent -> RecordingRed
                                    else -> OrangeAccent
                                }
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Controls Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer pill
                Row(
                    modifier = Modifier
                        .weight(1f).height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (state == RecordingState.RECORDING) TealPrimary else TealPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    if (state == RecordingState.RECORDING) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = RecordingRed, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = formatTime(time),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (state == RecordingState.RECORDING) SurfaceWhite else TealPrimary
                        )
                    )
                }

                // Main Action Button
                Button(
                    onClick = {
                        when (state) {
                            RecordingState.RECORDING -> viewModel.pauseRecording()
                            RecordingState.PAUSED, RecordingState.PAUSED_PHONE, RecordingState.PAUSED_FOCUS -> viewModel.resumeRecording()
                            else -> viewModel.startRecording()
                        }
                    },
                    modifier = Modifier.height(52.dp), shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (state) { RecordingState.RECORDING -> OrangeAccent; else -> TealPrimary }
                    )
                ) {
                    Icon(
                        imageVector = when (state) {
                            RecordingState.RECORDING -> Icons.Default.Pause
                            RecordingState.PAUSED, RecordingState.PAUSED_PHONE, RecordingState.PAUSED_FOCUS -> Icons.Default.PlayArrow
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (state) {
                            RecordingState.RECORDING -> "Pause"
                            RecordingState.PAUSED, RecordingState.PAUSED_PHONE, RecordingState.PAUSED_FOCUS -> "Resume"
                            else -> "Record"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Stop Button → navigates to summary
                if (state == RecordingState.RECORDING || state == RecordingState.PAUSED || state == RecordingState.PAUSED_PHONE || state == RecordingState.PAUSED_FOCUS) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(RecordingRed.copy(alpha = 0.1f))
                            .clickable {
                                val id = meetingId
                                viewModel.stopRecording()
                                if (id != null) {
                                    onNavigateToSummary(id)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = RecordingRed, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

fun formatTime(timeInSeconds: Long): String {
    val minutes = timeInSeconds / 60
    val seconds = timeInSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun getCurrentDateTime(): String {
    val dateFormat = java.text.SimpleDateFormat("MMM dd \u2022 h:mm a", java.util.Locale.getDefault())
    return dateFormat.format(java.util.Date())
}