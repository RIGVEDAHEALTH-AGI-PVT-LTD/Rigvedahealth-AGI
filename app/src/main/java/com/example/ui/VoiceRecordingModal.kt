package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ObjectiveData
import com.example.TriageTier
import com.example.ai.GeminiClinicalParser
import com.example.clinical.OfflineClinicalCommandEngine
import com.example.speech.SpeechRecognitionManager
import com.example.ui.theme.OnPrimaryContainerTeal
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageRed
import com.example.ui.theme.TriageYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceRecordingModal(
    patientAge: Int,
    patientGender: String,
    isPregnant: Boolean,
    currentVitals: ObjectiveData?,
    onParsedSoapResult: (GeminiClinicalParser.GeminiSoapResult, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val speechManager = remember { SpeechRecognitionManager(context) }

    var selectedLangCode by remember { mutableStateOf("te-IN") } // Default Telugu
    var isProcessing by remember { mutableStateOf(false) }
    var fallbackNotice by remember { mutableStateOf<String?>(null) }

    // Smooth modal enter/exit animation state
    var isModalContentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Trigger smooth fade-in after sheet attaches
        delay(60)
        isModalContentVisible = true
    }

    fun dismissWithAnimation() {
        coroutineScope.launch {
            isModalContentVisible = false
            delay(180)
            speechManager.stopListening()
            onDismiss()
        }
    }

    val transcript by speechManager.transcript.collectAsStateWithLifecycle()
    val partialTranscript by speechManager.partialTranscript.collectAsStateWithLifecycle()
    val isListening by speechManager.isListening.collectAsStateWithLifecycle()
    val rmsDb by speechManager.rmsDb.collectAsStateWithLifecycle()
    val detectedEmergencyKeywords by speechManager.detectedEmergencyKeywords.collectAsStateWithLifecycle()
    val speechError by speechManager.error.collectAsStateWithLifecycle()

    val currentFullText = if (partialTranscript.isNotBlank()) {
        if (transcript.isNotBlank()) "$transcript $partialTranscript" else partialTranscript
    } else {
        transcript
    }

    // Real-time offline keyword analysis of current speech
    val recognizedKeywords = remember(currentFullText) {
        if (currentFullText.isNotBlank()) {
            OfflineClinicalCommandEngine.analyzeLiveSpeech(currentFullText)
        } else {
            null
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) speechManager.startListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    // Process with Gemini (with automatic zero-latency offline fallback)
    fun processIntake(useOfflineDirectly: Boolean = false) {
        val textToProcess = currentFullText.trim()
        if (textToProcess.isBlank()) return

        speechManager.stopListening()
        isProcessing = true
        fallbackNotice = null

        coroutineScope.launch {
            if (useOfflineDirectly) {
                // Direct offline execution
                val localResult = OfflineClinicalCommandEngine.processOfflineClinicalIntake(
                    transcript = textToProcess,
                    patientAge = patientAge,
                    patientGender = patientGender,
                    isPregnant = isPregnant,
                    vitals = currentVitals
                )
                isProcessing = false
                onParsedSoapResult(localResult, textToProcess)
            } else {
                try {
                    val result = GeminiClinicalParser.parseTranscriptToSoap(
                        transcript = textToProcess,
                        patientAge = patientAge,
                        patientGender = patientGender,
                        isPregnant = isPregnant,
                        vitals = currentVitals
                    )
                    isProcessing = false
                    onParsedSoapResult(result, textToProcess)
                } catch (e: Exception) {
                    // Seamless offline fallback when offline or API unreachable
                    fallbackNotice = "Gemini API unavailable. Falling back to on-device clinical engine."
                    val fallbackResult = OfflineClinicalCommandEngine.processOfflineClinicalIntake(
                        transcript = textToProcess,
                        patientAge = patientAge,
                        patientGender = patientGender,
                        isPregnant = isPregnant,
                        vitals = currentVitals
                    )
                    isProcessing = false
                    onParsedSoapResult(fallbackResult, textToProcess)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithAnimation() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag("voice_recording_modal")
    ) {
        AnimatedVisibility(
            visible = isModalContentVisible,
            enter = fadeIn(animationSpec = tween(280, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f)),
            exit = fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                    scaleOut(targetScale = 0.96f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Push-to-Talk Clinical Intake",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PrimaryContainerTeal
                            ) {
                                Text(
                                    text = "Offline-Ready",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimaryContainerTeal,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Telugu / Hindi / English code-switching with real-time spectrum feedback",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { dismissWithAnimation() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Voice Modal")
                    }
                }

                // Regional Language Selector Pills (Minimum 48dp touch target)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf(
                        Triple("te-IN", "తెలుగు (Telugu)", "te"),
                        Triple("hi-IN", "हिंदी (Hindi)", "hi"),
                        Triple("en-IN", "English (India)", "en")
                    )

                    languages.forEach { (code, label, _) ->
                        val isSelected = selectedLangCode == code
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedLangCode = code
                                    speechManager.setLanguage(code)
                                    if (isListening) {
                                        speechManager.stopListening()
                                        speechManager.startListening()
                                    }
                                },
                            color = if (isSelected) PrimaryContainerTeal else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OnPrimaryContainerTeal else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Visual Audio Amplitude Waveform Overlay when mic is active
                AudioWaveformOverlay(
                    isVisible = isListening,
                    rmsDb = rmsDb,
                    transcript = transcript,
                    partialTranscript = partialTranscript,
                    onStopListening = { speechManager.stopListening() },
                    onCancel = {
                        speechManager.stopListening()
                        speechManager.clear()
                    },
                    languageLabel = when (selectedLangCode) {
                        "te-IN" -> "తెలుగు (Telugu)"
                        "hi-IN" -> "हिंदी (Hindi)"
                        else -> "English (India)"
                    },
                    emergencyKeywords = detectedEmergencyKeywords,
                    modifier = Modifier.fillMaxWidth()
                )

                // Standby / Compact Multi-Bar Audio Spectrum Waveform Visualizer
                if (!isListening) {
                    AudioWaveformVisualizer(
                        isRecording = false,
                        rmsDb = 0f,
                        barCount = 24,
                        maxHeight = 56.dp,
                        showDbMeter = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Real-time Transcript Display Box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 150.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        val displayText = when {
                            currentFullText.isNotBlank() -> currentFullText
                            isListening -> "Listening... Speak clinical symptoms or dictation in Telugu, Hindi, or English (e.g., 'Gunde noppi, aayasam, BP 140/90, SpO2 92%')"
                            else -> "Tap the large microphone button below to record patient narrative"
                        }

                        Text(
                            text = displayText,
                            fontSize = 13.sp,
                            fontWeight = if (currentFullText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                            color = if (currentFullText.isNotBlank())
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Live Offline Keyword & Vitals Recognition Feedback Chips
                if (recognizedKeywords != null && currentFullText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OFFLINE COMMAND RECOGNITION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7BF5DF)
                                )
                                // Proposed Triage Badge
                                val tierColor = when (recognizedKeywords.proposedTier) {
                                    TriageTier.RED -> TriageRed
                                    TriageTier.YELLOW -> TriageYellow
                                    TriageTier.GREEN -> TriageGreen
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = tierColor) {
                                    Text(
                                        text = "TIER: ${recognizedKeywords.proposedTier.name}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (recognizedKeywords.proposedTier == TriageTier.YELLOW) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Extracted vitals
                                val v = recognizedKeywords.vitalsExtracted
                                if (v.bpSystolic != null && v.bpDiastolic != null) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E293B)) {
                                        Text("BP ${v.bpSystolic}/${v.bpDiastolic}", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                                if (v.spo2 != null) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = if (v.spo2 < 93) TriageRed.copy(alpha = 0.8f) else Color(0xFF1E293B)) {
                                        Text("SpO2 ${v.spo2}%", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                                if (v.heartRate != null) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E293B)) {
                                        Text("HR ${v.heartRate} bpm", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                                if (v.temperatureF != null) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E293B)) {
                                        Text("Temp ${v.temperatureF}°F", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }

                                // Symptoms detected
                                recognizedKeywords.symptomsDetected.forEach { symptom ->
                                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryContainerTeal) {
                                        Text(symptom, fontSize = 10.sp, color = OnPrimaryContainerTeal, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (speechError != null) {
                    Text(
                        text = speechError ?: "",
                        fontSize = 11.sp,
                        color = TriageYellow,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (fallbackNotice != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TriageYellow.copy(alpha = 0.15f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TriageYellow, modifier = Modifier.size(16.dp))
                        Text(fallbackNotice ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Microphone Push-to-Talk Button (Large tactile 72dp touch target)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
                    val micScale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = if (isListening) 1.18f else 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(480, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "MicScale"
                    )

                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(micScale)
                            .clip(CircleShape)
                            .clickable {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (isListening) {
                                        speechManager.stopListening()
                                    } else {
                                        speechManager.startListening()
                                    }
                                }
                            }
                            .testTag("btn_mic_record"),
                        shape = CircleShape,
                        color = if (isListening) TriageRed else PrimaryTeal,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Close else Icons.Default.Phone,
                                contentDescription = if (isListening) "Stop Recording" else "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (isListening) "Listening in real time... Tap to stop" else "Tap microphone to record or speak",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Dual Parsing Action Buttons (Online Gemini with auto-fallback OR Direct Offline Engine)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Direct Offline Rule Engine Button
                    OutlinedButton(
                        onClick = { processIntake(useOfflineDirectly = true) },
                        enabled = currentFullText.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_parse_offline"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Parse Offline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Primary Gemini Cloud Button (with auto-fallback on error)
                    Button(
                        onClick = { processIntake(useOfflineDirectly = false) },
                        enabled = currentFullText.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_parse_with_gemini"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parsing...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Parse via Gemini", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Regional Clinical Presets for offline demonstrations
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Clinical Quick Voice Simulation Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                val text = "Gunde noppi ga undi 2 hours nunchi, aayasam, BP 150 95, SpO2 91%."
                                speechManager.setSimulatedSpeech(text)
                            },
                            label = { Text("Telugu (RED)", fontSize = 10.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )

                        SuggestionChip(
                            onClick = {
                                val text = "Bohot tez bukhar hai 4 din se, ulti ho rahi hai, Temp 102.5, Pulse 106."
                                speechManager.setSimulatedSpeech(text)
                            },
                            label = { Text("Hindi (YELLOW)", fontSize = 10.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )

                        SuggestionChip(
                            onClick = {
                                val text = "Routine ANC trimester checkup, BP 118 78, normal fetal movements, weight 54kg."
                                speechManager.setSimulatedSpeech(text)
                            },
                            label = { Text("ANC (GREEN)", fontSize = 10.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}
