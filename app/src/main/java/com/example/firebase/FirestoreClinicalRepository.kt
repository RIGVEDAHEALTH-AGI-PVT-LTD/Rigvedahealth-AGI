package com.example.firebase

import android.content.Context
import com.example.R
import com.example.db.EncounterEntity
import com.example.db.PatientEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreClinicalRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePatients(): Flow<List<FirestorePatient>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/patients"
        emitAll(
            db.collection("users").document(uid).collection("patients")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestorePatient::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeEncounters(): Flow<List<FirestoreEncounter>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/encounters"
        emitAll(
            db.collection("users").document(uid).collection("encounters")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreEncounter::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun savePatient(patient: FirestorePatient) {
        val uid = requireUserId()
        val docId = if (patient.patientId.isNotBlank()) patient.patientId else "PAT-${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(uid).collection("patients").document(docId)

        val validatedAbha = com.example.security.AbhaValidator.requireValid(patient.abhaNumber)

        val data = hashMapOf<String, Any>(
            "patientId" to docId,
            "userId" to uid,
            "abhaNumber" to validatedAbha,
            "encryptedAbha" to patient.encryptedAbha,
            "abhaBlindIndex" to patient.abhaBlindIndex,
            "name" to patient.name,
            "age" to patient.age,
            "gender" to patient.gender,
            "isPregnant" to patient.isPregnant,
            "phone" to patient.phone,
            "village" to patient.village,
            "activeMedications" to patient.activeMedications,
            "createdAt" to (patient.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveEncounter(encounter: FirestoreEncounter) {
        val uid = requireUserId()
        val docId = if (encounter.encounterId.isNotBlank()) encounter.encounterId else "ENC-${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(uid).collection("encounters").document(docId)

        val data = hashMapOf<String, Any>(
            "encounterId" to docId,
            "userId" to uid,
            "patientId" to encounter.patientId,
            "facilityId" to encounter.facilityId,
            "operatorId" to encounter.operatorId,
            "triageTier" to encounter.triageTier,
            "triageRationale" to encounter.triageRationale,
            "referralRequired" to encounter.referralRequired,
            "chiefComplaint" to encounter.chiefComplaint,
            "rawVoiceTranscript" to encounter.rawVoiceTranscript,
            "soapJson" to encounter.soapJson,
            "fhirBundleJson" to encounter.fhirBundleJson,
            "vitalsSummary" to encounter.vitalsSummary,
            "bloodPressure" to encounter.bloodPressure,
            "spo2" to encounter.spo2,
            "heartRate" to encounter.heartRate,
            "prescribedMedications" to encounter.prescribedMedications,
            "syncedNhcx" to encounter.syncedNhcx,
            "syncedFhir" to encounter.syncedFhir,
            "syncStatus" to encounter.syncStatus,
            "createdAt" to (encounter.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun syncLocalPatient(entity: PatientEntity) {
        savePatient(
            FirestorePatient(
                patientId = entity.patientId,
                abhaNumber = entity.abhaNumber,
                encryptedAbha = entity.encryptedAbha,
                abhaBlindIndex = entity.abhaBlindIndex,
                name = entity.name,
                age = entity.age,
                gender = entity.gender,
                isPregnant = entity.isPregnant,
                phone = entity.phoneNumber,
                village = entity.location,
                activeMedications = entity.activeMedications
            )
        )
    }

    suspend fun syncLocalEncounter(entity: EncounterEntity) {
        saveEncounter(
            FirestoreEncounter(
                encounterId = entity.encounterId,
                patientId = entity.patientId,
                facilityId = entity.facilityId,
                operatorId = entity.operatorId,
                triageTier = entity.triageTier,
                triageRationale = entity.triageRationale,
                referralRequired = entity.referralRequired,
                chiefComplaint = entity.chiefComplaint,
                rawVoiceTranscript = entity.rawVoiceTranscript,
                soapJson = entity.soapJson,
                fhirBundleJson = entity.fhirBundleJson,
                vitalsSummary = entity.vitalsSummary,
                bloodPressure = entity.bloodPressure,
                spo2 = entity.spo2,
                heartRate = entity.heartRate,
                prescribedMedications = entity.prescribedMedications,
                syncedNhcx = entity.syncedNhcx,
                syncedFhir = entity.syncedFhir,
                syncStatus = entity.syncStatus
            )
        )
    }

    suspend fun syncClinicalEncounter(encounter: com.example.ClinicalEncounter) {
        saveEncounter(
            FirestoreEncounter(
                encounterId = encounter.id,
                patientId = encounter.patientId,
                facilityId = encounter.facilityId,
                operatorId = encounter.operatorId,
                triageTier = encounter.triage.tier.name,
                triageRationale = encounter.triage.rationale,
                referralRequired = encounter.triage.referralRequired,
                chiefComplaint = encounter.soapNote.subjective.chiefComplaint,
                rawVoiceTranscript = encounter.rawCodeSwitchingInput,
                soapJson = encounter.soapNote.toString(),
                fhirBundleJson = encounter.abdmFhirMapping.fhirJsonPayload,
                vitalsSummary = encounter.soapNote.objective.vitalsSummary,
                bloodPressure = "${encounter.soapNote.objective.bpSystolic}/${encounter.soapNote.objective.bpDiastolic} mmHg",
                spo2 = encounter.soapNote.objective.spo2,
                heartRate = encounter.soapNote.objective.heartRate,
                prescribedMedications = encounter.soapNote.plan.medicationsAdvised.joinToString(", ") { "${it.name} ${it.dosage} ${it.frequency}" },
                syncedNhcx = encounter.isSyncedNhcx,
                syncedFhir = encounter.isSyncedFhir,
                syncStatus = if (encounter.isSyncedNhcx && encounter.isSyncedFhir) "SYNCED" else "CACHED_OFFLINE"
            )
        )
    }
}
