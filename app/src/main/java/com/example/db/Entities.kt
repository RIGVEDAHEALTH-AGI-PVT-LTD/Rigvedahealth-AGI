package com.example.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity(
    tableName = "patients",
    indices = [
        Index(value = ["abhaNumber"], unique = true),
        Index(value = ["name"]),
        Index(value = ["abhaBlindIndex"])
    ]
)
data class PatientEntity(
    @PrimaryKey
    val patientId: String = "PAT-${UUID.randomUUID().toString().take(8).uppercase()}",
    val abhaNumber: String,
    val encryptedAbha: String = "",
    val abhaBlindIndex: String = "",
    val name: String,
    val age: Int,
    val gender: String, // "Female", "Male", "Other"
    val isPregnant: Boolean = false,
    val phone: String = "",
    val phoneNumber: String = phone,
    val village: String = "PHC Chevella, Telangana",
    val location: String = village,
    val hasSmartphone: Boolean = false,
    val activeMedications: String = "", // Comma-separated active meds e.g. "Warfarin, Metformin"
    val lastVisitTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = lastVisitTimestamp,
    val updatedAt: Long = lastVisitTimestamp,
    val syncStatus: String = "CACHED_OFFLINE",
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "encounters",
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["triageTier"])
    ]
)
data class EncounterEntity(
    @PrimaryKey
    val encounterId: String = "ENC-${UUID.randomUUID().toString().take(8).uppercase()}",
    val patientId: String,
    val facilityId: String = "PHC-CHV-501",
    val operatorId: String = "ANM-704",
    val triageTier: String = "GREEN", // "RED", "YELLOW", "GREEN"
    val triageRationale: String = "",
    val referralRequired: Boolean = false,
    val chiefComplaint: String = "",
    val rawNarrative: String = "",
    val rawVoiceTranscript: String = rawNarrative,
    val historyPresentIllness: String = "",
    val associatedSymptomsJson: String = "[]",
    val physicalExamFindings: String = "",
    val differentialDiagnosesJson: String = "[]",
    val immediateActionsJson: String = "[]",
    val medicationsAdvisedJson: String = "[]",
    val redFlagWarningsJson: String = "[]",
    val followUpPlan: String = "",
    val soapJson: String = "{}",
    val fhirBundleJson: String = "{}",
    val fhirResourceId: String = "bundle-${UUID.randomUUID()}",
    val fhirResourceType: String = "ClinicalImpression",
    val snomedConcepts: String = "",
    val vitalsSummary: String = "",
    val bpSystolic: Int = 120,
    val bpDiastolic: Int = 80,
    val bloodPressure: String = "$bpSystolic/$bpDiastolic mmHg",
    val spo2: Int = 98,
    val spo2Percent: Int? = spo2,
    val heartRate: Int = 75,
    val pulseBpm: Int? = heartRate,
    val temperatureF: Float? = 98.6f,
    val weightKg: Float? = null,
    val prescribedMedications: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val encounterDate: Long = timestamp,
    val syncedNhcx: Boolean = false,
    val syncedFhir: Boolean = false,
    val isSynced: Boolean = syncedNhcx && syncedFhir,
    val syncStatus: String = if (isSynced) "SYNCED" else "CACHED_OFFLINE",
    val syncRetryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val syncedAt: Long? = null,
    val syncErrorMessage: String? = null,
    val createdAt: Long = timestamp,
    val updatedAt: Long = timestamp
)

data class PatientWithEncounters(
    @Embedded
    val patient: PatientEntity,
    @Relation(
        parentColumn = "patientId",
        entityColumn = "patientId"
    )
    val encounters: List<EncounterEntity>
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["priority", "createdAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val queueId: Long = id,
    val targetEndpoint: String = "NHCX", // "NHCX" or "FHIR_ABDM"
    val payloadType: String = "CLINICAL_IMPRESSION", // "CLAIM_TRANSACTION" or "CLINICAL_IMPRESSION"
    val recordId: String = "",
    val entityType: String = "ENCOUNTER",
    val entityId: String = recordId,
    val patientId: String = "",
    val patientName: String = "",
    val payloadJson: String = "{}",
    val priority: Int = 2, // 1 = High (RED), 2 = Normal (YELLOW), 3 = Low (GREEN)
    val triageTier: String = "YELLOW",
    val retryCount: Int = 0,
    val attemptCount: Int = retryCount,
    val maxRetries: Int = 5,
    val status: String = "PENDING", // "PENDING", "SYNCED", "FAILED", "QUEUED"
    val createdAt: Long = System.currentTimeMillis(),
    val enqueuedAt: Long = createdAt,
    val lastAttemptAt: Long = 0,
    val lastError: String? = null,
    val errorMessage: String? = lastError,
    val syncedAt: Long? = null
)

sealed class AtomicSyncStatus {
    object Idle : AtomicSyncStatus()
    data class Syncing(val currentItem: String = "", val progress: Float = 0f) : AtomicSyncStatus()
    data class Completed(val syncedCount: Int = 0) : AtomicSyncStatus()
    data class Failed(val error: String = "") : AtomicSyncStatus()
    object Offline : AtomicSyncStatus()
}
