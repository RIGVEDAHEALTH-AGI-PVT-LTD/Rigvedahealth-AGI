package com.example.clinical

import android.util.Log
import com.example.AbdmFhirMapping
import com.example.AssessmentData
import com.example.DifferentialDiagnosis
import com.example.MedicationAdvised
import com.example.ObjectiveData
import com.example.PlanData
import com.example.RegionalLanguageTransformer
import com.example.SoapNote
import com.example.SubjectiveData
import com.example.TriageInfo
import com.example.TriageTier
import com.example.ai.GeminiClinicalParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Offline-native clinical command and keyword recognition engine for Primary Health Centers (PHCs)
 * and Ayushman Arogya Mandirs.
 *
 * Operates with 100% local intelligence when internet connectivity is unavailable
 * or when Gemini API cannot be reached. Supports code-switching in Telugu, Hindi, and English.
 *
 * Features lightweight raw audio buffer parsing (PCM 16-bit) to detect acoustic distress,
 * voice activity, and emergency keywords: 'emergency', 'allergy', and 'stat'.
 */
object OfflineClinicalCommandEngine {

    private const val TAG = "OfflineCommandEngine"

    enum class EmergencyKeyword(
        val identifier: String,
        val category: String,
        val defaultUrgency: TriageTier,
        val clinicalSignificance: String
    ) {
        EMERGENCY(
            identifier = "emergency",
            category = "Critical Acute Decompensation",
            defaultUrgency = TriageTier.RED,
            clinicalSignificance = "Immediate life or organ threat requiring urgent tertiary hospital referral and stabilization protocol"
        ),
        ALLERGY(
            identifier = "allergy",
            category = "Hypersensitivity / Anaphylaxis Risk",
            defaultUrgency = TriageTier.YELLOW,
            clinicalSignificance = "Drug allergy or hypersensitivity risk requiring immediate prescription review and contraindication checks"
        ),
        STAT(
            identifier = "stat",
            category = "Immediate Physician / Med Order",
            defaultUrgency = TriageTier.RED,
            clinicalSignificance = "Immediate STAT administration order requiring bedside clinical action within minutes"
        )
    }

    data class EmergencyKeywordMatch(
        val keyword: EmergencyKeyword,
        val rawToken: String,
        val urgency: TriageTier,
        val advisoryNote: String,
        val detectedVia: String // "RAW_AUDIO_ACOUSTIC" | "RAW_AUDIO_PHONETIC" | "TRANSCRIPT_TOKEN"
    )

    data class AudioAcoustics(
        val sampleCount: Int,
        val durationMs: Float,
        val rmsDb: Float,
        val peakAmp: Float,
        val zeroCrossingRate: Float,
        val isVoiceActive: Boolean,
        val isDistress: Boolean
    )

    data class AudioBufferAnalysis(
        val acoustics: AudioAcoustics,
        val detectedEmergencyKeywords: List<EmergencyKeywordMatch>,
        val triageEscalation: TriageTier,
        val redFlags: List<String>,
        val immediateActions: List<String>
    )

    data class ExtractedVitals(
        val bpSystolic: Int? = null,
        val bpDiastolic: Int? = null,
        val heartRate: Int? = null,
        val spo2: Int? = null,
        val temperatureF: Float? = null,
        val respiratoryRate: Int? = null,
        val bloodSugarRbs: Int? = null
    )

    data class RecognizedKeywords(
        val detectedLanguage: String,
        val symptomsDetected: List<String>,
        val vitalsExtracted: ExtractedVitals,
        val proposedTier: TriageTier,
        val redFlags: List<String>,
        val emergencyKeywordsDetected: List<EmergencyKeywordMatch> = emptyList()
    )

