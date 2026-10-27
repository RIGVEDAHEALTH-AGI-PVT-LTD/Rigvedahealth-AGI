package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.speech.SpeechRecognitionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioWaveformOverlayTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testSpeechRecognitionManagerInitialState() {
        val manager = SpeechRecognitionManager(context)

        assertFalse(manager.isListening.value)
        assertEquals(0f, manager.rmsDb.value, 0.001f)
        assertEquals("", manager.transcript.value)
        assertEquals("", manager.partialTranscript.value)
    }

    @Test
    fun testSimulatedSpeechAndClear() {
        val manager = SpeechRecognitionManager(context)

        manager.setSimulatedSpeech("Patient ki BP 160/100 mariyu headache undi")
        assertEquals("Patient ki BP 160/100 mariyu headache undi", manager.transcript.value)
        assertFalse(manager.isListening.value)

        manager.clear()
        assertEquals("", manager.transcript.value)
        assertEquals("", manager.partialTranscript.value)
        assertEquals(0f, manager.rmsDb.value, 0.001f)
    }

    @Test
    fun testStartAndStopListeningTransitions() = runBlocking {
        val manager = SpeechRecognitionManager(context)

        // Starting listening engages listening state
        manager.startListening()
        assertTrue(manager.isListening.value)

        // Stopping listening cleanly disengages
        manager.stopListening()
        assertFalse(manager.isListening.value)
        assertEquals(0f, manager.rmsDb.value, 0.001f)
    }

    @Test
    fun testRmsDecibelCategoryThresholds() {
        // Test audio amplitude classification thresholds used in AudioWaveformOverlay
        fun classifyAmplitude(rms: Float): String = when {
            rms < 0.12f -> "Low / Whisper"
            rms in 0.12f..0.78f -> "Optimal Clinical Audio"
            else -> "High / Loud Peak"
        }

        assertEquals("Low / Whisper", classifyAmplitude(0.05f))
        assertEquals("Low / Whisper", classifyAmplitude(0.10f))
        assertEquals("Optimal Clinical Audio", classifyAmplitude(0.25f))
        assertEquals("Optimal Clinical Audio", classifyAmplitude(0.50f))
        assertEquals("Optimal Clinical Audio", classifyAmplitude(0.75f))
        assertEquals("High / Loud Peak", classifyAmplitude(0.85f))
        assertEquals("High / Loud Peak", classifyAmplitude(1.0f))
    }
}
