package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.ObjectiveData
import com.example.RegionalLanguageTransformer
import com.example.SoapNote
import com.example.SubjectiveData
import com.example.TriageInfo
import com.example.TriageTier
import com.example.AssessmentData
import com.example.PlanData
import com.example.DifferentialDiagnosis
import com.example.MedicationAdvised
import com.example.AbdmFhirMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClinicalParser {

    private const val TAG = "GeminiClinicalParser"
    private const val MODEL_NAME = "gemini-2.5-flash"

    data class GeminiSoapResult(
        val rawJson: String,
        val triageInfo: TriageInfo,
        val soapNote: SoapNote,
        val abdmFhirMapping: AbdmFhirMapping,
        val isAiGenerated: Boolean
    )

    suspend fun parseTranscriptToSoap(
        transcript: String,
        patientAge: Int = 30,
        patientGender: String = "Female",
        isPregnant: Boolean = false,
        vitals: ObjectiveData? = null
    ): GeminiSoapResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        if (apiKey.isBlank()) {
            Log.w(TAG, "GEMINI_API_KEY not configured. Falling back to local offline transformer.")
            return@withContext fallbackToOffline(transcript, patientAge, patientGender, isPregnant, vitals)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
            }

            val systemInstruction = """
You are the Rigveda Neural Grid Core Clinical Agent, an offline-native clinical decision support system for Primary Health Centers (PHCs).
Translate code-switching regional clinical inputs (Telugu/Hindi mixed with English) into formal clinical English.
Classify clinical triage tier into: RED (Emergent), YELLOW (Urgent), GREEN (Routine).
Enforce dual-agent safety guardrails (never give single definitive diagnosis; provide differential diagnoses with ICD-10 codes).
Respond strictly in JSON matching the exact schema:
{
  "triage": {
    "tier": "RED | YELLOW | GREEN",
    "rationale": "Clinical reasoning",
    "referral_required": true
  },
  "soap_note": {
    "subjective": {
      "chief_complaint": "Standardized primary complaint in formal English",
      "history_present_illness": "Structured narrative translated to English",
      "associated_symptoms": ["symptom1", "symptom2"]
    },
    "objective": {
      "vitals_summary": "Extracted vitals",
      "physical_exam_findings": "Summary of observations"
    },
    "assessment": {
      "differential_diagnoses": [
        {"condition": "Condition Name", "icd10_code": "Code", "urgency": "High|Medium|Low"}
      ]
    },
    "plan": {
      "immediate_actions": ["Action 1"],
      "medications_advised": [
        {"name": "Drug Name", "dosage": "Dosage", "frequency": "Frequency", "safety_flag": "Clear | Warning"}
      ],
      "red_flag_warnings": ["Warning 1"],
      "follow_up": "Timeline or referral"
    }
  },
  "abdm_fhir_mapping": {
    "resource_type": "ClinicalImpression",
    "snomed_concept": "Concept placeholder",
    "sync_status": "Ready for Edge-to-Cloud Sync"
  }
}
""".trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "System: $systemInstruction\n\nPatient input: $transcript\nAge: $patientAge, Gender: $patientGender, Pregnant: $isPregnant"))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                val candidates = rootJson.optJSONArray("candidates")
                val firstPart = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!firstPart.isNullOrBlank()) {
                    val parsed = parseJsonToModels(firstPart, vitals)
                    if (parsed != null) {
                        return@withContext parsed.copy(isAiGenerated = true)
                    }
                }
            } else {
                val errText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Gemini API error ($responseCode): $errText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API: ${e.message}. Using offline fallback.", e)
        }

        fallbackToOffline(transcript, patientAge, patientGender, isPregnant, vitals)
    }

    private fun parseJsonToModels(rawJson: String, inputVitals: ObjectiveData?): GeminiSoapResult? {
        return try {
            val root = JSONObject(rawJson)

            // Triage
            val triageObj = root.optJSONObject("triage")
            val tierStr = triageObj?.optString("tier", "GREEN") ?: "GREEN"
            val tier = TriageTier.fromString(tierStr)
            val rationale = triageObj?.optString("rationale", "Clinical triage assessment") ?: ""
            val referral = triageObj?.optBoolean("referral_required", tier == TriageTier.RED) ?: (tier == TriageTier.RED)
            val triageInfo = TriageInfo(tier, rationale, referral)

            // SOAP
            val soapObj = root.optJSONObject("soap_note")
            val subjObj = soapObj?.optJSONObject("subjective")
            val chiefComplaint = subjObj?.optString("chief_complaint", "Clinical Consultation") ?: ""
            val hpi = subjObj?.optString("history_present_illness", "") ?: ""
            val symptoms = mutableListOf<String>()
            subjObj?.optJSONArray("associated_symptoms")?.let { arr ->
                for (i in 0 until arr.length()) symptoms.add(arr.getString(i))
            }
            val subjective = SubjectiveData(chiefComplaint, hpi, symptoms)

            val objObj = soapObj?.optJSONObject("objective")
            val vitalsSummary = objObj?.optString("vitals_summary", inputVitals?.vitalsSummary ?: "Stable vitals") ?: ""
            val exam = objObj?.optString("physical_exam_findings", inputVitals?.physicalExamFindings ?: "Normal exam") ?: ""
            val objective = inputVitals?.copy(vitalsSummary = vitalsSummary, physicalExamFindings = exam)
                ?: ObjectiveData(vitalsSummary = vitalsSummary, physicalExamFindings = exam)

            val assessObj = soapObj?.optJSONObject("assessment")
            val diffs = mutableListOf<DifferentialDiagnosis>()
            assessObj?.optJSONArray("differential_diagnoses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    diffs.add(
                        DifferentialDiagnosis(
                            condition = d.optString("condition", "Medical Condition"),
                            icd10Code = d.optString("icd10_code", "R69"),
                            urgency = d.optString("urgency", "Medium")
                        )
                    )
                }
            }
            val assessment = AssessmentData(differentialDiagnoses = diffs)

            val planObj = soapObj?.optJSONObject("plan")
            val actions = mutableListOf<String>()
            planObj?.optJSONArray("immediate_actions")?.let { arr ->
                for (i in 0 until arr.length()) actions.add(arr.getString(i))
            }
            val meds = mutableListOf<MedicationAdvised>()
            planObj?.optJSONArray("medications_advised")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    meds.add(
                        MedicationAdvised(
                            name = m.optString("name", "Medicine"),
                            dosage = m.optString("dosage", "Standard dose"),
                            frequency = m.optString("frequency", "OD"),
                            safetyFlag = m.optString("safety_flag", "Clear")
                        )
                    )
                }
            }
            val redFlags = mutableListOf<String>()
            planObj?.optJSONArray("red_flag_warnings")?.let { arr ->
                for (i in 0 until arr.length()) redFlags.add(arr.getString(i))
            }
            val followUp = planObj?.optString("follow_up", "Routine PHC review") ?: "Routine PHC review"
            val plan = PlanData(actions, meds, redFlags, followUp)

            val abdmObj = root.optJSONObject("abdm_fhir_mapping")
            val snomed = abdmObj?.optString("snomed_concept", "422843007") ?: "422843007"
            val fhirMapping = AbdmFhirMapping(
                resourceType = "ClinicalImpression",
                snomedConcept = snomed,
                syncStatus = "Ready for Edge-to-Cloud Sync",
                fhirJsonPayload = rawJson
            )

            GeminiSoapResult(
                rawJson = rawJson,
                triageInfo = triageInfo,
                soapNote = SoapNote(subjective, objective, assessment, plan),
                abdmFhirMapping = fhirMapping,
                isAiGenerated = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini JSON: ${e.message}")
            null
        }
    }

    private fun fallbackToOffline(
        transcript: String,
        patientAge: Int,
        patientGender: String,
        isPregnant: Boolean,
        vitals: ObjectiveData?
    ): GeminiSoapResult {
        return com.example.clinical.OfflineClinicalCommandEngine.processOfflineClinicalIntake(
            transcript = transcript,
            patientAge = patientAge,
            patientGender = patientGender,
            isPregnant = isPregnant,
            vitals = vitals
        )
    }
}
