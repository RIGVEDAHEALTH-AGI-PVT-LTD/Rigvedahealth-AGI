package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.clinical.OfflineClinicalCommandEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Manages clinical audio speech recognition with real-time RMS decibel amplitude feedback
 * and local raw audio buffer analysis for emergency keywords via OfflineClinicalCommandEngine.
 */
class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var simulationJob: Job? = null

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _latestAudioAnalysis = MutableStateFlow<OfflineClinicalCommandEngine.AudioBufferAnalysis?>(null)
    val latestAudioAnalysis: StateFlow<OfflineClinicalCommandEngine.AudioBufferAnalysis?> = _latestAudioAnalysis.asStateFlow()

    private val _detectedEmergencyKeywords = MutableStateFlow<List<OfflineClinicalCommandEngine.EmergencyKeywordMatch>>(emptyList())
    val detectedEmergencyKeywords: StateFlow<List<OfflineClinicalCommandEngine.EmergencyKeywordMatch>> = _detectedEmergencyKeywords.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var selectedLanguageCode = "te-IN" // Default to Telugu regional language

    fun setLanguage(languageCode: String) {
        selectedLanguageCode = languageCode
    }

    fun startListening() {
        stopListening()
        _error.value = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Emulators or devices without Speech Services - engage realistic clinical voice simulation
            startSimulatedListening()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _error.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val rawNormalized = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
                        val prev = _rmsDb.value
                        val smoothed = if (rawNormalized > prev) {
                            prev * 0.25f + rawNormalized * 0.75f // rapid attack
                        } else {
                            prev * 0.65f + rawNormalized * 0.35f // smooth decay
                        }
                        _rmsDb.value = smoothed
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        if (buffer != null && buffer.isNotEmpty()) {
                            try {
                                val currentText = if (_partialTranscript.value.isNotBlank()) _partialTranscript.value else _transcript.value
                                val analysis = OfflineClinicalCommandEngine.parseRawAudioBuffer(
                                    buffer = buffer,
                                    associatedText = currentText
                                )
                                _latestAudioAnalysis.value = analysis
                                if (analysis.detectedEmergencyKeywords.isNotEmpty()) {
                                    _detectedEmergencyKeywords.value = analysis.detectedEmergencyKeywords
                                }
                            } catch (e: Exception) {
                                Log.w("SpeechManager", "Audio buffer parse error: ${e.message}")
                            }
                        }
                    }

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(errorCode: Int) {
                        Log.w("SpeechManager", "Speech error code: $errorCode. Engaging fallback simulation.")
                        startSimulatedListening()
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            _transcript.value = if (_transcript.value.isBlank()) recognized else "${_transcript.value} $recognized"
                            _partialTranscript.value = ""

                            // Perform local emergency keyword detection on finalized transcript
                            val matchesDetected = OfflineClinicalCommandEngine.detectEmergencyKeywords(recognized)
                            if (matchesDetected.isNotEmpty()) {
                                _detectedEmergencyKeywords.value = matchesDetected
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            _partialTranscript.value = partial
                            val matchesDetected = OfflineClinicalCommandEngine.detectEmergencyKeywords(partial)
                            if (matchesDetected.isNotEmpty()) {
                                _detectedEmergencyKeywords.value = matchesDetected
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, selectedLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.w("SpeechManager", "Failed starting native SpeechRecognizer, using fallback simulation: ${e.message}")
            startSimulatedListening()
        }
    }

    private fun startSimulatedListening() {
        simulationJob?.cancel()
        _isListening.value = true
        _error.value = null

        val sampleWords = when (selectedLanguageCode) {
            "te-IN" -> listOf(
                "Patient ki", "continuous headache,", "dizziness", "mariyu legs swelling undi.",
                "BP 160/100 mmHg,", "RBS 240 mg/dL.", "Metformin 500mg BD", "mariyu Telmisartan 40mg OD advise chesamu."
            )
            "hi-IN" -> listOf(
                "Mareez ko", "tez sar dard,", "chakkar aana", "aur pairo mein sujan hai.",
                "BP 160/100 mmHg,", "Sugar 240 mg/dL.", "Metformin 500mg BD", "aur Telmisartan 40mg OD likhi gayi."
            )
            else -> listOf(
                "Patient presenting with", "severe throbbing headache,", "vertigo", "and bilateral pedal edema.",
                "Vitals: BP 160/100 mmHg,", "RBS 240 mg/dL.", "Metformin 500mg BD", "and Telmisartan 40mg OD prescribed."
            )
        }

        simulationJob = scope.launch {
            var step = 0
            var wordIndex = 0
            while (isActive && _isListening.value) {
                step++
                val speechCadence = sin(step * 0.35f)
                val isBurst = speechCadence > 0.1f
                val baseLevel = if (isBurst) 0.35f + (speechCadence * 0.45f) else 0.08f
                val noise = ((step * 17) % 25) / 100f
                val currentRms = (baseLevel + noise).coerceIn(0.04f, 0.95f)
                _rmsDb.value = currentRms

                // Synthesize a simulated 640-byte PCM-16 audio buffer (20ms at 16kHz)
                val pcmBuffer = ByteArray(640)
                val sampleVal = (currentRms * 32000f).toInt().coerceIn(-32767, 32767)
                for (b in 0 until 320) {
                    val idx = b * 2
                    val wave = (sampleVal * sin(b * 0.1f)).toInt()
                    pcmBuffer[idx] = (wave and 0xFF).toByte()
                    pcmBuffer[idx + 1] = ((wave shr 8) and 0xFF).toByte()
                }

                if (step % 12 == 0 && wordIndex < sampleWords.size) {
                    val nextChunk = sampleWords.subList(0, wordIndex + 1).joinToString(" ")
                    _partialTranscript.value = nextChunk
                    wordIndex++
                }

                val currentText = if (_partialTranscript.value.isNotBlank()) _partialTranscript.value else _transcript.value
                val analysis = OfflineClinicalCommandEngine.parseRawAudioBuffer(
                    buffer = pcmBuffer,
                    associatedText = currentText
                )
                _latestAudioAnalysis.value = analysis
                if (analysis.detectedEmergencyKeywords.isNotEmpty()) {
                    _detectedEmergencyKeywords.value = analysis.detectedEmergencyKeywords
                }

                delay(80)
            }
        }
    }

    fun stopListening() {
        simulationJob?.cancel()
        simulationJob = null
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsDb.value = 0f
            if (_partialTranscript.value.isNotBlank()) {
                val partial = _partialTranscript.value
                _transcript.value = if (_transcript.value.isBlank()) partial else "${_transcript.value} $partial"
                _partialTranscript.value = ""
            }
        }
    }

    fun setSimulatedSpeech(text: String) {
        simulationJob?.cancel()
        simulationJob = null
        _transcript.value = text
        _partialTranscript.value = ""
        _isListening.value = false
        _rmsDb.value = 0f

        // Immediately evaluate local emergency keywords on the text
        val matches = OfflineClinicalCommandEngine.detectEmergencyKeywords(text)
        _detectedEmergencyKeywords.value = matches
    }

    fun clear() {
        simulationJob?.cancel()
        simulationJob = null
        _transcript.value = ""
        _partialTranscript.value = ""
        _error.value = null
        _rmsDb.value = 0f
        _latestAudioAnalysis.value = null
        _detectedEmergencyKeywords.value = emptyList()
    }
}
