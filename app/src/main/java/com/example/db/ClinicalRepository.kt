package com.example.db

import android.content.Context
import androidx.room.withTransaction
import com.example.ClinicalEncounter
import com.example.TriageTier
import com.example.security.AbhaSecurityManager
import com.example.security.AbhaValidator
import kotlinx.coroutines.flow.Flow

class ClinicalRepository(
    private val database: AppDatabase,
    private val appContext: Context? = null
) {

    val allPatients: Flow<List<PatientEntity>> = database.patientDao().getAllPatients()
    val allEncounters: Flow<List<EncounterEntity>> = database.encounterDao().getAllEncounters()
    val pendingSyncQueue: Flow<List<SyncQueueEntity>> = database.syncQueueDao().getPendingQueue()
    val allSyncEntries: Flow<List<SyncQueueEntity>> = database.syncQueueDao().getAllQueueEntries()
    val pendingSyncCount: Flow<Int> = database.syncQueueDao().getPendingCountFlow()
    val failedSyncEntries: Flow<List<SyncQueueEntity>> = database.syncQueueDao().getFailedQueueEntries()
    val failedSyncCount: Flow<Int> = database.syncQueueDao().getFailedCountFlow()

    suspend fun retrySyncItem(id: Long) {
        database.syncQueueDao().retryQueueItem(id)
    }

    suspend fun retryAllFailedSyncItems() {
        database.syncQueueDao().retryAllFailed()
    }

    suspend fun deleteSyncItem(id: Long) {
        database.syncQueueDao().deleteQueueItem(id)
    }

    suspend fun clearCompletedSyncItems() {
        database.syncQueueDao().clearSynced()
    }

    suspend fun getPatientHistoricalMedications(patientId: String): List<String> {
        val result = mutableSetOf<String>()
        // 1. Check patient profile active medications
        val patient = database.patientDao().getPatientById(patientId)
        if (patient != null && patient.activeMedications.isNotBlank()) {
            patient.activeMedications.split(",").forEach {
                val trimmed = it.trim()
                if (trimmed.isNotBlank()) result.add(trimmed)
            }
        }
        // 2. Check previous encounters
        val encounters = database.encounterDao().getEncountersForPatient(patientId)
        encounters.forEach { enc ->
            if (enc.prescribedMedications.isNotBlank()) {
                enc.prescribedMedications.split(",").forEach {
                    val trimmed = it.trim()
                    if (trimmed.isNotBlank()) result.add(trimmed)
                }
            }
        }
        return result.toList()
    }

    suspend fun getPatientByAbha(abha: String): PatientEntity? {
        val validatedAbha = try {
            AbhaValidator.requireValid(abha)
        } catch (e: Exception) {
            abha
        }
        val byNumber = database.patientDao().getPatientByAbha(validatedAbha)
        if (byNumber != null) return byNumber

        return if (appContext != null) {
            val blindIndex = AbhaSecurityManager.generateBlindIndex(appContext, validatedAbha)
            database.patientDao().getPatientByAbhaBlindIndex(blindIndex)
        } else null
    }

    suspend fun upsertPatient(patient: PatientEntity) {
        val validatedAbha = AbhaValidator.requireValid(patient.abhaNumber)
        val encryptedRecord = if (appContext != null && patient.encryptedAbha.isBlank()) {
            try {
                AbhaSecurityManager.encryptAbha(appContext, validatedAbha)
            } catch (e: Exception) {
                null
            }
        } else null

        val securePatient = patient.copy(
            abhaNumber = validatedAbha,
            encryptedAbha = encryptedRecord?.encryptedAbha ?: patient.encryptedAbha,
            abhaBlindIndex = encryptedRecord?.blindIndexHash ?: patient.abhaBlindIndex
        )
        database.patientDao().insertPatient(securePatient)
    }

    suspend fun saveClinicalConsultation(encounter: ClinicalEncounter): Boolean {
        return try {
            database.withTransaction {
                val medNames = encounter.soapNote.plan.medicationsAdvised.joinToString(", ") { it.name }

                // 1. Validate & Secure ABHA Card Number
                val validatedAbha = AbhaValidator.requireValid(encounter.abhaNumber)
                val encryptedRecord = if (appContext != null) {
                    try {
                        AbhaSecurityManager.encryptAbha(appContext, validatedAbha)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                // 2. Insert/Update patient
                val existingPatient = database.patientDao().getPatientById(encounter.patientId)
                val combinedMeds = if (existingPatient != null && existingPatient.activeMedications.isNotBlank()) {
                    "${existingPatient.activeMedications}, $medNames"
                } else {
                    medNames
                }

                val patient = PatientEntity(
                    patientId = encounter.patientId,
                    name = encounter.patientName,
                    age = encounter.age,
                    gender = encounter.gender,
                    abhaNumber = validatedAbha,
                    encryptedAbha = encryptedRecord?.encryptedAbha ?: (existingPatient?.encryptedAbha ?: ""),
                    abhaBlindIndex = encryptedRecord?.blindIndexHash ?: (existingPatient?.abhaBlindIndex ?: ""),
                    phone = existingPatient?.phone ?: "9848011223",
                    village = existingPatient?.village ?: "Ramavaram PHC Sector",
                    hasSmartphone = existingPatient?.hasSmartphone ?: false,
                    activeMedications = combinedMeds,
                    lastVisitTimestamp = encounter.timestamp
                )
                database.patientDao().insertPatient(patient)

                // 2. Insert encounter
                val encounterEntity = EncounterEntity(
                    encounterId = encounter.id,
                    patientId = encounter.patientId,
                    facilityId = encounter.facilityId,
                    operatorId = encounter.operatorId,
                    triageTier = encounter.triage.tier.name,
                    triageRationale = encounter.triage.rationale,
                    referralRequired = encounter.triage.referralRequired,
                    chiefComplaint = encounter.soapNote.subjective.chiefComplaint,
                    rawNarrative = encounter.rawCodeSwitchingInput,
                    soapJson = encounter.soapNote.toString(),
                    fhirBundleJson = encounter.abdmFhirMapping.fhirJsonPayload,
                    vitalsSummary = encounter.soapNote.objective.vitalsSummary,
                    bpSystolic = encounter.soapNote.objective.bpSystolic,
                    bpDiastolic = encounter.soapNote.objective.bpDiastolic,
                    spo2 = encounter.soapNote.objective.spo2,
                    heartRate = encounter.soapNote.objective.heartRate,
                    temperatureF = encounter.soapNote.objective.temperatureF,
                    prescribedMedications = medNames,
                    timestamp = encounter.timestamp,
                    syncedNhcx = false,
                    syncedFhir = false
                )
                database.encounterDao().insertEncounter(encounterEntity)

                // 3. Enqueue to Atomic Sync Queue with triage priority
                val priority = when (encounter.triage.tier) {
                    TriageTier.RED -> 1
                    TriageTier.YELLOW -> 2
                    TriageTier.GREEN -> 3
                }

                val nhcxPayload = """
{
  "claimId": "NHCX-CLM-${encounter.id}",
  "facilityId": "${encounter.facilityId}",
  "operatorId": "${encounter.operatorId}",
  "abhaNumber": "${encounter.abhaNumber}",
  "triageTier": "${encounter.triage.tier.name}",
  "clinicalComplaint": "${encounter.soapNote.subjective.chiefComplaint}",
  "actionsCount": ${encounter.soapNote.plan.immediateActions.size},
  "timestamp": ${encounter.timestamp}
}
""".trimIndent()

                database.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        targetEndpoint = "NHCX",
                        payloadType = "CLAIM_TRANSACTION",
                        recordId = encounter.id,
                        payloadJson = nhcxPayload,
                        priority = priority,
                        status = "PENDING"
                    )
                )

                database.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        targetEndpoint = "FHIR_ABDM",
                        payloadType = "CLINICAL_IMPRESSION",
                        recordId = encounter.id,
                        payloadJson = encounter.abdmFhirMapping.fhirJsonPayload,
                        priority = priority,
                        status = "PENDING"
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getEncountersForPatientFlow(patientId: String): Flow<List<EncounterEntity>> {
        return database.encounterDao().getEncountersByPatientId(patientId)
    }

    suspend fun getEncountersForPatient(patientId: String): List<EncounterEntity> {
        return database.encounterDao().getEncountersForPatient(patientId)
    }

    suspend fun seedInitialDataIfEmpty() {
        fun makeSeedPatient(
            id: String,
            name: String,
            age: Int,
            gender: String,
            abha: String,
            phone: String,
            village: String,
            hasPhone: Boolean,
            meds: String,
            timeAgo: Long
        ): PatientEntity {
            val validatedAbha = AbhaValidator.requireValid(abha)
            val encryptedRecord = if (appContext != null) {
                try {
                    AbhaSecurityManager.encryptAbha(appContext, validatedAbha)
                } catch (e: Exception) {
                    null
                }
            } else null

            return PatientEntity(
                patientId = id,
                name = name,
                age = age,
                gender = gender,
                abhaNumber = validatedAbha,
                encryptedAbha = encryptedRecord?.encryptedAbha ?: "",
                abhaBlindIndex = encryptedRecord?.blindIndexHash ?: "",
                phone = phone,
                village = village,
                hasSmartphone = hasPhone,
                activeMedications = meds,
                lastVisitTimestamp = System.currentTimeMillis() - timeAgo
            )
        }

        val initialPatients = listOf(
            makeSeedPatient("P-101", "Lakshmi Devi", 26, "Female", "12-3456-7890-1234", "9848011223", "Chintalapudi Sub-Center", true, "Warfarin 2mg, Iron Folic Acid", 86400000L * 2),
            makeSeedPatient("P-102", "Ramesh Kumar", 58, "Male", "34-9876-5432-1098", "9848033445", "Peddapuram Ward 3", false, "Enalapril 5mg, Metformin 500mg", 86400000L * 1),
            makeSeedPatient("P-103", "Savitri Bai", 45, "Female", "77-2233-4455-6677", "9848055667", "Bheemavaram Colony", false, "Omeprazole 20mg, Calcium Vit D3", 3600000L * 5),
            makeSeedPatient("P-104", "Anjaneyulu M.", 62, "Male", "88-9900-1122-3344", "9848077889", "Gollaprolu PHC Area", false, "Clopidogrel 75mg, Atorvastatin 20mg", 3600000L * 2)
        )
        database.patientDao().insertAll(initialPatients)

        // Seed longitudinal historical encounters for trend tracking
        val existingEncounters = database.encounterDao().getEncountersForPatient("P-101")
        if (existingEncounters.isEmpty()) {
            val now = System.currentTimeMillis()
            val dayMs = 86400000L
            val historicalEncounters = listOf(
                // Lakshmi Devi (P-101): Clear worsening trend: GREEN -> YELLOW -> RED
                EncounterEntity(
                    encounterId = "ENC-HIST-101-1",
                    patientId = "P-101",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "GREEN",
                    triageRationale = "Stable vitals and normal obstetric exam. Routine NCD and ANC screening.",
                    referralRequired = false,
                    chiefComplaint = "Routine antenatal care and NCD screening. Mild fatigue during farm work.",
                    rawNarrative = "Regular checkup kosam vachanu, konchem alasata ga undi.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 120/80 mmHg, HR 72 bpm, SpO2 99%, Temp 98.4°F, RR 16/min",
                    bpSystolic = 120,
                    bpDiastolic = 80,
                    bloodPressure = "120/80 mmHg",
                    spo2 = 99,
                    heartRate = 72,
                    temperatureF = 98.4f,
                    differentialDiagnosesJson = """[{"condition":"Routine General Examination","icd10_code":"Z00.0","urgency":"Low"}]""",
                    prescribedMedications = "Iron Folic Acid, Calcium Vit D3",
                    timestamp = now - (30 * dayMs),
                    encounterDate = now - (30 * dayMs)
                ),
                EncounterEntity(
                    encounterId = "ENC-HIST-101-2",
                    patientId = "P-101",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "YELLOW",
                    triageRationale = "Stage 1 hypertension with borderline tachycardia; exertional fatigue. Medical Officer consultation advised.",
                    referralRequired = false,
                    chiefComplaint = "Occasional palpitations and exertional dyspnea while walking uphill for 3 days.",
                    rawNarrative = "Gunde gattiga kottukuntundi, aayasam vastundi metlu ekkinappudu.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 142/90 mmHg, HR 88 bpm, SpO2 97%, Temp 98.6°F, RR 20/min",
                    bpSystolic = 142,
                    bpDiastolic = 90,
                    bloodPressure = "142/90 mmHg",
                    spo2 = 97,
                    heartRate = 88,
                    temperatureF = 98.6f,
                    differentialDiagnosesJson = """[{"condition":"Essential Hypertension","icd10_code":"I10","urgency":"Medium"}]""",
                    prescribedMedications = "Amlodipine 5mg, Iron Folic Acid",
                    timestamp = now - (14 * dayMs),
                    encounterDate = now - (14 * dayMs)
                ),
                EncounterEntity(
                    encounterId = "ENC-HIST-101-3",
                    patientId = "P-101",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "RED",
                    triageRationale = "High-risk acute coronary syndrome presentation with retrosternal radiation and autonomic symptoms. Immediate tertiary referral advised.",
                    referralRequired = true,
                    chiefComplaint = "Acute retrosternal chest pain radiating to left shoulder with severe diaphoresis for 2 hours.",
                    rawNarrative = "Gunde noppi ga undi 2 hours nundi, aayasam ekkuva ayyindi, chematalu paduthunnayi.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 168/102 mmHg, HR 112 bpm, SpO2 93%, Temp 98.4°F, RR 26/min",
                    bpSystolic = 168,
                    bpDiastolic = 102,
                    bloodPressure = "168/102 mmHg",
                    spo2 = 93,
                    heartRate = 112,
                    temperatureF = 98.4f,
                    differentialDiagnosesJson = """[{"condition":"Acute Coronary Syndrome (STEMI / NSTEMI)","icd10_code":"I21.9","urgency":"High"}]""",
                    prescribedMedications = "Aspirin 300mg, Clopidogrel 300mg, Sorbitrate 5mg",
                    timestamp = now - (2 * dayMs),
                    encounterDate = now - (2 * dayMs)
                ),

                // Ramesh Kumar (P-102): Febrile progression: GREEN -> YELLOW
                EncounterEntity(
                    encounterId = "ENC-HIST-102-1",
                    patientId = "P-102",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "GREEN",
                    triageRationale = "Controlled type 2 diabetes and hypertension. Chronic medication refill authorized.",
                    referralRequired = false,
                    chiefComplaint = "Routine monthly NCD medicine refill and fasting blood sugar check.",
                    rawNarrative = "Sugar aur BP ki dawai lene aaya hoon, sab theek hai.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 124/82 mmHg, HR 74 bpm, SpO2 98%, Temp 98.6°F, RR 18/min",
                    bpSystolic = 124,
                    bpDiastolic = 82,
                    bloodPressure = "124/82 mmHg",
                    spo2 = 98,
                    heartRate = 74,
                    temperatureF = 98.6f,
                    differentialDiagnosesJson = """[{"condition":"Type 2 Diabetes Mellitus","icd10_code":"E11.9","urgency":"Low"}]""",
                    prescribedMedications = "Enalapril 5mg, Metformin 500mg",
                    timestamp = now - (20 * dayMs),
                    encounterDate = now - (20 * dayMs)
                ),
                EncounterEntity(
                    encounterId = "ENC-HIST-102-2",
                    patientId = "P-102",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "YELLOW",
                    triageRationale = "High grade fever for 4 days with rigors, mild dehydration. Peripheral blood smear sent for malaria.",
                    referralRequired = false,
                    chiefComplaint = "High pyrexia 102.5°F for 4 days, vomiting, rigors (Tez bukhar).",
                    rawNarrative = "4 din se bohot tez bukhar hai, ulti ho rahi hai aur kapkapi choot rahi hai.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 138/88 mmHg, HR 94 bpm, SpO2 96%, Temp 102.5°F, RR 22/min",
                    bpSystolic = 138,
                    bpDiastolic = 88,
                    bloodPressure = "138/88 mmHg",
                    spo2 = 96,
                    heartRate = 94,
                    temperatureF = 102.5f,
                    differentialDiagnosesJson = """[{"condition":"Acute Febrile Illness (Suspected Malaria / Dengue)","icd10_code":"A90","urgency":"Medium"}]""",
                    prescribedMedications = "Paracetamol 650mg, ORS, Azithromycin 500mg",
                    timestamp = now - (4 * dayMs),
                    encounterDate = now - (4 * dayMs)
                ),

                // Anjaneyulu M. (P-104): Respiratory decompensation: YELLOW -> RED
                EncounterEntity(
                    encounterId = "ENC-HIST-104-1",
                    patientId = "P-104",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "YELLOW",
                    triageRationale = "Exacerbation of chronic respiratory airway limitation. Inhaled bronchodilator therapy advised.",
                    referralRequired = false,
                    chiefComplaint = "Worsening chronic cough and exertional breathlessness on exertion.",
                    rawNarrative = "Daggara ekkuva ayyindi, aayasam ga undi nadisthe.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 136/86 mmHg, HR 84 bpm, SpO2 94%, Temp 98.6°F, RR 22/min",
                    bpSystolic = 136,
                    bpDiastolic = 86,
                    bloodPressure = "136/86 mmHg",
                    spo2 = 94,
                    heartRate = 84,
                    temperatureF = 98.6f,
                    differentialDiagnosesJson = """[{"condition":"Chronic Obstructive Pulmonary Disease","icd10_code":"J44.9","urgency":"Medium"}]""",
                    prescribedMedications = "Salbutamol Inhaler, Atorvastatin 20mg",
                    timestamp = now - (15 * dayMs),
                    encounterDate = now - (15 * dayMs)
                ),
                EncounterEntity(
                    encounterId = "ENC-HIST-104-2",
                    patientId = "P-104",
                    facilityId = "PHC-CHV-501",
                    operatorId = "ANM-704",
                    triageTier = "RED",
                    triageRationale = "Critical acute hypoxemic respiratory distress, cyanosis, tachycardia. Immediate tertiary transfer required.",
                    referralRequired = true,
                    chiefComplaint = "Acute severe dyspnea, wheezing, central cyanosis, and chest tightness.",
                    rawNarrative = "Gali aadatam ledu, pedavulu neelam ga ayyayi, chala aapatkaal situation.",
                    soapJson = "{}",
                    fhirBundleJson = "{}",
                    vitalsSummary = "BP 155/95 mmHg, HR 124 bpm, SpO2 90%, Temp 98.8°F, RR 28/min",
                    bpSystolic = 155,
                    bpDiastolic = 95,
                    bloodPressure = "155/95 mmHg",
                    spo2 = 90,
                    heartRate = 124,
                    temperatureF = 98.8f,
                    differentialDiagnosesJson = """[{"condition":"Acute Respiratory Failure with COPD Exacerbation","icd10_code":"J96.0","urgency":"High"}]""",
                    prescribedMedications = "High-flow O2, Deriphyllin, Hydrocortisone 100mg IV",
                    timestamp = now - (1 * dayMs),
                    encounterDate = now - (1 * dayMs)
                )
            )
            database.encounterDao().insertEncounters(historicalEncounters)
        }

        val existingQueue = database.syncQueueDao().getPendingQueueList()
        if (existingQueue.isEmpty()) {
            val now = System.currentTimeMillis()
            val seedQueue = listOf(
                SyncQueueEntity(
                    targetEndpoint = "NHCX",
                    payloadType = "CLAIM_TRANSACTION",
                    recordId = "ENC-HIST-101-3",
                    patientId = "P-101",
                    patientName = "Lakshmi Devi",
                    priority = 1,
                    triageTier = "RED",
                    retryCount = 2,
                    status = "FAILED",
                    lastAttemptAt = now - (12 * 60 * 1000L),
                    lastError = "HTTP 504 Gateway Timeout: District NHCX Claims Bridge Unreachable",
                    createdAt = now - (35 * 60 * 1000L)
                ),
                SyncQueueEntity(
                    targetEndpoint = "FHIR_ABDM",
                    payloadType = "CLINICAL_IMPRESSION",
                    recordId = "ENC-HIST-104-2",
                    patientId = "P-104",
                    patientName = "Anjaneyulu M.",
                    priority = 1,
                    triageTier = "RED",
                    retryCount = 0,
                    status = "PENDING",
                    lastAttemptAt = 0,
                    createdAt = now - (15 * 60 * 1000L)
                ),
                SyncQueueEntity(
                    targetEndpoint = "NHCX",
                    payloadType = "CLAIM_TRANSACTION",
                    recordId = "ENC-HIST-102-2",
                    patientId = "P-102",
                    patientName = "Ramesh Kumar",
                    priority = 2,
                    triageTier = "YELLOW",
                    retryCount = 0,
                    status = "PENDING",
                    lastAttemptAt = 0,
                    createdAt = now - (8 * 60 * 1000L)
                )
            )
            database.syncQueueDao().enqueueItems(seedQueue)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ClinicalRepository? = null

        fun getInstance(context: Context): ClinicalRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val repo = ClinicalRepository(db, context.applicationContext)
                INSTANCE = repo
                repo
            }
        }
    }
}
