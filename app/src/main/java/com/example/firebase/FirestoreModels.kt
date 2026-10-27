package com.example.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val facilityId: String = "PHC-CHV-501",
    val role: String = "MO",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestorePatient(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val userId: String = "",
    val abhaNumber: String = "",
    val encryptedAbha: String = "",
    val abhaBlindIndex: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: String = "Other",
    val isPregnant: Boolean = false,
    val phone: String = "",
    val village: String = "",
    val activeMedications: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreEncounter(
    @DocumentId val id: String = "",
    val encounterId: String = "",
    val userId: String = "",
    val patientId: String = "",
    val facilityId: String = "PHC-CHV-501",
    val operatorId: String = "ANM-704",
    val triageTier: String = "GREEN",
    val triageRationale: String = "",
    val referralRequired: Boolean = false,
    val chiefComplaint: String = "",
    val rawVoiceTranscript: String = "",
    val soapJson: String = "{}",
    val fhirBundleJson: String = "{}",
    val vitalsSummary: String = "",
    val bloodPressure: String = "",
    val spo2: Int = 98,
    val heartRate: Int = 75,
    val prescribedMedications: String = "",
    val syncedNhcx: Boolean = false,
    val syncedFhir: Boolean = false,
    val syncStatus: String = "CACHED_OFFLINE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
