package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.firebase.FirestoreClinicalRepository
import com.example.firebase.FirestoreEncounter
import com.example.firebase.FirestorePatient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

import org.robolectric.annotation.Config

@Config(sdk = [34])
class FirestoreClinicalRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndReadPatient_authenticatedOperator_succeeds() = runBlocking {
        val uid = signInTestUser("anm.chevella@phc.in")
        val repository = FirestoreClinicalRepository(firestore)

        val patient = FirestorePatient(
            patientId = "PAT-TEST-001",
            userId = uid,
            abhaNumber = "91-4432-8810-1209",
            name = "Sujatha Reddy",
            age = 58,
            gender = "Female",
            isPregnant = false,
            phone = "9876543210",
            village = "PHC Chevella",
            activeMedications = "Metformin 500mg, Telmisartan 40mg"
        )

        withTimeout(5000L) {
            repository.savePatient(patient)
        }

        val patients = withTimeout(5000L) {
            repository.observePatients().first()
        }

        assertTrue(patients.isNotEmpty())
        val saved = patients.firstOrNull { it.patientId == "PAT-TEST-001" }
        assertNotNull(saved)
        assertEquals("Sujatha Reddy", saved?.name)
    }

    @Test
    fun createAndReadEncounter_authenticatedOperator_succeeds() = runBlocking {
        val uid = signInTestUser("mo.chevella@phc.in")
        val repository = FirestoreClinicalRepository(firestore)

        val encounter = FirestoreEncounter(
            encounterId = "ENC-TEST-101",
            userId = uid,
            patientId = "PAT-TEST-001",
            facilityId = "PHC-CHV-501",
            operatorId = "MO-102",
            triageTier = "YELLOW",
            triageRationale = "Uncontrolled hypertension and hyperglycemia requiring medication refill",
            referralRequired = false,
            chiefComplaint = "Headache, dizziness, leg swelling",
            bloodPressure = "160/100 mmHg",
            spo2 = 98,
            heartRate = 88,
            prescribedMedications = "Metformin 500mg BD, Telmisartan 40mg OD, Amlodipine 5mg OD"
        )

        withTimeout(5000L) {
            repository.saveEncounter(encounter)
        }

        val encounters = withTimeout(5000L) {
            repository.observeEncounters().first()
        }

        assertTrue(encounters.isNotEmpty())
        val saved = encounters.firstOrNull { it.encounterId == "ENC-TEST-101" }
        assertNotNull(saved)
        assertEquals("YELLOW", saved?.triageTier)
    }

    @Test
    fun unauthenticatedOperator_accessRejected() = runBlocking {
        auth.signOut()
        val repository = FirestoreClinicalRepository(firestore)

        try {
            repository.savePatient(
                FirestorePatient(
                    patientId = "PAT-FAIL",
                    name = "Unauthenticated Attempt"
                )
            )
            fail("Expected IllegalStateException for unauthenticated operator")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("User must be signed in with Google") == true)
        }
    }
}
