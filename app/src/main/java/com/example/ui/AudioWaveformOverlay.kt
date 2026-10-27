package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TriageTier
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageRed
import com.example.ui.theme.TriageYellow
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Visual Audio Amplitude Waveform Overlay
 *
 * Appears dynamically when the microphone is actively capturing audio during clinical
 * transcriptions. Provides high-fidelity real-time feedback with:
 * 1. Multi-harmonic sinusoidal wave oscillation scaled by live RMS dB.
 * 2. Symmetric center-expanded acoustic frequency bars.
 * 3. Pulsing microphone ripple aura reflecting real-time voice amplitude.
 * 4. Streaming transcript live ticker showing recognized words as they arrive.
 * 5. Sound level decibel meter and audio quality gauge ("Optimal", "Whisper", "Peak").
 * 6. Quick action controls to stop and insert transcript or cancel.
 */
@Composable
fun AudioWaveformOverlay(
    isVisible: Boolean,
    rmsDb: Float,
    transcript: String,
    partialTranscript: String,
    onStopListening: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    languageLabel: String = "తెలుగు / English",
    onLanguageClick: (() -> Unit)? = null,
    emergencyKeywords: List<com.example.clinical.OfflineClinicalCommandEngine.EmergencyKeywordMatch> = emptyList()
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f)
        ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(180, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        AudioWaveformOverlayContent(
            rmsDb = rmsDb,
            transcript = transcript,
            partialTranscript = partialTranscript,
            onStopListening = onStopListening,
            onCancel = onCancel,
            languageLabel = languageLabel,
            onLanguageClick = onLanguageClick,
            emergencyKeywords = emergencyKeywords
        )
    }
}

