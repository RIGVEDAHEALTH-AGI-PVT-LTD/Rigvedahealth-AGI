package com.example

import com.example.clinical.OfflineClinicalCommandEngine
import com.example.clinical.OfflineClinicalCommandEngine.EmergencyKeyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineClinicalCommandEngineTest {

    @Test
    fun testDecodePcm16AudioBuffer() {
        // Construct 4 16-bit samples: 0, 16384 (0.5), -16384 (-0.5), 32767 (~1.0)
        val buffer = ByteArray(8)
        // Sample 0: 0
        buffer[0] = 0; buffer[1] = 0
        // Sample 1: 16384 (0x4000)
        buffer[2] = 0x00; buffer[3] = 0x40
        // Sample 2: -16384 (0xC000)
        buffer[4] = 0x00; buffer[5] = 0xC0.toByte()
        // Sample 3: 32767 (0x7FFF)
        buffer[6] = 0xFF.toByte(); buffer[7] = 0x7F

        val samples = OfflineClinicalCommandEngine.decodePcm16AudioBuffer(buffer)
        assertEquals(4, samples.size)
        assertEquals(0.0f, samples[0], 0.001f)
        assertEquals(0.5f, samples[1], 0.01f)
        assertEquals(-0.5f, samples[2], 0.01f)
        assertEquals(1.0f, samples[3], 0.01f)
    }

    @Test
    fun testAcousticsSilentBuffer() {
        // Silent buffer
        val silentBuffer = ByteArray(320)
        val analysis = OfflineClinicalCommandEngine.parseRawAudioBuffer(silentBuffer)

        assertFalse(analysis.acoustics.isVoiceActive)
        assertFalse(analysis.acoustics.isDistress)
        assertTrue(analysis.detectedEmergencyKeywords.isEmpty())
        assertEquals(TriageTier.GREEN, analysis.triageEscalation)
    }

    @Test
    fun testAcousticsVocalDistressBuffer() {
        // High-amplitude distress buffer (> 0.85 peak)
        val highEnergyBuffer = ByteArray(640)
        val sampleVal = 30000 // approx 0.91 normalized amplitude
        for (i in 0 until 320) {
            val idx = i * 2
            val wave = (sampleVal * sin(i * 0.3f)).toInt()
            highEnergyBuffer[idx] = (wave and 0xFF).toByte()
            highEnergyBuffer[idx + 1] = ((wave shr 8) and 0xFF).toByte()
        }

        val analysis = OfflineClinicalCommandEngine.parseRawAudioBuffer(highEnergyBuffer)
        assertTrue(analysis.acoustics.isVoiceActive)
        assertTrue(analysis.acoustics.peakAmp > 0.85f)
        assertTrue(analysis.acoustics.isDistress)
        assertEquals(TriageTier.RED, analysis.triageEscalation)
        assertTrue(analysis.detectedEmergencyKeywords.any { it.keyword == EmergencyKeyword.EMERGENCY })
    }

    @Test
    fun testDetectEmergencyKeywordsInTranscript() {
        // 1. Keyword: emergency
        val emergencyMatches = OfflineClinicalCommandEngine.detectEmergencyKeywords("Patient in acute respiratory distress, this is an emergency case")
        assertEquals(1, emergencyMatches.size)
        assertEquals(EmergencyKeyword.EMERGENCY, emergencyMatches[0].keyword)
        assertEquals(TriageTier.RED, emergencyMatches[0].urgency)

        // 2. Keyword: allergy
        val allergyMatches = OfflineClinicalCommandEngine.detectEmergencyKeywords("Patient has known penicillin allergy and sulfa drug reaction")
        assertEquals(1, allergyMatches.size)
        assertEquals(EmergencyKeyword.ALLERGY, allergyMatches[0].keyword)
        assertEquals(TriageTier.YELLOW, allergyMatches[0].urgency)

        // 3. Keyword: stat
        val statMatches = OfflineClinicalCommandEngine.detectEmergencyKeywords("Start oxygen and give Sorbitrate 5mg stat")
        assertEquals(1, statMatches.size)
        assertEquals(EmergencyKeyword.STAT, statMatches[0].keyword)
        assertEquals(TriageTier.RED, statMatches[0].urgency)

        // 4. Combined all 3 keywords
        val mixedText = "Doctor ordered Telmisartan stat, check for amlodipine allergy, initiate emergency protocol"
        val allMatches = OfflineClinicalCommandEngine.detectEmergencyKeywords(mixedText)
        assertEquals(3, allMatches.size)
        assertTrue(allMatches.any { it.keyword == EmergencyKeyword.EMERGENCY })
        assertTrue(allMatches.any { it.keyword == EmergencyKeyword.ALLERGY })
        assertTrue(allMatches.any { it.keyword == EmergencyKeyword.STAT })
    }

    @Test
    fun testProcessRawAudioAndTranscriptOfflineFailover() {
        val transcript = "Patient ki acute chest pain undi, give aspirin stat, patient has penicillin allergy, severe emergency"
        val mockBuffer = ByteArray(320)

        val result = OfflineClinicalCommandEngine.processRawAudioAndTranscript(
            audioBuffer = mockBuffer,
            transcript = transcript,
            patientAge = 54,
            patientGender = "Male",
            isPregnant = false
        )

        // Verifications
        assertFalse("Must be local offline execution", result.isAiGenerated)
        assertEquals(TriageTier.RED, result.triageInfo.tier)
        assertTrue("Referral must be required for emergency/stat", result.triageInfo.referralRequired)
        assertTrue(result.triageInfo.rationale.contains("EMERGENCY/STAT"))

        // Check Allergy safety guardrail in medications
        val meds = result.soapNote.plan.medicationsAdvised
        assertTrue(meds.isNotEmpty())
        assertTrue("Medications must be flagged with allergy warning", meds.any { it.safetyFlag.contains("ALLERGY REVIEW REQUIRED") })

        // Check Differential Diagnoses includes emergency and allergy codes
        val diffs = result.soapNote.assessment.differentialDiagnoses
        assertTrue(diffs.any { it.icd10Code == "T88.7" }) // Allergic reaction

        // Check FHIR R4 mapping
        assertNotNull(result.abdmFhirMapping.fhirJsonPayload)
        assertTrue(result.abdmFhirMapping.fhirJsonPayload.contains("ClinicalImpression"))
        assertTrue(result.abdmFhirMapping.fhirJsonPayload.contains("Offline-Native Rule Engine"))
    }
}
