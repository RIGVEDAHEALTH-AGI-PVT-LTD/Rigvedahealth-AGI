package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.speech.SpeechRecognitionManager
import com.example.speech.VoiceAction
import com.example.speech.VoiceCommandEngine
import com.example.speech.VoiceCommandResult
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Voice-Command Listener Modal
 *
 * Allows clinicians and doctors to quickly trigger specific actions hands-free:
 * - "Start recording"
 * - "Sync data"
 * - "View queue"
 * - "Open consultation"
 * - "Sync dashboard"
 * - "Scan ABHA"
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandDialog(
    onDismiss: () -> Unit,
    onExecuteAction: (VoiceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val speechManager = remember { SpeechRecognitionManager(context) }

    val isListening by speechManager.isListening.collectAsStateWithLifecycle()
    val rmsDb by speechManager.rmsDb.collectAsStateWithLifecycle()
    val transcript by speechManager.transcript.collectAsStateWithLifecycle()
    val partialTranscript by speechManager.partialTranscript.collectAsStateWithLifecycle()

    var recognizedCommand by remember { mutableStateOf<VoiceCommandResult?>(null) }
    var hasExecuted by remember { mutableStateOf(false) }

    val activeTranscript = remember(transcript, partialTranscript) {
        if (partialTranscript.isNotBlank()) {
            if (transcript.isNotBlank()) "$transcript $partialTranscript" else partialTranscript
        } else {
            transcript
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            speechManager.startListening()
        }
    }

    LaunchedEffect(Unit) {
        if (hasMicPermission) {
            speechManager.startListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    // Keyword detection on active spoken transcript
    LaunchedEffect(activeTranscript) {
        if (activeTranscript.isNotBlank() && !hasExecuted) {
            val match = VoiceCommandEngine.parseCommand(activeTranscript)
            if (match != null) {
                recognizedCommand = match
                hasExecuted = true
                speechManager.stopListening()
                coroutineScope.launch {
                    delay(800) // Brief visual confirmation for the doctor
                    onExecuteAction(match.action)
                    onDismiss()
                }
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("voice_command_dialog")
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryContainerTeal,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) TriageGreen else Color.Gray)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Voice Command Listener",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isListening) "Listening for clinician keywords..." else "Offline Engine Ready",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_close_voice_command")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Voice Command Dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Audio Amplitude Wave / Microphone Aura
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Live Decibel audio amplitude visualizer
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(28.dp)
                        ) {
                            val normalized = ((rmsDb + 2.0f) / 12.0f).coerceIn(0.1f, 1.0f)
                            for (i in 0 until 12) {
                                val heightFactor = if (isListening) {
                                    val wave = kotlin.math.abs(kotlin.math.sin(i * 0.6f + normalized * 3.0f))
                                    (normalized * wave * 24.dp.value).coerceIn(4f, 26f)
                                } else 4f

                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(heightFactor.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) PrimaryTeal else Color(0xFF475569))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (activeTranscript.isNotBlank()) "\"$activeTranscript\"" else "Say a command, e.g. \"Start recording\" or \"Sync data\"",
                            fontSize = 12.sp,
                            fontWeight = if (activeTranscript.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTranscript.isNotBlank()) Color(0xFF7BF5DF) else Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.testTag("voice_command_transcript")
                        )
                    }
                }

                // Command Recognition Success Banner
                AnimatedVisibility(
                    visible = recognizedCommand != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TriageGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TriageGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = TriageGreen)
                            Column {
                                Text(
                                    text = "Executing: ${recognizedCommand?.action?.title}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TriageGreen
                                )
                                Text(
                                    text = recognizedCommand?.action?.description ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Quick Tap Keywords (for doctor convenience or noisy wards)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Or tap a quick action:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoiceActionChip(
                            title = "Start recording",
                            icon = Icons.Default.Edit,
                            onClick = {
                                onExecuteAction(VoiceAction.START_RECORDING)
                                onDismiss()
                            },
                            testTag = "chip_voice_command_start_recording"
                        )

                        VoiceActionChip(
                            title = "Sync data",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                onExecuteAction(VoiceAction.SYNC_DATA)
                                onDismiss()
                            },
                            testTag = "chip_voice_command_sync_data"
                        )

                        VoiceActionChip(
                            title = "View queue",
                            icon = Icons.AutoMirrored.Filled.List,
                            onClick = {
                                onExecuteAction(VoiceAction.VIEW_QUEUE)
                                onDismiss()
                            },
                            testTag = "chip_voice_command_view_queue"
                        )

                        VoiceActionChip(
                            title = "Scan ABHA",
                            icon = Icons.Default.Search,
                            onClick = {
                                onExecuteAction(VoiceAction.SCAN_ABHA)
                                onDismiss()
                            },
                            testTag = "chip_voice_command_scan_abha"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceActionChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryTeal,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