@Composable
private fun AudioWaveformOverlayContent(
    rmsDb: Float,
    transcript: String,
    partialTranscript: String,
    onStopListening: () -> Unit,
    onCancel: () -> Unit,
    languageLabel: String,
    onLanguageClick: (() -> Unit)?,
    emergencyKeywords: List<com.example.clinical.OfflineClinicalCommandEngine.EmergencyKeywordMatch>
) {
    // Smoothed RMS with responsive attack & decay
    val animatedRms by animateFloatAsState(
        targetValue = rmsDb.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 750f),
        label = "overlay_rms_spring"
    )

    // Elapsed recording timer
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            delay(500)
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerString = String.format("%02d:%02d", minutes, seconds)

    val currentSpeechText = remember(transcript, partialTranscript) {
        when {
            partialTranscript.isNotBlank() && transcript.isNotBlank() -> "$transcript $partialTranscript"
            partialTranscript.isNotBlank() -> partialTranscript
            transcript.isNotBlank() -> transcript
            else -> ""
        }
    }

    // Audio level quality classification
    val levelCategory = when {
        animatedRms < 0.12f -> Triple("Low / Whisper", Color(0xFF94A3B8), 0.12f)
        animatedRms in 0.12f..0.78f -> Triple("Optimal Clinical Audio", Color(0xFF10B981), animatedRms)
        else -> Triple("High / Loud Peak", TriageYellow, animatedRms)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF7BF5DF).copy(alpha = 0.6f),
                        PrimaryTeal.copy(alpha = 0.8f),
                        Color(0xFF10B981).copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("audio_waveform_overlay"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20B132B) // High-contrast translucent dark slate
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top HUD Bar: Live REC dot, Timer, Status, Language Badge, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing recording indicator
                    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
                    val pulseScale by pulseTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "rec_dot_scale"
                    )

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(TriageRed.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TriageRed)
                        )
                    }

                    Text(
                        text = "REC",
                        color = TriageRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = timerString,
                        color = Color(0xFFF1F5F9),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = levelCategory.first,
                            color = levelCategory.second,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Language Badge (interactive if callback provided)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryContainerTeal.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = onLanguageClick != null) { onLanguageClick?.invoke() }
                    ) {
                        Text(
                            text = languageLabel,
                            color = Color(0xFF7BF5DF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Cancel / Dismiss button (48dp target)
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp).testTag("btn_overlay_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Dictation",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Real-time Dual-Mode Visual Waveform: Harmonized Bezier Waves + Spectrum Bars
            WaveformSpectrumCanvas(
                animatedRms = animatedRms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF030712).copy(alpha = 0.7f))
            )

            // Decibel & Amplitude Gauge Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dbEst = (-26f * (1f - animatedRms)).toInt()
                val percentLevel = (animatedRms * 100).toInt()

                Text(
                    text = "MIC LEVEL: $percentLevel% ($dbEst dB)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
                )

                // Mini horizontal level bar
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRms.coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7BF5DF), Color(0xFF10B981), if (animatedRms > 0.8f) TriageRed else Color(0xFF7BF5DF))
                                )
                            )
                    )
                }
            }

            // Real-Time Local Emergency Keywords Alert Banner (from OfflineClinicalCommandEngine)
            if (emergencyKeywords.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF450A0A).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TriageRed),
                    modifier = Modifier.fillMaxWidth().testTag("emergency_keyword_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "LOCAL ALERT:",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        emergencyKeywords.forEach { match ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (match.urgency == TriageTier.RED) TriageRed else TriageYellow
                            ) {
                                Text(
                                    text = match.keyword.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Real-time Streaming Transcript Ticker
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp, max = 80.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (currentSpeechText.isNotBlank()) {
                        Text(
                            text = currentSpeechText,
                            color = Color(0xFFF8FAFC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Listening to clinical narrative... Speak Telugu, Hindi, or English",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Bottom Action Controls: Stop & Accept (Primary) and Discard (Outlined)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_overlay_discard"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFCBD5E1)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Discard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onStopListening,
                    modifier = Modifier
                        .weight(1.5f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_overlay_finish"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentSpeechText.isNotBlank()) "Finish & Apply" else "Stop Recording",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * High-performance dual-layer waveform canvas combining continuous Bezier sine oscillations
 * with responsive vertical acoustic frequency bars.
 */
@Composable
private fun WaveformSpectrumCanvas(
    animatedRms: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sine_phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // --- LAYER 1: Symmetrical Multi-bar Frequency Spectrum ---
        val barWidth = (width / (barCount * 1.55f)).coerceIn(3f, 8f)
        val barSpacing = (width - (barCount * barWidth)) / (barCount + 1)
        val minBarHeight = 4f
        val maxAvailableHeight = height * 0.88f

        for (i in 0 until barCount) {
            val x = barSpacing + i * (barWidth + barSpacing)

            // Human voice acoustic envelope (higher response in conversational speech frequencies)
            val normalizedIdx = (i + 1).toFloat() / (barCount + 1)
            val bellWeight = sin(normalizedIdx * PI.toFloat()).coerceIn(0.25f, 1.0f)

            // Harmonic dynamic factor
            val waveOscillation = sin(phase * 1.8f + (i * 0.4f))
            val harmonicBoost = (waveOscillation + 1f) / 2f

            val dynamicHeight = if (animatedRms > 0.02f) {
                val base = 0.12f
                val rmsComponent = animatedRms * 0.88f * bellWeight
                val heightFactor = (base + rmsComponent * (0.75f + 0.25f * harmonicBoost)).coerceIn(0.08f, 1.0f)
                (maxAvailableHeight * heightFactor).coerceAtLeast(minBarHeight)
            } else {
                // Breathing idle state
                (minBarHeight + 5f * sin(phase + i * 0.3f).coerceAtLeast(0f))
            }

            val yTop = centerY - (dynamicHeight / 2f)

            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7BF5DF), // Luminous crest
                    Color(0xFF14B8A6), // Emerald mid
                    Color(0xFF0F766E)  // Deep teal base
                ),
                startY = yTop,
                endY = yTop + dynamicHeight
            )

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, yTop),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // --- LAYER 2: Flowing Dual Bezier Sine Waves across Foreground ---
        val waveAmplitude = (height * 0.38f * (0.15f + animatedRms * 0.85f)).coerceAtMost(height * 0.44f)

        // Wave 1: Cyan/Mint Primary Wave
        val path1 = Path()
        val path2 = Path()

        val steps = 60
        val stepX = width / steps

        path1.moveTo(0f, centerY)
        path2.moveTo(0f, centerY)

        for (step in 1..steps) {
            val currentX = step * stepX
            val angle1 = (currentX / width) * 2f * PI.toFloat() * 1.5f + phase
            val angle2 = (currentX / width) * 2f * PI.toFloat() * 2.2f - phase

            val y1 = centerY + sin(angle1) * waveAmplitude
            val y2 = centerY + sin(angle2) * (waveAmplitude * 0.7f)

            path1.lineTo(currentX, y1)
            path2.lineTo(currentX, y2)
        }

        // Stroke secondary subtle emerald wave
        drawPath(
            path = path2,
            color = Color(0xFF10B981).copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Stroke primary glowing cyan wave
        drawPath(
            path = path1,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF7BF5DF).copy(alpha = 0.2f),
                    Color(0xFF7BF5DF),
                    Color(0xFF00E5FF),
                    Color(0xFF7BF5DF).copy(alpha = 0.2f)
                )
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
