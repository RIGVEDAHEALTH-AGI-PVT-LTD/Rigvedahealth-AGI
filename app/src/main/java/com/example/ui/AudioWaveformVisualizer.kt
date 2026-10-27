package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TriageRed
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-fidelity Multi-Bar Audio Spectrum Visualizer with real-time RMS height scaling.
 *
 * Driven by live microphone RMS decibel callback from Android SpeechRecognizer.
 * Uses an acoustic spectral envelope and dynamic frequency-bar scaling to provide
 * immediate, tactile feedback for clinicians speaking Telugu, Hindi, or English.
 */
@Composable
fun AudioWaveformVisualizer(
    isRecording: Boolean,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    maxHeight: Dp = 68.dp,
    showDbMeter: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum_phase")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_rot"
    )

    // Animated smoothed RMS dB value for UI responsiveness
    val animatedRms by animateFloatAsState(
        targetValue = if (isRecording) rmsDb.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "rms_spring"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Multi-bar Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            // Calculate responsive bar dimensions
            val minBarWidth = 4f
            val maxBarWidth = 14f
            val calculatedWidth = (canvasWidth / (barCount * 1.55f)).coerceIn(minBarWidth, maxBarWidth)
            val barSpacing = (canvasWidth - (barCount * calculatedWidth)) / (barCount + 1)

            val minHeight = 6f
            val usableHeight = canvasHeight * 0.92f

            for (i in 0 until barCount) {
                val x = barSpacing + i * (calculatedWidth + barSpacing)

                // Acoustic bell-curve spectral envelope (higher response in mid-frequencies)
                val normalizedIndex = (i + 1).toFloat() / (barCount + 1)
                val bellEnvelope = sin(normalizedIndex * PI.toFloat()).coerceIn(0.2f, 1.0f)

                // Multi-frequency micro-oscillation for lifelike dynamic spectrum motion
                val wave1 = sin(phase * 1.4f + (i * 0.35f))
                val wave2 = sin(phase * 2.1f + (i * 0.65f))
                val harmonic = ((wave1 + wave2 + 2f) / 4f)

                val barHeight = if (isRecording) {
                    val baseFactor = 0.15f
                    // RMS decibels dynamically drive the height expansion
                    val rmsFactor = animatedRms * 0.85f * bellEnvelope
                    val dynamicFactor = (baseFactor + rmsFactor * (0.8f + 0.2f * harmonic)).coerceIn(0.08f, 1.0f)
                    (usableHeight * dynamicFactor).coerceAtLeast(minHeight)
                } else {
                    minHeight
                }

                val yTop = centerY - (barHeight / 2f)

                // High-contrast glowing clinical gradient
                val barBrush = Brush.verticalGradient(
                    colors = if (isRecording) {
                        listOf(
                            Color(0xFF7BF5DF), // Glowing crest
                            Color(0xFF148577), // Active emerald teal
                            Color(0xFF006A60)  // Deep clinical base
                        )
                    } else {
                        listOf(
                            Color(0xFF64748B),
                            Color(0xFF334155)
                        )
                    },
                    startY = yTop,
                    endY = yTop + barHeight
                )

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(x, yTop),
                    size = Size(calculatedWidth, barHeight),
                    cornerRadius = CornerRadius(calculatedWidth / 2f, calculatedWidth / 2f)
                )
            }
        }

        if (showDbMeter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) TriageRed else Color.Gray)
                    )
                    Text(
                        text = if (isRecording) "RECORDING LIVE AUDIO" else "STANDBY / READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFF7BF5DF) else Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                }

                val levelPercent = (animatedRms * 100).toInt()
                Text(
                    text = if (isRecording) "RMS: $levelPercent% (-${(20 * (1f - animatedRms)).toInt()} dB)" else "0 dB",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}
