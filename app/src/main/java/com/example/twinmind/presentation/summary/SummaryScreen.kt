package com.example.twinmind.presentation.summary

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.MeetingEntity
import com.example.twinmind.data.repository.SummaryState
import com.example.twinmind.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    meetingId: String,
    onNavigateBack: () -> Unit = {}
) {
    val summaryState by viewModel.summaryState.collectAsState()
    val meeting by viewModel.meeting.collectAsState()
    val transcriptChunks by viewModel.transcriptChunks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Summary", "Transcript")

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TealPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Meeting Title + Date
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = meeting?.title ?: "Meeting",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dateStr = meeting?.dateStarted?.let {
                    SimpleDateFormat("MMM dd \u2022 h:mm a", Locale.getDefault()).format(Date(it))
                } ?: ""
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) TealPrimary else CardBorder.copy(alpha = 0.5f))
                            .clickable { selectedTab = index }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) SurfaceWhite else TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on selected tab
            when (selectedTab) {
                0 -> SummaryTabContent(
                    summaryState,
                    onRetry = { viewModel.retry() },
                    onRegenerate = { viewModel.regenerate() },
                    transcriptChunks = transcriptChunks
                )
                1 -> TranscriptTabContent(transcriptChunks, meeting)
            }
        }
    }
}

// ──────────────────────────────────────
// SUMMARY TAB
// ──────────────────────────────────────
@Composable
private fun SummaryTabContent(
    summaryState: SummaryState,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    transcriptChunks: List<AudioChunkEntity>
) {
    when (summaryState) {
        is SummaryState.Loading -> LoadingState()
        is SummaryState.Streaming -> StreamingState(summaryState.partialSummary)
        is SummaryState.Error -> ErrorState(summaryState.message, onRetry)
        is SummaryState.Success -> SuccessState(summaryState.finalSummary, transcriptChunks, onRegenerate)
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "loading")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutCubic), RepeatMode.Reverse),
            label = "alpha"
        )
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = TealPrimary, strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Generating summary...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TealPrimary.copy(alpha = alpha)))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Analyzing your transcript and creating\na structured summary", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center))
    }
}

@Composable
private fun StreamingState(partialSummary: String) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            val infiniteTransition = rememberInfiniteTransition(label = "streaming")
            val dotAlpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "dotAlpha")
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TealPrimary.copy(alpha = dotAlpha)))
            Text("Generating...", style = MaterialTheme.typography.labelMedium.copy(color = TealPrimary, fontWeight = FontWeight.Medium))
        }
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(0.dp), border = CardDefaults.outlinedCardBorder()
        ) {
            Text(
                text = formatMarkdown(partialSummary),
                style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary, lineHeight = 26.sp),
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = RecordingRed, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = TealPrimary), modifier = Modifier.height(48.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SuccessState(summary: String, transcriptChunks: List<AudioChunkEntity>, onRegenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val sections = parseSummaryIntoSections(summary)

        // Notes & Summary Card
        SummaryCard(
            icon = Icons.Outlined.Description,
            title = "Notes & Summary",
            content = sections["summary"] ?: summary
        )

        // Transcript preview card
        if (transcriptChunks.isNotEmpty()) {
            val previewText = transcriptChunks.firstOrNull()?.transcript ?: ""
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(0.dp), border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transcript", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TealPrimary))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${transcriptChunks.size} chunks", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Items Card
        sections["action_items"]?.let { actionItems ->
            SummaryCard(icon = Icons.Outlined.CheckCircle, title = "Action Items", content = actionItems)
        }

        // Key Points Card
        sections["key_points"]?.let { keyPoints ->
            SummaryCard(icon = Icons.Outlined.Lightbulb, title = "Key Points", content = keyPoints)
        }

        // Regenerate button — small reload icon
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Regenerate summary",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(icon: ImageVector, title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(0.dp), border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TealPrimary))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formatMarkdown(content.trim()),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, lineHeight = 22.sp)
            )
        }
    }
}

