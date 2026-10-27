package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TriageTier
import com.example.db.EncounterEntity
import com.example.security.AbhaValidator
import com.example.ui.theme.*

data class PatientQueueItem(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val abha: String,
    val chiefComplaint: String,
    val vitalsSummary: String,
    val tier: TriageTier,
    val isEmergent: Boolean,
    val activeMedications: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalQueueView(
    onSelectPatient: (PatientQueueItem) -> Unit,
    onAddNewPatient: () -> Unit,
    onScanAbha: () -> Unit,
    encounters: List<EncounterEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTierFilter by remember { mutableStateOf("ALL") }
    var inspectingHistoryPatient by remember { mutableStateOf<PatientQueueItem?>(null) }

    val defaultQueue = remember {
        mutableStateListOf(
            PatientQueueItem(
                id = "P-101",
                name = "Lakshmi Devi",
                age = 26,
                gender = "Female",
                abha = "12-3456-7890-1234",
                chiefComplaint = "Severe chest pain radiating to left shoulder, breathlessness (Telugu: Gunde noppi)",
                vitalsSummary = "BP 168/102, HR 112, SpO2 93%",
                tier = TriageTier.RED,
                isEmergent = true,
                activeMedications = "Warfarin 2mg"
            ),
            PatientQueueItem(
                id = "P-102",
                name = "Ramesh Kumar",
                age = 58,
                gender = "Male",
                abha = "34-9876-5432-1098",
                chiefComplaint = "High pyrexia 102.5°F for 4 days, vomiting, rigors (Hindi: Tez bukhar)",
                vitalsSummary = "BP 138/88, HR 94, SpO2 96%, Temp 102.5°F",
                tier = TriageTier.YELLOW,
                isEmergent = false,
                activeMedications = "Enalapril 5mg, Metformin 500mg"
            ),
            PatientQueueItem(
                id = "P-103",
                name = "Savitri Bai",
                age = 45,
                gender = "Female",
                abha = "77-2233-4455-6677",
                chiefComplaint = "Routine Antenatal Care (ANC) second trimester checkup",
                vitalsSummary = "BP 118/76, HR 72, SpO2 99%",
                tier = TriageTier.GREEN,
                isEmergent = false,
                activeMedications = "Omeprazole 20mg"
            ),
            PatientQueueItem(
                id = "P-104",
                name = "Anjaneyulu M.",
                age = 62,
                gender = "Male",
                abha = "88-9900-1122-3344",
                chiefComplaint = "Acute severe dyspnea and wheezing with cyanosis",
                vitalsSummary = "BP 155/95, HR 124, SpO2 90%, RR 28",
                tier = TriageTier.RED,
                isEmergent = true,
                activeMedications = "Clopidogrel 75mg, Atorvastatin 20mg"
            )
        )
    }

    val sortedQueue = remember(defaultQueue, selectedTierFilter) {
        val filtered = when (selectedTierFilter) {
            "RED" -> defaultQueue.filter { it.tier == TriageTier.RED }
            "YELLOW" -> defaultQueue.filter { it.tier == TriageTier.YELLOW }
            "GREEN" -> defaultQueue.filter { it.tier == TriageTier.GREEN }
            else -> defaultQueue
        }
        filtered.sortedBy { it.tier.level }
    }

    val redCount = defaultQueue.count { it.tier == TriageTier.RED }
    val yellowCount = defaultQueue.count { it.tier == TriageTier.YELLOW }
    val greenCount = defaultQueue.count { it.tier == TriageTier.GREEN }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Clinical Triage Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Prioritized by Urgency Tier (Red > Yellow > Green)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onScanAbha,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_queue_scan_abha")
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan ABHA", fontSize = 12.sp)
                }

                Button(
                    onClick = onAddNewPatient,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_admit_patient"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Patient")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 12.sp)
                }
            }
        }

        // Triage Filter Chips (Red/Yellow/Green)
        TriageFilterBar(
            selectedTier = selectedTierFilter,
            redCount = redCount,
            yellowCount = yellowCount,
            greenCount = greenCount,
            totalCount = defaultQueue.size,
            onSelectTier = { selectedTierFilter = it }
        )

        // Queue List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sortedQueue) { patient ->
                val primaryColor = TriageVisualTokens.getPrimaryColor(patient.tier)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("patient_queue_card_${patient.id}")
                        .clickable { onSelectPatient(patient) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (patient.tier == TriageTier.RED)
                            TriageRedContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (patient.tier == TriageTier.RED) 2.dp else 1.dp,
                        color = if (patient.tier == TriageTier.RED) TriageRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(primaryColor)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = patient.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "(${patient.age}y / ${patient.gender})",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TriageUrgencyBadge(
                                    tier = patient.tier,
                                    isReferralRequired = patient.isEmergent
                                )
                            }

                            Text(
                                text = patient.chiefComplaint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Vitals: ${patient.vitalsSummary}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "ABHA: ${AbhaValidator.mask(patient.abha)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (AbhaValidator.isValid(patient.abha)) {
                                        Text(
                                            text = "✓",
                                            fontSize = 11.sp,
                                            color = TriageGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { inspectingHistoryPatient = patient },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_patient_history_${patient.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "View Triage History",
                                tint = primaryColor
                            )
                        }

                        IconButton(
                            onClick = { onSelectPatient(patient) },
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 8.dp)
                                .testTag("btn_open_soap_${patient.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open SOAP form",
                                tint = primaryColor
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom-Sheet: Patient Triage History & Clinical Trends from Queue
    if (inspectingHistoryPatient != null) {
        val patient = inspectingHistoryPatient!!
        val patientEncounters = remember(encounters, patient.id) {
            encounters.filter { it.patientId == patient.id || it.patientId.equals(patient.id, ignoreCase = true) }
        }
        PatientTriageHistoryBottomSheet(
            patientId = patient.id,
            patientName = patient.name,
            patientAge = patient.age,
            patientGender = patient.gender,
            abhaNumber = patient.abha,
            encounters = patientEncounters,
            onDismiss = { inspectingHistoryPatient = null }
        )
    }
}
