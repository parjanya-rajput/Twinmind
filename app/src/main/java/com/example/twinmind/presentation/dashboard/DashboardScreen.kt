package com.example.twinmind.presentation.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twinmind.data.local.entity.MeetingEntity
import com.example.twinmind.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToRecording: () -> Unit,
    onNavigateToSummary: (String) -> Unit
) {
    val meetings by viewModel.meetings.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "TwinMind",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            // Notes / Chats filter chips
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(TealPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Notes", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = TealPrimary))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meetings list
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (meetings.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.MicNone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No meetings yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap \"Capture Notes\" below to start\nrecording your first meeting",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Group by date
                    val grouped = meetings.groupBy { meeting ->
                        val cal = Calendar.getInstance().apply { timeInMillis = meeting.dateStarted }
                        val today = Calendar.getInstance()
                        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        when {
                            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                            cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(meeting.dateStarted))
                        }
                    }

                    grouped.forEach { (dateLabel, dateMeetings) ->
                        item {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        items(dateMeetings, key = { it.id }) { meeting ->
                            MeetingCard(meeting = meeting, onClick = { onNavigateToSummary(meeting.id) })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Bottom "Capture Notes" Button
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = onNavigateToRecording,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = SurfaceWhite),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (recordingState == com.example.twinmind.service.RecordingState.RECORDING) "Capturing notes..." else "Capture Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingCard(meeting: MeetingEntity, onClick: () -> Unit) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(meeting.dateStarted))

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title + Time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title ?: "Untitled",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            // Chevron
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}