// ──────────────────────────────────────
// TRANSCRIPT TAB
// ──────────────────────────────────────
@Composable
private fun TranscriptTabContent(chunks: List<AudioChunkEntity>, meeting: MeetingEntity?) {
    if (chunks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.MicOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No transcript yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Transcripts will appear here once\naudio has been processed.", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center))
        }
    } else {
        val baseTime = meeting?.dateStarted ?: 0L
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            chunks.forEach { chunk ->
                val chunkTime = baseTime + (chunk.chunkOrder * 30_000L) // each chunk is ~30s
                val timeStr = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(chunkTime))

                Column {
                    // Timestamp header
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold, color = TealPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Transcript text
                    Text(
                        text = chunk.transcript ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary, lineHeight = 24.sp
                        )
                    )
                }
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ──────────────────────────────────────
// HELPER: Parse LLM sections
// ──────────────────────────────────────
private fun parseSummaryIntoSections(rawSummary: String): Map<String, String> {
    val sections = mutableMapOf<String, String>()
    val lines = rawSummary.lines()
    var currentSection = "summary"
    val currentContent = StringBuilder()

    fun flush() {
        if (currentContent.isNotEmpty()) {
            sections[currentSection] = currentContent.toString().trim()
            currentContent.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        val upper = trimmed.uppercase()

        when {
            // Plain text format: TITLE: ...
            upper.startsWith("TITLE:") -> {
                flush(); currentSection = "title"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            upper.startsWith("SUMMARY:") -> {
                flush(); currentSection = "summary"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            upper.startsWith("KEY POINTS:") || upper.startsWith("KEY POINTS") -> {
                flush(); currentSection = "key_points"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            upper.startsWith("ACTION ITEMS:") || upper.startsWith("ACTION ITEMS") -> {
                flush(); currentSection = "action_items"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            // Legacy ** format support
            trimmed.startsWith("**Title") || trimmed.startsWith("**TITLE") -> {
                flush(); currentSection = "title"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            trimmed.startsWith("**Summary") || trimmed.startsWith("**SUMMARY") -> {
                flush(); currentSection = "summary"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            trimmed.startsWith("**Action") || trimmed.startsWith("**action") -> {
                flush(); currentSection = "action_items"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            trimmed.startsWith("**Key") || trimmed.startsWith("**key") -> {
                flush(); currentSection = "key_points"
                val v = trimmed.substringAfter(":").trim().stripMarkdown()
                if (v.isNotEmpty()) currentContent.append(v)
            }
            else -> {
                val cleaned = trimmed.stripMarkdown()
                if (cleaned.isNotEmpty()) {
                    if (currentContent.isNotEmpty()) currentContent.append("\n")
                    currentContent.append(cleaned)
                }
            }
        }
    }
    flush()
    return sections
}

/** Strips raw markdown symbols (**, *, ##) from text while keeping bullet dashes */
private fun String.stripMarkdown(): String {
    return this
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // **bold** → bold
        .replace(Regex("^#{1,6}\\s+"), "")           // ## Heading → Heading
        .replace(Regex("\\*{1,2}"), "")               // orphan * or **
        .trim()
}

// ──────────────────────────────────────
// HELPER: Format Markdown → AnnotatedString
// ──────────────────────────────────────
@Composable
fun formatMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        // First strip any residual ** that aren't paired properly
        val cleaned = text.stripMarkdown()
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val lines = cleaned.lines()
        val formattedText = lines.joinToString("\n") { line ->
            val t = line.trimStart()
            when {
                t.startsWith("- ") -> "\u2022 " + t.drop(2)
                t.startsWith("* ") -> "\u2022 " + t.drop(2)
                else -> line
            }
        }
        var lastIndex = 0
        boldRegex.findAll(formattedText).forEach { matchResult ->
            val startIndex = matchResult.range.first
            append(formattedText.substring(lastIndex, startIndex))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchResult.groupValues[1])
            }
            lastIndex = matchResult.range.last + 1
        }
        append(formattedText.substring(lastIndex))
    }
}