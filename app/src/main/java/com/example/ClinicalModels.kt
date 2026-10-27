package com.example

data class TriageInfo(
    val tier: TriageTier,
    val rationale: String,
    val referralRequired: Boolean
)

data class SubjectiveData(
    val chiefComplaint: String = "",
    val historyPresentIllness: String = "",
    val associatedSymptoms: List<String> = emptyList()
)

data class ObjectiveData(
    val vitalsSummary: String = "Not provided",
    val bpSystolic: Int = 120,
    val bpDiastolic: Int = 80,
    val heartRate: Int = 76,
    val spo2: Int = 98,
    val temperatureF: Float = 98.6f,
    val respiratoryRate: Int = 18,
    val physicalExamFindings: String = "Alert, oriented, clear breath sounds"
)

data class DifferentialDiagnosis(
    val condition: String,
    val icd10Code: String,
    val urgency: String, // "High", "Medium", "Low"
    val confidence: Float = 0.85f
)

data class MedicationAdvised(
    val name: String,
    val dosage: String,
    val frequency: String,
    val safetyFlag: String = "Clear", // "Clear" | "Warning" | "Contraindicated"
    val warningDetail: String = "",
    val suggestedSubstitute: String = ""
)

data class PlanData(
    val immediateActions: List<String> = emptyList(),
    val medicationsAdvised: List<MedicationAdvised> = emptyList(),
    val redFlagWarnings: List<String> = emptyList(),
    val followUp: String = "Routine 7 days follow-up at PHC"
)

data class AssessmentData(
    val differentialDiagnoses: List<DifferentialDiagnosis> = emptyList(),
    val safetyValidationAlerts: List<String> = emptyList()
)

data class SoapNote(
    val subjective: SubjectiveData = SubjectiveData(),
    val objective: ObjectiveData = ObjectiveData(),
    val assessment: AssessmentData = AssessmentData(),
    val plan: PlanData = PlanData()
)

data class AbdmFhirMapping(
    val resourceType: String = "ClinicalImpression",
    val snomedConcept: String = "422843007 | Clinical impression |",
    val syncStatus: String = "Ready for Edge-to-Cloud Sync",
    val fhirJsonPayload: String = ""
)

data class ClinicalEncounter(
    val id: String,
    val patientId: String,
    val patientName: String,
    val age: Int,
    val gender: String,
    val abhaNumber: String,
    val facilityId: String,
    val operatorId: String,
    val rawCodeSwitchingInput: String,
    val detectedLanguage: String,
    val triage: TriageInfo,
    val soapNote: SoapNote,
    val abdmFhirMapping: AbdmFhirMapping,
    val timestamp: Long = System.currentTimeMillis(),
    val isSyncedNhcx: Boolean = false,
    val isSyncedFhir: Boolean = false
)
