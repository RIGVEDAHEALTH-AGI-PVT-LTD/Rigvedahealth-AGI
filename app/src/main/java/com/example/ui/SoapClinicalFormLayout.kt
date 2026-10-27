package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.*
import com.example.ai.GeminiClinicalParser
import com.example.clinical.DdiInteractionEngine
import com.example.security.AbhaValidationResult
import com.example.security.AbhaValidator
import com.example.speech.SpeechRecognitionManager
import com.example.db.EncounterEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoapClinicalFormLayout(
    initialPatientId: String = "P-101",
    initialPatientName: String = "Lakshmi Devi",
    initialPatientAge: Int = 26,
    initialPatientGender: String = "Female",
    initialAbhaNumber: String = "12-3456-7890-1234",
    initialPatientHistoricalMeds: List<String> = listOf("Warfarin 2mg"),
    facilityId: String = "UP-PHC-4401",
    operatorId: String = "ANM-8891",
    historicalEncounters: List<EncounterEntity> = emptyList(),
    onSaveConsultation: (ClinicalEncounter) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) }
    val sectionTitles = listOf("S: Subjective", "O: Objective", "A: Assessment", "P: Plan", "FHIR R4")

    // Modals
    var showVoiceModal by remember { mutableStateOf(false) }
    var showAbhaScanner by remember { mutableStateOf(false) }
    var showTriageHistorySheet by remember { mutableStateOf(false) }

    // Demographics
    var patientId by remember(initialPatientId) { mutableStateOf(initialPatientId) }
    var patientName by remember { mutableStateOf(initialPatientName) }
    var patientAge by remember { mutableStateOf(initialPatientAge.toString()) }
    var patientGender by remember { mutableStateOf(initialPatientGender) }
    var isPregnant by remember { mutableStateOf(initialPatientAge in 18..45 && initialPatientGender == "Female") }
    var abhaNumber by remember { mutableStateOf(initialAbhaNumber) }
    var historicalMeds by remember { mutableStateOf(initialPatientHistoricalMeds) }
    var showAbhaErrorDialog by remember { mutableStateOf(false) }
    var abhaErrorMessage by remember { mutableStateOf("") }

    // Subjective
    var rawInputText by remember {
        mutableStateOf("Gunde noppi ga undi for 2 hours, breathlessness (aayasam) ekkuva ayyindi, BP 170/100.")
    }
    var detectedLanguage by remember { mutableStateOf("Code-Switching (Telugu + English)") }
    var chiefComplaint by remember { mutableStateOf("Acute retrosternal chest pain with exertional radiation") }
    var hpiNarrative by remember {
        mutableStateOf("Patient reports sudden onset crushing retrosternal chest pressure associated with diaphoresis and shortness of breath (aayasam/saans phulna). Pain radiates to left shoulder/mandible for past 2 hours.")
    }
    val associatedSymptoms = remember {
        mutableStateListOf("Diaphoresis", "Shortness of breath", "Precordial heaviness", "Nausea")
    }

    // Objective
    var bpSystolic by remember { mutableStateOf("168") }
    var bpDiastolic by remember { mutableStateOf("102") }
    var heartRate by remember { mutableStateOf("112") }
    var spo2 by remember { mutableStateOf("93") }
    var tempF by remember { mutableStateOf("98.4") }
    var respRate by remember { mutableStateOf("26") }
    var physicalExam by remember {
        mutableStateOf("Cold clammy peripheries; bilateral basal lung crepitations; regular tachycardic heart sounds; no peripheral edema.")
    }

    // Triage
    var currentTier by remember { mutableStateOf(TriageTier.RED) }
    var triageRationale by remember {
        mutableStateOf("High-risk acute presentation (chest discomfort/dyspnea with autonomic features). Imminent risk of Acute Coronary Syndrome. Immediate tertiary referral advised.")
    }

    // Assessment Differentials
    val differentials = remember {
        mutableStateListOf(
            DifferentialDiagnosis("Acute Coronary Syndrome (STEMI / NSTEMI)", "I21.9", "High", 0.92f),
            DifferentialDiagnosis("Acute Pulmonary Embolism", "I26.9", "Medium", 0.65f),
            DifferentialDiagnosis("Gastroesophageal Reflux Disease with Spasm", "K21.9", "Low", 0.35f)
        )
    }

    // Plan
    val immediateActions = remember {
        mutableStateListOf(
            "Initiate high-flow Oxygen via face mask @ 4-6 L/min",
            "Establish immediate 18G peripheral IV cannula & 12-lead ECG",
            "Dispatch 108 Emergency Ambulance for tertiary transfer to District Hospital",
            "Administer emergency loading dose: Aspirin 300mg chewable + Clopidogrel 300mg"
        )
    }

    val medications = remember {
        mutableStateListOf(
            MedicationAdvised("Aspirin (Dispirin)", "300 mg", "STAT Chewable", "Clear"),
            MedicationAdvised("Clopidogrel", "300 mg", "STAT Oral", "Clear"),
            MedicationAdvised("Sorbitrate (Isosorbide Dinitrate)", "5 mg", "Sublingual STAT if SBP > 100", "Clear")
        )
    }

    val redFlags = remember {
        mutableStateListOf(
            "Loss of consciousness or profound diaphoresis",
            "Systolic BP dropping below 90 mmHg (Cardiogenic shock)",
            "Worsening cyanosis or respiratory exhaustion"
        )
    }

    var followUpTimeline by remember { mutableStateOf("Immediate 108 emergency tertiary hospital referral") }
    var fhirJsonPayload by remember { mutableStateOf("") }
    var isAiGenerated by remember { mutableStateOf(false) }

    // REAL-TIME DDI CROSS-REFERENCING ENGINE
    val ddiResult = remember(medications.toList(), historicalMeds, patientAge, isPregnant) {
        DdiInteractionEngine.checkInteractions(
            prescribedMeds = medications.toList(),
            patientHistoricalMeds = historicalMeds,
            patientAge = patientAge.toIntOrNull() ?: 30,
            isPregnant = isPregnant
        )
    }

    // Populate from Gemini / Speech Recognition
    fun applyParsedGeminiResult(result: GeminiClinicalParser.GeminiSoapResult, sourceTranscript: String) {
        rawInputText = sourceTranscript
        currentTier = result.triageInfo.tier
        triageRationale = result.triageInfo.rationale
        chiefComplaint = result.soapNote.subjective.chiefComplaint
        hpiNarrative = result.soapNote.subjective.historyPresentIllness

        associatedSymptoms.clear()
        associatedSymptoms.addAll(result.soapNote.subjective.associatedSymptoms)

        differentials.clear()
        differentials.addAll(result.soapNote.assessment.differentialDiagnoses)

        immediateActions.clear()
        immediateActions.addAll(result.soapNote.plan.immediateActions)

        medications.clear()
        medications.addAll(result.soapNote.plan.medicationsAdvised)

        redFlags.clear()
        redFlags.addAll(result.soapNote.plan.redFlagWarnings)

        followUpTimeline = result.soapNote.plan.followUp
        fhirJsonPayload = result.abdmFhirMapping.fhirJsonPayload
        isAiGenerated = result.isAiGenerated
    }

    // Initialize FHIR bundle if empty
    LaunchedEffect(Unit) {
        if (fhirJsonPayload.isEmpty()) {
            val res = RegionalLanguageTransformer.transformCodeSwitchingInput(
                rawInput = rawInputText,
                patientAge = patientAge.toIntOrNull() ?: 30,
                patientGender = patientGender,
                isPregnant = isPregnant
            )
            fhirJsonPayload = res.abdmFhirMapping.fhirJsonPayload
        }
    }

    // INLINE MICROPHONE & AUDIO WAVEFORM ENGINE
    val context = LocalContext.current
    val formSpeechManager = remember { SpeechRecognitionManager(context) }
    val isFormListening by formSpeechManager.isListening.collectAsStateWithLifecycle()
    val formRmsDb by formSpeechManager.rmsDb.collectAsStateWithLifecycle()
    val formTranscript by formSpeechManager.transcript.collectAsStateWithLifecycle()
    val formPartialTranscript by formSpeechManager.partialTranscript.collectAsStateWithLifecycle()
    val formDetectedEmergencyKeywords by formSpeechManager.detectedEmergencyKeywords.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) formSpeechManager.startListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            formSpeechManager.stopListening()
        }
    }

    fun finishFormDictation() {
        val captured = if (formPartialTranscript.isNotBlank()) {
            if (formTranscript.isNotBlank()) "$formTranscript $formPartialTranscript" else formPartialTranscript
        } else {
            formTranscript
        }
        formSpeechManager.stopListening()
        if (captured.isNotBlank()) {
            val textToAdd = captured.trim()
            rawInputText = if (rawInputText.isBlank()) textToAdd else "$rawInputText $textToAdd"
            val res = RegionalLanguageTransformer.transformCodeSwitchingInput(
                rawInput = rawInputText,
                patientAge = patientAge.toIntOrNull() ?: 30,
                patientGender = patientGender,
                isPregnant = isPregnant
            )
            currentTier = res.triageInfo.tier
            triageRationale = res.triageInfo.rationale
            chiefComplaint = res.soapNote.subjective.chiefComplaint
            hpiNarrative = res.soapNote.subjective.historyPresentIllness
            associatedSymptoms.clear()
            associatedSymptoms.addAll(res.soapNote.subjective.associatedSymptoms)
            differentials.clear()
            differentials.addAll(res.soapNote.assessment.differentialDiagnoses)
            immediateActions.clear()
            immediateActions.addAll(res.soapNote.plan.immediateActions)
            medications.clear()
            medications.addAll(res.soapNote.plan.medicationsAdvised)
            redFlags.clear()
            redFlags.addAll(res.soapNote.plan.redFlagWarnings)
            followUpTimeline = res.soapNote.plan.followUp
            fhirJsonPayload = res.abdmFhirMapping.fhirJsonPayload
        }
        formSpeechManager.clear()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TriageUrgencyBadge(tier = currentTier)
                            if (isAiGenerated) {
                                Surface(shape = RoundedCornerShape(4.dp), color = PrimaryContainerTeal) {
                                    Text("Gemini 2.5 AI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnPrimaryContainerTeal, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E293B)) {
                                    Text("Offline Rule Engine", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7BF5DF), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(
                            text = if (ddiResult.hasContraindications) "⚠ DDI Contraindications Detected" else "Encrypted SQLite Caching Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ddiResult.hasContraindications) TriageRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            val vitalsObj = ObjectiveData(
                                vitalsSummary = "BP $bpSystolic/$bpDiastolic mmHg, HR $heartRate bpm, SpO2 $spo2%, Temp $tempF°F, RR $respRate/min",
                                bpSystolic = bpSystolic.toIntOrNull() ?: 120,
                                bpDiastolic = bpDiastolic.toIntOrNull() ?: 80,
                                heartRate = heartRate.toIntOrNull() ?: 75,
                                spo2 = spo2.toIntOrNull() ?: 98,
                                temperatureF = tempF.toFloatOrNull() ?: 98.6f,
                                respiratoryRate = respRate.toIntOrNull() ?: 18,
                                physicalExamFindings = physicalExam
                            )

                            val soap = SoapNote(
                                subjective = SubjectiveData(
                                    chiefComplaint = chiefComplaint,
                                    historyPresentIllness = hpiNarrative,
                                    associatedSymptoms = associatedSymptoms.toList()
                                ),
                                objective = vitalsObj,
                                assessment = AssessmentData(
                                    differentialDiagnoses = differentials.toList()
                                ),
                                plan = PlanData(
                                    immediateActions = immediateActions.toList(),
                                    medicationsAdvised = ddiResult.updatedMedications,
                                    redFlagWarnings = redFlags.toList(),
                                    followUp = followUpTimeline
                                )
                            )

                            val abhaValidation = AbhaValidator.validate(abhaNumber)
                            if (abhaValidation is AbhaValidationResult.Invalid) {
                                abhaErrorMessage = abhaValidation.reason
                                showAbhaErrorDialog = true
                                return@Button
                            }
                            val validatedAbha = (abhaValidation as AbhaValidationResult.Valid).formattedNumber

                            val encounter = ClinicalEncounter(
                                id = "ENC-${System.currentTimeMillis().toString().takeLast(6)}",
                                patientId = if (patientId.isNotBlank()) patientId else "P-${(100..999).random()}",
                                patientName = patientName,
                                age = patientAge.toIntOrNull() ?: 30,
                                gender = patientGender,
                                abhaNumber = validatedAbha,
                                facilityId = facilityId,
                                operatorId = operatorId,
                                rawCodeSwitchingInput = rawInputText,
                                detectedLanguage = detectedLanguage,
                                triage = TriageInfo(
                                    tier = currentTier,
                                    rationale = triageRationale,
                                    referralRequired = currentTier == TriageTier.RED
                                ),
                                soapNote = soap,
                                abdmFhirMapping = AbdmFhirMapping(
                                    fhirJsonPayload = fhirJsonPayload
                                )
                            )
                            onSaveConsultation(encounter)
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_save_consultation"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (currentTier) {
                                TriageTier.RED -> TriageRed
                                TriageTier.YELLOW -> TriageYellow
                                TriageTier.GREEN -> TriageGreen
                            }
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save Consultation")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Consultation & Queue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Patient Demographics & ABHA Check-in Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("patient_header_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$patientName ($patientAge yrs, $patientGender)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val isAbhaValid = remember(abhaNumber) { AbhaValidator.isValid(abhaNumber) }
                                val maskedAbha = remember(abhaNumber) { AbhaValidator.mask(abhaNumber) }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ABHA: $maskedAbha",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isAbhaValid) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = TriageGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "ABDM Verified ✓",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TriageGreen,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Invalid Format",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Facility: $facilityId | Operator: $operatorId",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (historicalMeds.isNotEmpty()) {
                                    Text(
                                        text = "Room DB Active Meds: ${historicalMeds.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = PrimaryTeal,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                // 1-Tap ABHA QR Scanner Button
                                Button(
                                    onClick = { showAbhaScanner = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_open_abha_scanner")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Scan ABHA QR", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan ABHA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                val patientEncounters = remember(historicalEncounters, patientId) {
                                    historicalEncounters.filter { it.patientId == patientId || it.patientId.equals(patientId, ignoreCase = true) }
                                }

                                // Secondary Screen/Bottom-Sheet: Triage History & Clinical Trends Button
                                OutlinedButton(
                                    onClick = { showTriageHistorySheet = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_open_triage_history"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Triage History", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "History & Trends (${patientEncounters.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Triage Assessment Card
                        TriageAssessmentCard(
                            tier = currentTier,
                            rationale = triageRationale,
                            onCallAmbulance = {}
                        )

                        // Urgency Override Selector (48dp buttons)
                        TriageUrgencySelector(
                            selectedTier = currentTier,
                            onTierSelected = { newTier ->
                                currentTier = newTier
                                triageRationale = "Manually verified by clinical operator as ${newTier.title}"
                            }
                        )
                    }
                }
            }

            // Tab Navigation for S, O, A, P, FHIR
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedSection,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth().testTag("soap_section_tabs")
                ) {
                    sectionTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSection == index,
                            onClick = { selectedSection = index },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("tab_soap_$index"),
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }

            // S: Subjective
            if (selectedSection == 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1. Regional Voice / Text Intake",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Live Waveform Dictate Button (Triggers AudioWaveformOverlay)
                            FilledTonalButton(
                                onClick = {
                                    if (isFormListening) {
                                        finishFormDictation()
                                    } else {
                                        if (!hasMicPermission) {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            formSpeechManager.startListening()
                                        }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_quick_waveform_dictate"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isFormListening) TriageRed else PrimaryContainerTeal,
                                    contentColor = if (isFormListening) Color.White else OnPrimaryContainerTeal
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = if (isFormListening) "Stop Dictation" else "Live Dictate",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFormListening) "Stop Mic" else "Live Mic",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Push-to-Talk Full Modal Button (48dp)
                            Button(
                                onClick = { showVoiceModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_open_voice_intake")
                            ) {
                                Text("Modal Sheet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        label = { Text("Raw Transcript / Code-Switching Narrative") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("input_raw_voice_text")
                    )
                }

                item {
                    OutlinedTextField(
                        value = chiefComplaint,
                        onValueChange = { chiefComplaint = it },
                        label = { Text("Standardized Chief Complaint (Formal Clinical English)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_chief_complaint")
                    )
                }

                item {
                    OutlinedTextField(
                        value = hpiNarrative,
                        onValueChange = { hpiNarrative = it },
                        label = { Text("History of Present Illness (HPI)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp).testTag("input_hpi_narrative")
                    )
                }

                item {
                    Text("Associated Symptoms:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        associatedSymptoms.forEach { symptom ->
                            AssistChip(
                                onClick = {},
                                label = { Text(symptom, fontSize = 11.sp) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }

            // O: Objective
            if (selectedSection == 1) {
                item {
                    Text(
                        text = "2. Objective Vitals & Physical Exam",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bpSystolic,
                            onValueChange = { bpSystolic = it },
                            label = { Text("BP Systolic") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_bp_sys")
                        )
                        OutlinedTextField(
                            value = bpDiastolic,
                            onValueChange = { bpDiastolic = it },
                            label = { Text("BP Diastolic") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_bp_dia")
                        )
                        OutlinedTextField(
                            value = heartRate,
                            onValueChange = { heartRate = it },
                            label = { Text("Pulse") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_pulse")
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spo2,
                            onValueChange = { spo2 = it },
                            label = { Text("SpO2 %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_spo2")
                        )
                        OutlinedTextField(
                            value = tempF,
                            onValueChange = { tempF = it },
                            label = { Text("Temp °F") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_temp")
                        )
                        OutlinedTextField(
                            value = respRate,
                            onValueChange = { respRate = it },
                            label = { Text("Resp Rate") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_resp_rate")
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = physicalExam,
                        onValueChange = { physicalExam = it },
                        label = { Text("Physical Examination Findings") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp).testTag("input_physical_exam")
                    )
                }
            }

            // A: Assessment
            if (selectedSection == 2) {
                item {
                    Text(
                        text = "3. Ranked Differential Diagnoses (ICD-10 Mapped)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(differentials) { diff ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("differential_item_${diff.icd10Code}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(diff.condition, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("ICD-10: ${diff.icd10Code} | Confidence: ${(diff.confidence * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (diff.urgency.uppercase()) {
                                    "HIGH" -> TriageRedContainer
                                    "MEDIUM" -> TriageYellowContainer
                                    else -> TriageGreenContainer
                                }
                            ) {
                                Text(
                                    text = diff.urgency.uppercase(),
                                    color = when (diff.urgency.uppercase()) {
                                        "HIGH" -> OnTriageRedContainer
                                        "MEDIUM" -> OnTriageYellowContainer
                                        else -> OnTriageGreenContainer
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // P: Plan (With Real-Time DDI Warning Banner & Substitute Recommendations!)
            if (selectedSection == 3) {
                item {
                    Text(
                        text = "4. Plan: Real-Time Drug Interaction Checker & Prescriptions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // HIGH-CONTRAST DDI WARNING BANNER WITH SUBSTITUTE RECOMMENDATIONS
                if (ddiResult.hasContraindications) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("ddi_warning_banner"),
                            colors = CardDefaults.cardColors(containerColor = TriageRedDark),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, TriageRed)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = "DDI Alert", tint = Color.White, modifier = Modifier.size(24.dp))
                                    Text(
                                        text = "POTENTIAL DRUG-DRUG CONTRAINDICATIONS DETECTED",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }

                                ddiResult.alerts.forEach { alert ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${alert.drug1} + ${alert.drug2}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = TriageRedDark
                                                )
                                                Surface(shape = RoundedCornerShape(4.dp), color = TriageRedContainer) {
                                                    Text(alert.severity, color = OnTriageRedContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }

                                            Text(
                                                text = "Source: ${alert.source}\nRisk: ${alert.clinicalRisk}",
                                                fontSize = 11.sp,
                                                color = Color.Black,
                                                lineHeight = 15.sp
                                            )

                                            // Recommended Substitute Action
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PrimaryContainerTeal.copy(alpha = 0.5f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("RECOMMENDED SUBSTITUTE:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = OnPrimaryContainerTeal)
                                                        Text(alert.recommendedSubstitute, fontSize = 11.sp, color = OnPrimaryContainerTeal)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            // Apply substitute into medication list
                                                            val idx = medications.indexOfFirst { it.name.contains("Omeprazole", ignoreCase = true) || it.name.contains("Aspirin", ignoreCase = true) }
                                                            if (idx >= 0) {
                                                                val old = medications[idx]
                                                                val subName = if (old.name.contains("Omeprazole", ignoreCase = true)) "Pantoprazole" else "Paracetamol"
                                                                medications[idx] = old.copy(name = subName, safetyFlag = "Clear", warningDetail = "Substituted to avoid DDI")
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Apply Substitute", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Prescribed Medications & Safety Flags:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ddiResult.updatedMedications.forEach { med ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(med.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${med.dosage} • ${med.frequency}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (med.warningDetail.isNotBlank()) {
                                            Text("⚠ ${med.warningDetail}", fontSize = 10.sp, color = TriageRed, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (med.safetyFlag) {
                                            "Clear" -> TriageGreenContainer
                                            "Contraindicated" -> TriageRedContainer
                                            else -> TriageYellowContainer
                                        }
                                    ) {
                                        Text(
                                            text = med.safetyFlag,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (med.safetyFlag) {
                                                "Clear" -> OnTriageGreenContainer
                                                "Contraindicated" -> OnTriageRedContainer
                                                else -> OnTriageYellowContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = followUpTimeline,
                        onValueChange = { followUpTimeline = it },
                        label = { Text("Follow-up / Referral Destination") },
                        modifier = Modifier.fillMaxWidth().testTag("input_followup")
                    )
                }
            }

            // FHIR R4
            if (selectedSection == 4) {
                item {
                    Text(
                        text = "5. HL7 FHIR R4 & ABDM ClinicalImpression Bundle",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BackgroundDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (fhirJsonPayload.isNotEmpty()) fhirJsonPayload else "Generating FHIR R4 Bundle...",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF81C784),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Visual Audio Amplitude Waveform Overlay (Floats over form when mic is active)
        AudioWaveformOverlay(
            isVisible = isFormListening,
            rmsDb = formRmsDb,
            transcript = formTranscript,
            partialTranscript = formPartialTranscript,
            onStopListening = { finishFormDictation() },
            onCancel = {
                formSpeechManager.stopListening()
                formSpeechManager.clear()
            },
            languageLabel = "తెలుగు / Hindi / Eng",
            emergencyKeywords = formDetectedEmergencyKeywords,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = innerPadding.calculateBottomPadding() + 8.dp)
        )
    }
}

    // Modal Sheet: ABHA QR Scanner
    if (showAbhaScanner) {
        AbhaQrScannerModal(
            onPatientScanned = { scanned ->
                patientName = scanned.name
                patientAge = scanned.age.toString()
                patientGender = scanned.gender
                abhaNumber = scanned.abhaNumber
                isPregnant = (scanned.age in 18..45 && scanned.gender.startsWith("F", ignoreCase = true))
                if (scanned.activeMedications.isNotBlank()) {
                    historicalMeds = scanned.activeMedications.split(",").map { it.trim() }
                }
                showAbhaScanner = false
            },
            onDismiss = { showAbhaScanner = false }
        )
    }

    // Modal Sheet: Real-time Voice Recording & Gemini SOAP Parser
    if (showVoiceModal) {
        val vitalsObj = ObjectiveData(
            vitalsSummary = "BP $bpSystolic/$bpDiastolic mmHg, HR $heartRate bpm, SpO2 $spo2%, Temp $tempF°F, RR $respRate/min",
            bpSystolic = bpSystolic.toIntOrNull() ?: 120,
            bpDiastolic = bpDiastolic.toIntOrNull() ?: 80,
            heartRate = heartRate.toIntOrNull() ?: 75,
            spo2 = spo2.toIntOrNull() ?: 98,
            temperatureF = tempF.toFloatOrNull() ?: 98.6f,
            respiratoryRate = respRate.toIntOrNull() ?: 18,
            physicalExamFindings = physicalExam
        )

        VoiceRecordingModal(
            patientAge = patientAge.toIntOrNull() ?: 30,
            patientGender = patientGender,
            isPregnant = isPregnant,
            currentVitals = vitalsObj,
            onParsedSoapResult = { result, sourceText ->
                applyParsedGeminiResult(result, sourceText)
                showVoiceModal = false
            },
            onDismiss = { showVoiceModal = false }
        )
    }

    // Dialog: ABHA Validation Error Enforcement
    if (showAbhaErrorDialog) {
        AlertDialog(
            onDismissRequest = { showAbhaErrorDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "ABHA Format Alert",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "ABHA Format Enforcement",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "National Health Authority (NHA) & Ayushman Bharat Digital Mission (ABDM) standards strictly require a 14-digit format: 'XX-XXXX-XXXX-XXXX'.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = "Validation Error: $abhaErrorMessage\nCurrent Value: '$abhaNumber'",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Please scan or manually verify the patient's official ABHA card before submitting to the NHCX claims and ABDM FHIR gateway.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAbhaErrorDialog = false
                        showAbhaScanner = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_fix_abha_scanner")
                ) {
                    Text("Scan / Verify ABHA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAbhaErrorDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Bottom-Sheet: Patient Triage History & Clinical Trends
    if (showTriageHistorySheet) {
        val patientEncounters = remember(historicalEncounters, patientId) {
            historicalEncounters.filter { it.patientId == patientId || it.patientId.equals(patientId, ignoreCase = true) }
        }
        PatientTriageHistoryBottomSheet(
            patientId = patientId,
            patientName = patientName,
            patientAge = patientAge.toIntOrNull() ?: 30,
            patientGender = patientGender,
            abhaNumber = abhaNumber,
            encounters = patientEncounters,
            onDismiss = { showTriageHistorySheet = false }
        )
    }
}
