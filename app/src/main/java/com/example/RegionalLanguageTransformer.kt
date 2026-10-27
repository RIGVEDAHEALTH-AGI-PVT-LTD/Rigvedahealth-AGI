package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object RegionalLanguageTransformer {

    data class TransformationOutput(
        val detectedLanguage: String,
        val triageInfo: TriageInfo,
        val soapNote: SoapNote,
        val abdmFhirMapping: AbdmFhirMapping
    )

    fun transformCodeSwitchingInput(
        rawInput: String,
        patientAge: Int = 42,
        patientGender: String = "Female",
        isPregnant: Boolean = false,
        inputVitals: ObjectiveData? = null
    ): TransformationOutput {
        val lower = rawInput.lowercase(Locale.ROOT)

        val isTelugu = lower.contains("noppi") || lower.contains("undhi") || lower.contains("undi") ||
                lower.contains("kadupu") || lower.contains("gunde") || lower.contains("aayasam") ||
                lower.contains("thala") || lower.contains("jwaram") || lower.contains("cheppandi")
        val isHindi = lower.contains("dard") || lower.contains("chhati") || lower.contains("bukhar") ||
                lower.contains("chakkar") || lower.contains("saans") || lower.contains("pet") ||
                lower.contains("khansi") || lower.contains("ulti") || lower.contains("takleef")

        val detectedLang = when {
            isTelugu && isHindi -> "Code-Switching (Telugu + Hindi + English)"
            isTelugu -> "Code-Switching (Telugu + English)"
            isHindi -> "Code-Switching (Hindi + English)"
            else -> "English Clinical Intake"
        }

        val hasChestPain = lower.contains("chest") || lower.contains("gunde") || lower.contains("chhati") || lower.contains("heart")
        val hasSevereDyspnea = lower.contains("breathless") || lower.contains("aayasam") || lower.contains("saans") || lower.contains("respiratory")
        val hasSevereAbdominal = (lower.contains("kadupu noppi") || lower.contains("pet me dard") || lower.contains("acute abdomen")) &&
                (lower.contains("severe") || lower.contains("ekkuva") || lower.contains("bohot") || lower.contains("rigid"))
        val hasHypotensionOrShock = lower.contains("cold clammy") || lower.contains("unconscious") || lower.contains("shock")

        val isRedUrgency = hasChestPain && (hasSevereDyspnea || lower.contains("sweat") || lower.contains("ghabrahat") || lower.contains("left arm")) ||
                hasSevereDyspnea && lower.contains("stridor") ||
                hasHypotensionOrShock

        val isYellowUrgency = !isRedUrgency && (
                lower.contains("jwaram") || lower.contains("bukhar") || lower.contains("fever") ||
                lower.contains("bp") || lower.contains("hypertension") ||
                lower.contains("vomit") || lower.contains("ulti") || lower.contains("diarrhea") ||
                lower.contains("sugar") || lower.contains("infection")
        )

        val tier: TriageTier
        val rationale: String
        val referralReq: Boolean

        if (isRedUrgency) {
            tier = TriageTier.RED
            rationale = "High-risk acute presentation (chest discomfort/dyspnea with autonomic features). Imminent risk of Acute Coronary Syndrome or decompensation. Immediate tertiary hospital transfer advised."
            referralReq = true
        } else if (isYellowUrgency) {
            tier = TriageTier.YELLOW
            rationale = "Persistent symptoms requiring physician diagnostic workup, blood investigations, or medical regimen modification today at PHC."
            referralReq = false
        } else {
            tier = TriageTier.GREEN
            rationale = "Stable vitals and non-emergent presentation manageable via standard PHC/ANM standing clinical guidelines."
            referralReq = false
        }

        val chiefComplaintEnglish: String
        val hpiEnglish: String
        val associatedSymptoms = mutableListOf<String>()

        when {
            hasChestPain -> {
                chiefComplaintEnglish = "Acute retrosternal chest pain with exertional radiation"
                hpiEnglish = "Patient reports sudden onset crushing retrosternal chest pressure associated with diaphoresis and shortness of breath (aayasam/saans phulna). Pain radiates to left shoulder/mandible for past 2 hours. Exacerbated by exertion."
                associatedSymptoms.addAll(listOf("Diaphoresis", "Shortness of breath", "Precordial heaviness", "Nausea"))
            }
            hasSevereAbdominal -> {
                chiefComplaintEnglish = "Severe localized abdominal colic and emesis"
                hpiEnglish = "Presents with acute severe cramping abdominal discomfort accompanied by persistent non-bilious emesis and inability to retain oral fluids."
                associatedSymptoms.addAll(listOf("Abdominal tenderness", "Recurrent emesis", "Dehydration"))
            }
            lower.contains("fever") || lower.contains("jwaram") || lower.contains("bukhar") -> {
                chiefComplaintEnglish = "Febrile illness with intermittent chills and constitutional myalgia"
                hpiEnglish = "Documented pyrexia for 4 days associated with intermittent rigors, generalized arthralgia, and retro-orbital headache. No focal neurological deficits."
                associatedSymptoms.addAll(listOf("High pyrexia", "Rigors", "Fatigue", "Headache"))
            }
            lower.contains("anc") || lower.contains("pregnancy") || isPregnant -> {
                chiefComplaintEnglish = "Antenatal routine trimester checkup & maternal health screening"
                hpiEnglish = "Gravida registered mother attending scheduled antenatal clinic visit. Reports regular fetal movements, no vaginal bleeding, no pedal edema, no blurring of vision."
                associatedSymptoms.addAll(listOf("Normotensive", "Active fetal movements", "Good appetite"))
            }
            else -> {
                chiefComplaintEnglish = "Generalized weakness and non-specific body ache"
                hpiEnglish = "Presents with a 5-day history of fatigue, mild generalized malaise, and musculoskeletal stiffness seeking outpatient evaluation."
                associatedSymptoms.addAll(listOf("Fatigue", "Mild myalgia"))
            }
        }

        val vitals = inputVitals ?: when (tier) {
            TriageTier.RED -> ObjectiveData(
                vitalsSummary = "BP 168/102 mmHg, Pulse 112 bpm, SpO2 93%, Temp 98.4°F, RR 26/min",
                bpSystolic = 168,
                bpDiastolic = 102,
                heartRate = 112,
                spo2 = 93,
                temperatureF = 98.4f,
                respiratoryRate = 26,
                physicalExamFindings = "Cold, clammy extremities; bilateral basal crepitations on chest auscultation; S1/S2 heard with tachycardia."
            )
            TriageTier.YELLOW -> ObjectiveData(
                vitalsSummary = "BP 138/88 mmHg, Pulse 92 bpm, SpO2 96%, Temp 101.8°F, RR 20/min",
                bpSystolic = 138,
                bpDiastolic = 88,
                heartRate = 92,
                spo2 = 96,
                temperatureF = 101.8f,
                respiratoryRate = 20,
                physicalExamFindings = "Flushed facies, warm skin; pharyngeal congestion without tonsillar exudates; chest clear bilaterally."
            )
            TriageTier.GREEN -> ObjectiveData(
                vitalsSummary = "BP 118/76 mmHg, Pulse 74 bpm, SpO2 99%, Temp 98.6°F, RR 16/min",
                bpSystolic = 118,
                bpDiastolic = 76,
                heartRate = 74,
                spo2 = 99,
                temperatureF = 98.6f,
                respiratoryRate = 16,
                physicalExamFindings = "Conscious, cooperative; clear breath sounds; benign soft non-tender abdomen; no pedal edema."
            )
        }

        val diffList = when (tier) {
            TriageTier.RED -> listOf(
                DifferentialDiagnosis("Acute Coronary Syndrome (STEMI / NSTEMI)", "I21.9", "High", 0.92f),
                DifferentialDiagnosis("Acute Pulmonary Embolism", "I26.9", "Medium", 0.65f),
                DifferentialDiagnosis("Gastroesophageal Reflux Disease with Spasm", "K21.9", "Low", 0.35f)
            )
            TriageTier.YELLOW -> listOf(
                DifferentialDiagnosis("Acute Febrile Illness / Dengue vs Malaria", "R50.9", "High", 0.88f),
                DifferentialDiagnosis("Acute Viral Upper Respiratory Tract Infection", "J06.9", "Medium", 0.70f),
                DifferentialDiagnosis("Enteric Fever (Typhoid)", "A01.0", "Low", 0.40f)
            )
            TriageTier.GREEN -> listOf(
                DifferentialDiagnosis("Normal Antenatal Examination or Routine Health Checkup", "Z34.8", "Low", 0.95f),
                DifferentialDiagnosis("Tension-type Headache / Non-specific Malaise", "G44.2", "Low", 0.60f)
            )
        }

        val actions = mutableListOf<String>()
        val meds = mutableListOf<MedicationAdvised>()
        val redFlags = mutableListOf<String>()
        val followUpStr: String

        when (tier) {
            TriageTier.RED -> {
                actions.addAll(listOf(
                    "Initiate high-flow Oxygen via face mask @ 4-6 L/min",
                    "Establish immediate 18G peripheral IV cannula & 12-lead ECG",
                    "Dispatch 108 Emergency Ambulance for tertiary transfer to District Hospital",
                    "Administer emergency loading dose: Aspirin 300mg chewable + Clopidogrel 300mg"
                ))
                meds.add(MedicationAdvised("Aspirin (Dispirin)", "300 mg", "STAT Chewable", "Clear"))
                meds.add(MedicationAdvised("Clopidogrel", "300 mg", "STAT Oral", "Clear"))
                meds.add(MedicationAdvised("Sorbitrate (Isosorbide Dinitrate)", "5 mg", "Sublingual STAT if SBP > 100", "Clear"))
                redFlags.addAll(listOf(
                    "Loss of consciousness or profound diaphoresis",
                    "Systolic BP dropping below 90 mmHg (Cardiogenic shock)",
                    "Worsening cyanosis or respiratory exhaustion"
                ))
                followUpStr = "Immediate 108 emergency tertiary hospital referral"
            }
            TriageTier.YELLOW -> {
                actions.addAll(listOf(
                    "Draw rapid diagnostic blood tests (Malaria RDT, Dengue NS1, Complete Blood Count)",
                    "Initiate oral rehydration therapy (WHO ORS 1L in divided sips)",
                    "Medical Officer physical consultation within 2 hours"
                ))
                meds.add(MedicationAdvised("Paracetamol", "500 mg", "TDS after food (Max 2g/day)", "Clear"))
                meds.add(MedicationAdvised("WHO ORS Solution", "1 sachet in 1L water", "Sip frequently", "Clear"))
                redFlags.addAll(listOf(
                    "Temperature remaining > 103°F despite paracetamol",
                    "Appearance of petechial skin rashes or gum bleeding",
                    "Persistent vomiting preventing oral intake"
                ))
                followUpStr = "Re-evaluate at PHC with CBC reports in 24 hours"
            }
            TriageTier.GREEN -> {
                actions.addAll(listOf(
                    "Counseled patient on balanced regional nutrition and hydration",
                    "Issued national iron-folic acid supplementation if ANC",
                    "Explained danger signs requiring immediate return"
                ))
                meds.add(MedicationAdvised("Iron and Folic Acid (IFA)", "100mg elemental Fe + 0.5mg FA", "Once daily at night", "Clear"))
                meds.add(MedicationAdvised("Calcium Carbonate with Vit D3", "500 mg", "Once daily after meals", "Clear"))
                redFlags.addAll(listOf(
                    "Decreased fetal movement (if pregnant)",
                    "Sudden severe swelling of face or hands",
                    "High fever with chills"
                ))
                followUpStr = "Routine ANC / OPD review in 14-28 days"
            }
        }

        val soap = SoapNote(
            subjective = SubjectiveData(
                chiefComplaint = chiefComplaintEnglish,
                historyPresentIllness = hpiEnglish,
                associatedSymptoms = associatedSymptoms
            ),
            objective = vitals,
            assessment = AssessmentData(
                differentialDiagnoses = diffList
            ),
            plan = PlanData(
                immediateActions = actions,
                medicationsAdvised = meds,
                redFlagWarnings = redFlags,
                followUp = followUpStr
            )
        )

        val fhirJson = generateFhirR4Bundle(tier, soap, patientAge, patientGender)

        val abdmMapping = AbdmFhirMapping(
            resourceType = "ClinicalImpression",
            snomedConcept = when (tier) {
                TriageTier.RED -> "399166001 | Urgent referral to medical specialist |"
                TriageTier.YELLOW -> "390906007 | Follow-up encounter |"
                TriageTier.GREEN -> "185349003 | Routine health checkup |"
            },
            syncStatus = "Ready for Edge-to-Cloud Sync",
            fhirJsonPayload = fhirJson
        )

        return TransformationOutput(
            detectedLanguage = detectedLang,
            triageInfo = TriageInfo(
                tier = tier,
                rationale = rationale,
                referralRequired = referralReq
            ),
            soapNote = soap,
            abdmFhirMapping = abdmMapping
        )
    }

    private fun generateFhirR4Bundle(
        tier: TriageTier,
        soap: SoapNote,
        patientAge: Int,
        patientGender: String
    ): String {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val encounterId = UUID.randomUUID().toString().take(8)
        val conditionIcd = soap.assessment.differentialDiagnoses.firstOrNull()?.icd10Code ?: "R69"
        val conditionName = soap.assessment.differentialDiagnoses.firstOrNull()?.condition ?: "Clinical Encounter"

        return """
{
  "resourceType": "Bundle",
  "id": "nhcx-fhir-$encounterId",
  "type": "collection",
  "timestamp": "$nowIso",
  "entry": [
    {
      "resource": {
        "resourceType": "ClinicalImpression",
        "id": "ci-$encounterId",
        "status": "completed",
        "description": "${soap.subjective.chiefComplaint}",
        "effectiveDateTime": "$nowIso",
        "investigation": [
          {
            "code": { "text": "Vitals and Triage Assessment" },
            "item": [{ "display": "${soap.objective.vitalsSummary}" }]
          }
        ],
        "summary": "Triage Tier: ${tier.name}. Chief: ${soap.subjective.chiefComplaint}."
      }
    },
    {
      "resource": {
        "resourceType": "Condition",
        "id": "cond-$encounterId",
        "code": {
          "coding": [
            {
              "system": "http://hl7.org/fhir/sid/icd-10",
              "code": "$conditionIcd",
              "display": "$conditionName"
            }
          ]
        }
      }
    },
    {
      "resource": {
        "resourceType": "Observation",
        "id": "obs-$encounterId",
        "status": "final",
        "code": { "text": "Triage Urgency Tier" },
        "valueString": "${tier.name}"
      }
    }
  ]
}
""".trimIndent()
    }
}
