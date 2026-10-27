package com.example

import com.example.db.EncounterEntity
import com.example.ui.ClinicalTrendAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PatientTriageHistoryTest {

    private val dayMs = 86400000L
    private val baseTime = 1761200000000L

    private val encounterVisit1 = EncounterEntity(
        encounterId = "ENC-01",
        patientId = "P-101",
        triageTier = "GREEN",
        triageRationale = "Normal vitals. Routine follow-up.",
        chiefComplaint = "Routine follow-up",
        bpSystolic = 120,
        bpDiastolic = 80,
        bloodPressure = "120/80 mmHg",
        spo2 = 99,
        heartRate = 72,
        temperatureF = 98.4f,
        timestamp = baseTime - (30 * dayMs)
    )

    private val encounterVisit2 = EncounterEntity(
        encounterId = "ENC-02",
        patientId = "P-101",
        triageTier = "YELLOW",
        triageRationale = "Stage 1 hypertension, palpitations.",
        chiefComplaint = "Exertional palpitations",
        bpSystolic = 142,
        bpDiastolic = 90,
        bloodPressure = "142/90 mmHg",
        spo2 = 97,
        heartRate = 88,
        temperatureF = 98.6f,
        timestamp = baseTime - (14 * dayMs)
    )

    private val encounterVisit3 = EncounterEntity(
        encounterId = "ENC-03",
        patientId = "P-101",
        triageTier = "RED",
        triageRationale = "Acute coronary syndrome presentation with retrosternal radiation.",
        chiefComplaint = "Acute severe chest pain with diaphoresis",
        bpSystolic = 168,
        bpDiastolic = 102,
        bloodPressure = "168/102 mmHg",
        spo2 = 93,
        heartRate = 112,
        temperatureF = 98.4f,
        referralRequired = true,
        timestamp = baseTime - (2 * dayMs)
    )

    @Test
    fun testClinicalTrendAnalyzer_Deterioration() {
        val list = listOf(encounterVisit1, encounterVisit2, encounterVisit3)
        val analysis = ClinicalTrendAnalyzer.analyze(list)

        assertTrue("Should detect clinical deterioration", analysis.isDeteriorating)
        assertFalse("Should not be improving", analysis.isImproving)
        assertEquals(48, analysis.bpSystolicDelta) // 168 - 120
        assertEquals(-6, analysis.spo2Delta)      // 93 - 99
        assertEquals(40, analysis.hrDelta)        // 112 - 72
        assertTrue(analysis.status.contains("Deterioration"))
        assertEquals(3, analysis.tierProgression.size)
        assertEquals(TriageTier.GREEN, analysis.tierProgression[0])
        assertEquals(TriageTier.YELLOW, analysis.tierProgression[1])
        assertEquals(TriageTier.RED, analysis.tierProgression[2])
    }

    @Test
    fun testClinicalTrendAnalyzer_Improvement() {
        val recoveringList = listOf(
            encounterVisit3.copy(timestamp = baseTime - (10 * dayMs)),
            encounterVisit1.copy(timestamp = baseTime - (1 * dayMs))
        )
        val analysis = ClinicalTrendAnalyzer.analyze(recoveringList)

        assertTrue("Should detect clinical stabilization/improvement", analysis.isImproving)
        assertFalse("Should not be deteriorating", analysis.isDeteriorating)
        assertEquals(-48, analysis.bpSystolicDelta) // 120 - 168
        assertEquals(6, analysis.spo2Delta)        // 99 - 93
        assertTrue(analysis.status.contains("Stabilizing") || analysis.status.contains("Improving"))
    }

    @Test
    fun testClinicalTrendAnalyzer_EmptyEncounters() {
        val analysis = ClinicalTrendAnalyzer.analyze(emptyList())

        assertFalse(analysis.isDeteriorating)
        assertFalse(analysis.isImproving)
        assertEquals("Baseline Visit", analysis.status)
        assertEquals(0, analysis.bpSystolicDelta)
    }

    @Test
    fun testSortingEncountersByDate() {
        val unsorted = listOf(encounterVisit2, encounterVisit3, encounterVisit1)

        val newestFirst = unsorted.sortedByDescending { it.timestamp }
        assertEquals("ENC-03", newestFirst[0].encounterId) // -2 days
        assertEquals("ENC-02", newestFirst[1].encounterId) // -14 days
        assertEquals("ENC-01", newestFirst[2].encounterId) // -30 days

        val oldestFirst = unsorted.sortedBy { it.timestamp }
        assertEquals("ENC-01", oldestFirst[0].encounterId) // -30 days
        assertEquals("ENC-02", oldestFirst[1].encounterId) // -14 days
        assertEquals("ENC-03", oldestFirst[2].encounterId) // -2 days
    }

    @Test
    fun testFilteringEncountersByTier() {
        val encounters = listOf(encounterVisit1, encounterVisit2, encounterVisit3)

        val redOnly = encounters.filter { it.triageTier.equals("RED", ignoreCase = true) }
        assertEquals(1, redOnly.size)
        assertEquals("ENC-03", redOnly[0].encounterId)

        val yellowOnly = encounters.filter { it.triageTier.equals("YELLOW", ignoreCase = true) }
        assertEquals(1, yellowOnly.size)
        assertEquals("ENC-02", yellowOnly[0].encounterId)

        val greenOnly = encounters.filter { it.triageTier.equals("GREEN", ignoreCase = true) }
        assertEquals(1, greenOnly.size)
        assertEquals("ENC-01", greenOnly[0].encounterId)
    }
}