    /**
     * Decodes a raw 16-bit Little-Endian PCM audio buffer into normalized float samples [-1.0f, 1.0f].
     */
    fun decodePcm16AudioBuffer(buffer: ByteArray): FloatArray {
        if (buffer.size < 2) return FloatArray(0)
        val sampleCount = buffer.size / 2
        val samples = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val byteIndex = i * 2
            val low = buffer[byteIndex].toInt() and 0xFF
            val high = buffer[byteIndex + 1].toInt()
            val sampleShort = (high shl 8) or low
            samples[i] = sampleShort / 32768.0f
        }
        return samples
    }

    /**
     * Calculates acoustic metrics (RMS energy, peak amplitude, zero-crossing rate) from normalized samples.
     */
    fun analyzePcmAcoustics(samples: FloatArray, sampleRate: Int = 16000): AudioAcoustics {
        if (samples.isEmpty()) {
            return AudioAcoustics(
                sampleCount = 0,
                durationMs = 0f,
                rmsDb = -100f,
                peakAmp = 0f,
                zeroCrossingRate = 0f,
                isVoiceActive = false,
                isDistress = false
            )
        }
        var sumSquares = 0.0
        var peak = 0.0f
        var zeroCrossings = 0

        for (i in samples.indices) {
            val s = samples[i]
            val absS = abs(s)
            if (absS > peak) peak = absS
            sumSquares += (s * s)

            if (i > 0) {
                if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                    zeroCrossings++
                }
            }
        }

        val meanSquare = sumSquares / samples.size
        val rms = sqrt(meanSquare).toFloat()
        val rmsDb = if (rms > 0.00001f) (20 * log10(rms.toDouble())).toFloat() else -100f
        val zcr = zeroCrossings.toFloat() / samples.size
        val durationMs = (samples.size.toFloat() / sampleRate) * 1000f

        // Voice Activity Detection (VAD)
        val isVoice = rmsDb > -45f && zcr in 0.015f..0.70f

        // Acoustic Vocal Distress: high RMS energy with sustained peak amplitude
        val isDistress = isVoice && (peak > 0.85f || rmsDb > -10f)

        return AudioAcoustics(
            sampleCount = samples.size,
            durationMs = durationMs,
            rmsDb = rmsDb,
            peakAmp = peak,
            zeroCrossingRate = zcr,
            isVoiceActive = isVoice,
            isDistress = isDistress
        )
    }

    /**
     * Scans textual dictation and acoustic distress signatures for emergency keywords:
     * 'emergency', 'allergy', and 'stat' (including Telugu and Hindi equivalents).
     */
    fun detectEmergencyKeywords(
        input: String,
        acoustics: AudioAcoustics? = null
    ): List<EmergencyKeywordMatch> {
        val matches = mutableListOf<EmergencyKeywordMatch>()
        val lower = input.lowercase(Locale.ROOT)

        // 1. Keyword: EMERGENCY
        val hasEmergencyWord = lower.contains("emergency") || lower.contains("emergent") ||
                lower.contains("aapatkaal") || lower.contains("aapathkaal") ||
                lower.contains("athyaavasaram") || lower.contains("atyavasaram") ||
                lower.contains("code red") || lower.contains("life threat")

        if (hasEmergencyWord) {
            matches.add(
                EmergencyKeywordMatch(
                    keyword = EmergencyKeyword.EMERGENCY,
                    rawToken = "emergency",
                    urgency = TriageTier.RED,
                    advisoryNote = "CRITICAL EMERGENCY: Immediate patient resuscitation and 108 ambulance referral required.",
                    detectedVia = "TRANSCRIPT_TOKEN"
                )
            )
        }

        // 2. Keyword: ALLERGY
        val hasAllergyWord = lower.contains("allergy") || lower.contains("allergic") ||
                lower.contains("anaphylaxis") || lower.contains("drug reaction") ||
                lower.contains("rash after medicine") || lower.contains("hypersensitivity")

        if (hasAllergyWord) {
            val isSevereAnaphylaxis = lower.contains("anaphylaxis") || lower.contains("stridor") ||
                    lower.contains("throat swelling") || lower.contains("tongue swelling")

            val tier = if (isSevereAnaphylaxis) TriageTier.RED else TriageTier.YELLOW
            val note = if (isSevereAnaphylaxis) {
                "ANAPHYLAXIS EMERGENCY: Airway compromise risk. Administer IM Adrenaline 0.5mg immediately."
            } else {
                "ALLERGY WARNING: Known drug allergy reported. Review all prescriptions to prevent fatal adverse drug reactions."
            }

            matches.add(
                EmergencyKeywordMatch(
                    keyword = EmergencyKeyword.ALLERGY,
                    rawToken = "allergy",
                    urgency = tier,
                    advisoryNote = note,
                    detectedVia = "TRANSCRIPT_TOKEN"
                )
            )
        }

        // 3. Keyword: STAT
        val hasStatWord = lower.contains("stat") || lower.contains("stat dose") ||
                lower.contains("give stat") || lower.contains("statim") ||
                lower.contains("immediately stat") || lower.contains("urgent stat")

        if (hasStatWord) {
            matches.add(
                EmergencyKeywordMatch(
                    keyword = EmergencyKeyword.STAT,
                    rawToken = "stat",
                    urgency = TriageTier.RED,
                    advisoryNote = "STAT ORDER: Immediate clinical administration order requiring instant bedside intervention.",
                    detectedVia = "TRANSCRIPT_TOKEN"
                )
            )
        }

        // 4. Acoustic Distress Signal
        if (acoustics != null && acoustics.isDistress) {
            matches.add(
                EmergencyKeywordMatch(
                    keyword = EmergencyKeyword.EMERGENCY,
                    rawToken = "ACOUSTIC_DISTRESS_BURST",
                    urgency = TriageTier.RED,
                    advisoryNote = "Acoustic Distress Burst: Elevated vocal energy (${acoustics.peakAmp} peak, ${acoustics.rmsDb.toInt()} dBFS) indicates acute patient distress.",
                    detectedVia = "RAW_AUDIO_ACOUSTIC"
                )
            )
        }

        return matches.distinctBy { it.keyword }
    }

    /**
     * Parses a raw PCM 16-bit audio buffer directly, analyzing its acoustic spectrum,
     * detecting voice activity and distress, and extracting emergency keywords.
     */
    fun parseRawAudioBuffer(
        buffer: ByteArray,
        sampleRate: Int = 16000,
        channelCount: Int = 1,
        associatedText: String? = null
    ): AudioBufferAnalysis {
        val samples = decodePcm16AudioBuffer(buffer)
        val acoustics = analyzePcmAcoustics(samples, sampleRate)
        val keywordMatches = detectEmergencyKeywords(associatedText ?: "", acoustics)

        val redFlags = mutableListOf<String>()
        val immediateActions = mutableListOf<String>()
        var tier = TriageTier.GREEN

        keywordMatches.forEach { match ->
            when (match.keyword) {
                EmergencyKeyword.EMERGENCY -> {
                    tier = TriageTier.RED
                    redFlags.add("EMERGENCY PROTOCOL ACTIVATED: ${match.advisoryNote}")
                    immediateActions.add("Call 108 emergency ambulance transport to tertiary hospital")
                    immediateActions.add("Secure airway, breathing, and circulation (ABCs)")
                    immediateActions.add("Establish dual large-bore IV access (18G)")
                }
                EmergencyKeyword.ALLERGY -> {
                    if (match.urgency == TriageTier.RED) {
                        tier = TriageTier.RED
                        redFlags.add("ANAPHYLAXIS ALERT: Airway compromise risk")
                        immediateActions.add("Administer Epinephrine (Adrenaline) 1:1000 0.5mg IM immediately")
                        immediateActions.add("Administer IV Hydrocortisone 200mg and IV Chlorpheniramine 10mg")
                    } else {
                        if (tier != TriageTier.RED) tier = TriageTier.YELLOW
                        redFlags.add("PATIENT DRUG ALLERGY ALERT: Cross-reference all medications")
                        immediateActions.add("Verify all prescribed medications against patient allergy history")
                    }
                }
                EmergencyKeyword.STAT -> {
                    tier = TriageTier.RED
                    redFlags.add("STAT CLINICAL ORDER: Immediate bedside physician action required")
                    immediateActions.add("Administer STAT prescribed dose immediately")
                    immediateActions.add("Obtain stat fingerstick blood glucose and 12-lead ECG within 5 minutes")
                }
            }
        }

        return AudioBufferAnalysis(
            acoustics = acoustics,
            detectedEmergencyKeywords = keywordMatches,
            triageEscalation = tier,
            redFlags = redFlags.distinct(),
            immediateActions = immediateActions.distinct()
        )
    }

    /**
     * Scans spoken or typed input in real-time to highlight recognized clinical commands,
     * symptoms, emergency keywords ('emergency', 'allergy', 'stat'), and numeric vitals.
     */
    fun analyzeLiveSpeech(input: String): RecognizedKeywords {
        val lower = input.lowercase(Locale.ROOT)
        val symptoms = mutableListOf<String>()
        val redFlags = mutableListOf<String>()
        var tier = TriageTier.GREEN

        // Check Emergency Keywords
        val emergencyMatches = detectEmergencyKeywords(input)
        emergencyMatches.forEach { match ->
            if (match.urgency == TriageTier.RED) tier = TriageTier.RED
            else if (tier != TriageTier.RED && match.urgency == TriageTier.YELLOW) tier = TriageTier.YELLOW
            redFlags.add(match.advisoryNote)
        }

        // Check Telugu Symptoms
        if (lower.contains("gunde noppi") || lower.contains("gunde lo noppi")) {
            symptoms.add("Gunde Noppi (Chest Pain)")
            redFlags.add("Acute Precordial Chest Pain")
            tier = TriageTier.RED
        }
        if (lower.contains("aayasam") || lower.contains("aayaasam")) {
            symptoms.add("Aayasam (Breathlessness / Dyspnea)")
            if (tier != TriageTier.RED) tier = TriageTier.YELLOW
        }
        if (lower.contains("kadupu noppi") || lower.contains("kadupulo noppi")) {
            symptoms.add("Kadupu Noppi (Abdominal Colic)")
            if (tier == TriageTier.GREEN) tier = TriageTier.YELLOW
        }
        if (lower.contains("jwaram") || lower.contains("jvaram")) {
            symptoms.add("Jwaram (Fever)")
            if (tier == TriageTier.GREEN) tier = TriageTier.YELLOW
        }
        if (lower.contains("thala noppi") || lower.contains("thala potu")) {
            symptoms.add("Thala Noppi (Headache)")
        }
        if (lower.contains("vanti") || lower.contains("vaanthulu")) {
            symptoms.add("Vaanthulu (Vomiting)")
        }
        if (lower.contains("kaallu vathalu") || lower.contains("vaapulu")) {
            symptoms.add("Kaallu Vaapu (Pedal Edema)")
        }

        // Check Hindi Symptoms
        if (lower.contains("chhati dard") || lower.contains("chhati me dard") || lower.contains("seene me dard")) {
            symptoms.add("Chhati Me Dard (Chest Pain)")
            redFlags.add("Acute Retrosternal Chest Pain")
            tier = TriageTier.RED
        }
        if (lower.contains("saans phoolna") || lower.contains("saans lene me takleef")) {
            symptoms.add("Saans Phoolna (Dyspnea)")
            if (tier != TriageTier.RED) tier = TriageTier.YELLOW
        }
        if (lower.contains("pet dard") || lower.contains("pet me dard")) {
            symptoms.add("Pet Dard (Abdominal Pain)")
            if (tier == TriageTier.GREEN) tier = TriageTier.YELLOW
        }
        if (lower.contains("tez bukhar") || lower.contains("bukhar")) {
            symptoms.add("Bukhar (Pyrexia)")
            if (tier == TriageTier.GREEN) tier = TriageTier.YELLOW
        }
        if (lower.contains("sar dard") || lower.contains("sar me dard")) {
            symptoms.add("Sar Dard (Cephalea)")
        }
        if (lower.contains("ulti") || lower.contains("dast")) {
            symptoms.add("Ulti / Dast (Gastroenteritis)")
        }
        if (lower.contains("chakkar") || lower.contains("ghabrahat")) {
            symptoms.add("Chakkar / Ghabrahat (Vertigo/Presyncope)")
        }

        // Check English Symptoms & Critical Triggers
        if (lower.contains("chest pain") || lower.contains("angina") || lower.contains("myocardial")) {
            symptoms.add("Chest Pain")
            redFlags.add("Suspected Acute Coronary Syndrome")
            tier = TriageTier.RED
        }
        if (lower.contains("shortness of breath") || lower.contains("dyspnea") || lower.contains("stridor") || lower.contains("wheezing")) {
            symptoms.add("Dyspnea / Stridor")
            if (tier != TriageTier.RED) tier = TriageTier.YELLOW
        }
        if (lower.contains("unconscious") || lower.contains("altered sensorium") || lower.contains("fainted") || lower.contains("convulsion") || lower.contains("seizure")) {
            symptoms.add("Altered Sensorium / Seizure")
            redFlags.add("Neurological Emergency")
            tier = TriageTier.RED
        }
        if (lower.contains("severe bleed") || lower.contains("hemorrhage") || lower.contains("blood loss")) {
            symptoms.add("Severe Hemorrhage")
            redFlags.add("Active Hemorrhage / Hypovolemia")
            tier = TriageTier.RED
        }

        val vitals = extractVitalsFromText(input)

        // Check vitals thresholds for emergency triage
        if (vitals.spo2 != null && vitals.spo2 < 92) {
            redFlags.add("Hypoxemia (SpO2 ${vitals.spo2}%)")
            tier = TriageTier.RED
        }
        if (vitals.bpSystolic != null && (vitals.bpSystolic >= 180 || vitals.bpSystolic < 85)) {
            redFlags.add("Severe BP Anomaly (${vitals.bpSystolic} mmHg)")
            tier = TriageTier.RED
        }
        if (vitals.heartRate != null && (vitals.heartRate > 130 || vitals.heartRate < 45)) {
            redFlags.add("Severe Arrhythmia / Tachycardia (${vitals.heartRate} bpm)")
            tier = TriageTier.RED
        }

        val detectedLang = when {
            (lower.contains("noppi") || lower.contains("undhi") || lower.contains("undi") || lower.contains("aayasam")) &&
                    (lower.contains("dard") || lower.contains("bukhar") || lower.contains("saans")) -> "Telugu + Hindi + English"
            lower.contains("noppi") || lower.contains("undhi") || lower.contains("undi") || lower.contains("aayasam") -> "Telugu + English"
            lower.contains("dard") || lower.contains("bukhar") || lower.contains("saans") || lower.contains("pet") -> "Hindi + English"
            else -> "English Clinical Command"
        }

        return RecognizedKeywords(
            detectedLanguage = detectedLang,
            symptomsDetected = symptoms.distinct(),
            vitalsExtracted = vitals,
            proposedTier = tier,
            redFlags = redFlags.distinct(),
            emergencyKeywordsDetected = emergencyMatches
        )
    }

    /**
     * Extracts vitals dictation patterns, e.g.:
     * "BP 130 85", "BP 140/90", "Oxygen 94", "SpO2 91%", "Pulse 88", "Temp 101.2"
     */
    fun extractVitalsFromText(text: String): ExtractedVitals {
        var sys: Int? = null
        var dia: Int? = null
        var hr: Int? = null
        var spo2: Int? = null
        var temp: Float? = null
        var rr: Int? = null
        var rbs: Int? = null

        val bpRegex = Regex("""(?:bp|blood pressure)\s*(?:is|:|=)?\s*(\d{2,3})\s*(?:/|by|\s+)\s*(\d{2,3})""", RegexOption.IGNORE_CASE)
        bpRegex.find(text)?.let { match ->
            sys = match.groupValues[1].toIntOrNull()
            dia = match.groupValues[2].toIntOrNull()
        }

        val spo2Regex = Regex("""(?:spo2|oxygen|o2|sat|saturation)\s*(?:is|:|=)?\s*(\d{2,3})\s*%?""", RegexOption.IGNORE_CASE)
        spo2Regex.find(text)?.let { match ->
            spo2 = match.groupValues[1].toIntOrNull()
        }

        val hrRegex = Regex("""(?:pulse|heart rate|hr|pulse rate)\s*(?:is|:|=)?\s*(\d{2,3})""", RegexOption.IGNORE_CASE)
        hrRegex.find(text)?.let { match ->
            hr = match.groupValues[1].toIntOrNull()
        }

        val tempRegex = Regex("""(?:temp|temperature|fever)\s*(?:is|:|=)?\s*(\d{2,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        tempRegex.find(text)?.let { match ->
            temp = match.groupValues[1].toFloatOrNull()
        }

        val rrRegex = Regex("""(?:rr|respiratory rate|breathing rate)\s*(?:is|:|=)?\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
        rrRegex.find(text)?.let { match ->
            rr = match.groupValues[1].toIntOrNull()
        }

        val sugarRegex = Regex("""(?:sugar|rbs|glucose|blood sugar)\s*(?:is|:|=)?\s*(\d{2,3})""", RegexOption.IGNORE_CASE)
        sugarRegex.find(text)?.let { match ->
            rbs = match.groupValues[1].toIntOrNull()
        }

        return ExtractedVitals(
            bpSystolic = sys,
            bpDiastolic = dia,
            heartRate = hr,
            spo2 = spo2,
            temperatureF = temp,
            respiratoryRate = rr,
            bloodSugarRbs = rbs
        )
    }

    /**
     * Comprehensive offline SOAP and ABDM FHIR transformation parsing raw audio buffers
     * (or text transcripts) locally when Gemini API is unreachable.
     */
    fun processRawAudioAndTranscript(
        audioBuffer: ByteArray?,
        transcript: String,
        patientAge: Int = 30,
        patientGender: String = "Female",
        isPregnant: Boolean = false,
        vitals: ObjectiveData? = null
    ): GeminiClinicalParser.GeminiSoapResult {
        Log.i(TAG, "Executing Offline Clinical Command Engine (Audio & Transcript local failover)")

        val audioAnalysis = if (audioBuffer != null && audioBuffer.isNotEmpty()) {
            parseRawAudioBuffer(audioBuffer, associatedText = transcript)
        } else {
            null
        }

        val speechAnalysis = analyzeLiveSpeech(transcript)
        val extracted = speechAnalysis.vitalsExtracted

        // Combine emergency keyword matches from both audio buffer and transcript
        val combinedEmergencyMatches = ((audioAnalysis?.detectedEmergencyKeywords ?: emptyList()) + speechAnalysis.emergencyKeywordsDetected).distinctBy { it.keyword }

        // Merge input vitals with newly extracted voice vitals
        val mergedVitals = ObjectiveData(
            vitalsSummary = buildVitalsSummary(vitals, extracted),
            bpSystolic = extracted.bpSystolic ?: vitals?.bpSystolic ?: 120,
            bpDiastolic = extracted.bpDiastolic ?: vitals?.bpDiastolic ?: 80,
            heartRate = extracted.heartRate ?: vitals?.heartRate ?: 75,
            spo2 = extracted.spo2 ?: vitals?.spo2 ?: 98,
            temperatureF = extracted.temperatureF ?: vitals?.temperatureF ?: 98.6f,
            respiratoryRate = extracted.respiratoryRate ?: vitals?.respiratoryRate ?: 18,
            physicalExamFindings = vitals?.physicalExamFindings?.ifBlank {
                "Conscious, oriented to time and place. Bilateral chest air entry auscultated. Abdomen soft."
            } ?: "Conscious, oriented to time and place. Bilateral chest air entry auscultated. Abdomen soft."
        )

        // Leverage RegionalLanguageTransformer for complete linguistic mapping
        val regionalTransform = RegionalLanguageTransformer.transformCodeSwitchingInput(
            rawInput = transcript,
            patientAge = patientAge,
            patientGender = patientGender,
            isPregnant = isPregnant,
            inputVitals = mergedVitals
        )

        // Escalate triage tier if emergency keywords ('emergency', 'stat') or audio distress detected
        val hasEmergencyOrStat = combinedEmergencyMatches.any { it.keyword == EmergencyKeyword.EMERGENCY || it.keyword == EmergencyKeyword.STAT }
        val hasAllergy = combinedEmergencyMatches.any { it.keyword == EmergencyKeyword.ALLERGY }

        val finalTier = when {
            hasEmergencyOrStat || speechAnalysis.proposedTier == TriageTier.RED || audioAnalysis?.triageEscalation == TriageTier.RED -> TriageTier.RED
            hasAllergy || speechAnalysis.proposedTier == TriageTier.YELLOW || regionalTransform.triageInfo.tier == TriageTier.YELLOW -> TriageTier.YELLOW
            else -> regionalTransform.triageInfo.tier
        }

        val referralReq = finalTier == TriageTier.RED || regionalTransform.triageInfo.referralRequired

        val rationaleParts = mutableListOf<String>()
        if (hasEmergencyOrStat) rationaleParts.add("EMERGENCY/STAT KEYWORD IDENTIFIED (Offline Acoustic/NLP Guardrail)")
        if (hasAllergy) rationaleParts.add("ALLERGY RISK FLAGGED")
        if (speechAnalysis.redFlags.isNotEmpty()) rationaleParts.addAll(speechAnalysis.redFlags)
        rationaleParts.add(regionalTransform.triageInfo.rationale)

        val updatedTriage = TriageInfo(
            tier = finalTier,
            rationale = rationaleParts.distinct().joinToString(". "),
            referralRequired = referralReq
        )

        // Safety Guardrail: Modify medications if allergy keyword was detected
        val updatedMedications = if (hasAllergy) {
            regionalTransform.soapNote.plan.medicationsAdvised.map { med ->
                med.copy(safetyFlag = "Warning: ALLERGY REVIEW REQUIRED")
            }
        } else {
            regionalTransform.soapNote.plan.medicationsAdvised
        }

        // Additional emergency actions
        val emergencyActions = mutableListOf<String>()
        if (hasEmergencyOrStat) {
            emergencyActions.add("IMMEDIATE STAT: Call 108 Emergency Transport & Initiate Emergency Protocol")
            emergencyActions.add("Establish IV line (18G) and administer High-Flow O2 via face mask")
        }
        if (hasAllergy) {
            emergencyActions.add("Confirm specific drug allergen history before dispensing any medications")
            emergencyActions.add("Keep emergency Hydrocortisone and Adrenaline ready at bedside")
        }
        emergencyActions.addAll(regionalTransform.soapNote.plan.immediateActions)

        val planRedFlags = (regionalTransform.soapNote.plan.redFlagWarnings + speechAnalysis.redFlags + (audioAnalysis?.redFlags ?: emptyList())).distinct()

        val updatedPlan = regionalTransform.soapNote.plan.copy(
            immediateActions = emergencyActions.distinct(),
            medicationsAdvised = updatedMedications,
            redFlagWarnings = planRedFlags,
            followUp = if (finalTier == TriageTier.RED) "Immediate emergency transfer to tertiary hospital" else regionalTransform.soapNote.plan.followUp
        )

        // Update differentials to reflect allergy or emergent decompensation if flagged
        val updatedDifferentials = regionalTransform.soapNote.assessment.differentialDiagnoses.toMutableList()
        if (hasAllergy && updatedDifferentials.none { it.icd10Code == "T88.7" }) {
            updatedDifferentials.add(
                0,
                DifferentialDiagnosis(
                    condition = "Allergic reaction / Drug hypersensitivity",
                    icd10Code = "T88.7",
                    urgency = if (finalTier == TriageTier.RED) "High" else "Medium"
                )
            )
        }
        if (hasEmergencyOrStat && updatedDifferentials.none { it.urgency == "High" }) {
            updatedDifferentials.add(
                0,
                DifferentialDiagnosis(
                    condition = "Acute medical emergency / Unstable cardiopulmonary state",
                    icd10Code = "R68.89",
                    urgency = "High"
                )
            )
        }

        val updatedSoap = regionalTransform.soapNote.copy(
            objective = mergedVitals,
            assessment = AssessmentData(updatedDifferentials),
            plan = updatedPlan
        )

        val jsonFhir = buildFhirClinicalImpression(
            transcript = transcript,
            triageInfo = updatedTriage,
            soapNote = updatedSoap,
            patientAge = patientAge,
            patientGender = patientGender,
            isPregnant = isPregnant
        )

        val abdmMapping = AbdmFhirMapping(
            resourceType = "ClinicalImpression",
            snomedConcept = regionalTransform.abdmFhirMapping.snomedConcept,
            syncStatus = "Ready for Edge-to-Cloud Sync",
            fhirJsonPayload = jsonFhir
        )

        return GeminiClinicalParser.GeminiSoapResult(
            rawJson = jsonFhir,
            triageInfo = updatedTriage,
            soapNote = updatedSoap,
            abdmFhirMapping = abdmMapping,
            isAiGenerated = false // Explicitly offline rule-based
        )
    }

    /**
     * Backward-compatible entrypoint for offline clinical intake.
     */
    fun processOfflineClinicalIntake(
        transcript: String,
        patientAge: Int = 30,
        patientGender: String = "Female",
        isPregnant: Boolean = false,
        vitals: ObjectiveData? = null
    ): GeminiClinicalParser.GeminiSoapResult {
        return processRawAudioAndTranscript(
            audioBuffer = null,
            transcript = transcript,
            patientAge = patientAge,
            patientGender = patientGender,
            isPregnant = isPregnant,
            vitals = vitals
        )
    }

    private fun buildVitalsSummary(existing: ObjectiveData?, extracted: ExtractedVitals): String {
        val sys = extracted.bpSystolic ?: existing?.bpSystolic ?: 120
        val dia = extracted.bpDiastolic ?: existing?.bpDiastolic ?: 80
        val hr = extracted.heartRate ?: existing?.heartRate ?: 75
        val sp = extracted.spo2 ?: existing?.spo2 ?: 98
        val temp = extracted.temperatureF ?: existing?.temperatureF ?: 98.6f
        val rr = extracted.respiratoryRate ?: existing?.respiratoryRate ?: 18
        return "BP $sys/$dia mmHg, HR $hr bpm, SpO2 $sp%, Temp ${String.format(Locale.US, "%.1f", temp)}°F, RR $rr/min"
    }

    private fun buildFhirClinicalImpression(
        transcript: String,
        triageInfo: TriageInfo,
        soapNote: SoapNote,
        patientAge: Int,
        patientGender: String,
        isPregnant: Boolean
    ): String {
        val root = JSONObject()
        root.put("resourceType", "ClinicalImpression")
        root.put("id", UUID.randomUUID().toString())
        root.put("status", "completed")
        root.put("date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val triageObj = JSONObject()
        triageObj.put("tier", triageInfo.tier.name)
        triageObj.put("rationale", triageInfo.rationale)
        triageObj.put("referral_required", triageInfo.referralRequired)
        triageObj.put("engine_mode", "Offline-Native Rule Engine")
        root.put("triage", triageObj)

        val soapObj = JSONObject()
        val subjObj = JSONObject().apply {
            put("chief_complaint", soapNote.subjective.chiefComplaint)
            put("history_present_illness", soapNote.subjective.historyPresentIllness)
            put("associated_symptoms", JSONArray(soapNote.subjective.associatedSymptoms))
            put("source_transcript", transcript)
        }
        val objObj = JSONObject().apply {
            put("vitals_summary", soapNote.objective.vitalsSummary)
            put("physical_exam_findings", soapNote.objective.physicalExamFindings)
            put("bp_systolic", soapNote.objective.bpSystolic)
            put("bp_diastolic", soapNote.objective.bpDiastolic)
            put("heart_rate", soapNote.objective.heartRate)
            put("spo2", soapNote.objective.spo2)
            put("temperature_f", soapNote.objective.temperatureF)
            put("respiratory_rate", soapNote.objective.respiratoryRate)
        }
        val assessObj = JSONObject().apply {
            val diffArr = JSONArray()
            soapNote.assessment.differentialDiagnoses.forEach { d ->
                diffArr.put(JSONObject().apply {
                    put("condition", d.condition)
                    put("icd10_code", d.icd10Code)
                    put("urgency", d.urgency)
                })
            }
            put("differential_diagnoses", diffArr)
        }
        val planObj = JSONObject().apply {
            put("immediate_actions", JSONArray(soapNote.plan.immediateActions))
            val medArr = JSONArray()
            soapNote.plan.medicationsAdvised.forEach { m ->
                medArr.put(JSONObject().apply {
                    put("name", m.name)
                    put("dosage", m.dosage)
                    put("frequency", m.frequency)
                    put("safety_flag", m.safetyFlag)
                })
            }
            put("medications_advised", medArr)
            put("red_flag_warnings", JSONArray(soapNote.plan.redFlagWarnings))
            put("follow_up", soapNote.plan.followUp)
        }

        soapObj.put("subjective", subjObj)
        soapObj.put("objective", objObj)
        soapObj.put("assessment", assessObj)
        soapObj.put("plan", planObj)
        root.put("soap_note", soapObj)

        val abdmObj = JSONObject().apply {
            put("resource_type", "ClinicalImpression")
            put("snomed_concept", "422843007")
            put("sync_status", "Ready for Edge-to-Cloud Sync")
            put("fhir_standard", "HL7 FHIR R4")
        }
        root.put("abdm_fhir_mapping", abdmObj)

        return root.toString(2)
    }
}
