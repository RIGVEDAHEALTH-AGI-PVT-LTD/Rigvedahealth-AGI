package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TriageTier
import com.example.db.EncounterEntity
import com.example.security.AbhaValidator
import com.example.ui.theme.OnPrimaryContainerTeal
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageRed
import com.example.ui.theme.TriageYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trajectory analysis calculation for longitudinal encounters.
 */
data class ClinicalTrajectory(
    val status: String,
    val description: String,
    val isDeteriorating: Boolean,
    val isImproving: Boolean,
    val initialBp: String,
    val latestBp: String,
    val bpSystolicDelta: Int,
    val initialSpo2: Int,
    val latestSpo2: Int,
    val spo2Delta: Int,
    val initialHr: Int,
    val latestHr: Int,
    val hrDelta: Int,
    val tierProgression: List<TriageTier>
)

object ClinicalTrendAnalyzer {
    fun analyze(encounters: List<EncounterEntity>): ClinicalTrajectory {
        if (encounters.isEmpty()) {
            return ClinicalTrajectory(
                status = "Baseline Visit",
                description = "First encounter recorded today. No previous trend data.",
                isDeteriorating = false,
                isImproving = false,
                initialBp = "--/--",
                latestBp = "--/--",
                bpSystolicDelta = 0,
                initialSpo2 = 98,
                latestSpo2 = 98,
                spo2Delta = 0,
                initialHr = 75,
                latestHr = 75,
                hrDelta = 0,
                tierProgression = emptyList()
            )
        }

        // Sort chronologically (oldest first) to evaluate progression over time
        val chrono = encounters.sortedBy { it.timestamp }
        val initial = chrono.first()
        val latest = chrono.last()

        val tiers = chrono.map { enc ->
            when (enc.triageTier.uppercase(Locale.ROOT)) {
                "RED" -> TriageTier.RED
                "YELLOW" -> TriageTier.YELLOW
                else -> TriageTier.GREEN
            }
        }

        val initialBpSys = initial.bpSystolic
        val latestBpSys = latest.bpSystolic
        val bpDelta = latestBpSys - initialBpSys

        val initialSpo2 = initial.spo2
        val latestSpo2 = latest.spo2
        val spo2Delta = latestSpo2 - initialSpo2

        val initialHr = initial.heartRate
        val latestHr = latest.heartRate
        val hrDelta = latestHr - initialHr

        val firstTier = tiers.first()
        val lastTier = tiers.last()

        val isDeteriorating = (lastTier.level < firstTier.level) || (latestSpo2 <= 93 && initialSpo2 > 95) || (bpDelta >= 25)
        val isImproving = (lastTier.level > firstTier.level) && (latestSpo2 >= 95) && (bpDelta <= 0)

        val status = when {
            isDeteriorating -> "Critical Deterioration ⚠"
            isImproving -> "Stabilizing / Improving ✓"
            firstTier == TriageTier.RED -> "Persistently Emergent ⚠"
            firstTier == TriageTier.YELLOW -> "Persistent Urgent Status"
            else -> "Stable Routine Follow-up"
        }

        val description = when {
            isDeteriorating -> "Clinical escalation observed across visits: SBP delta of ${if (bpDelta >= 0) "+$bpDelta" else "$bpDelta"} mmHg and SpO2 delta of ${if (spo2Delta >= 0) "+$spo2Delta%" else "$spo2Delta%"}."
            isImproving -> "Hemodynamics stabilizing. Symptoms responding favorably compared to initial presentation."
            else -> "Vitals within consistent chronic management parameters."
        }

        return ClinicalTrajectory(
            status = status,
            description = description,
            isDeteriorating = isDeteriorating,
            isImproving = isImproving,
            initialBp = "${initial.bpSystolic}/${initial.bpDiastolic}",
            latestBp = "${latest.bpSystolic}/${latest.bpDiastolic}",
            bpSystolicDelta = bpDelta,
            initialSpo2 = initialSpo2,
            latestSpo2 = latestSpo2,
            spo2Delta = spo2Delta,
            initialHr = initialHr,
            latestHr = latestHr,
            hrDelta = hrDelta,
            tierProgression = tiers
        )
    }
}

/**
 * Patient Triage History Bottom-Sheet
 *
 * Appears during clinical consultations (or from queue inspection) to allow ANMs and MOs
 * to analyze longitudinal triage history, vitals progression, and clinical deterioration trends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientTriageHistoryBottomSheet(
    patientId: String,
    patientName: String,
    patientAge: Int,
    patientGender: String,
    abhaNumber: String,
    encounters: List<EncounterEntity>,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("patient_triage_history_sheet")
    ) {
        PatientTriageHistoryContent(
            patientId = patientId,
            patientName = patientName,
            patientAge = patientAge,
            patientGender = patientGender,
            abhaNumber = abhaNumber,
            encounters = encounters,
            onClose = onDismiss
        )
    }
}

/**
 * Reusable Content for Patient Triage History and Clinical Trends.
 */
@Composable
fun PatientTriageHistoryContent(
    patientId: String,
    patientName: String,
    patientAge: Int,
    patientGender: String,
    abhaNumber: String,
    encounters: List<EncounterEntity>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isNewestFirst by remember { mutableStateOf(true) }
    var selectedTierFilter by remember { mutableStateOf("ALL") }
    val expandedEncounterIds = remember { mutableStateMapOf<String, Boolean>() }

    // Patient's encounters sorted by timestamp
    val sortedEncounters by remember(encounters, isNewestFirst, selectedTierFilter) {
        derivedStateOf {
            val filtered = when (selectedTierFilter) {
                "RED" -> encounters.filter { it.triageTier.equals("RED", ignoreCase = true) }
                "YELLOW" -> encounters.filter { it.triageTier.equals("YELLOW", ignoreCase = true) }
                "GREEN" -> encounters.filter { it.triageTier.equals("GREEN", ignoreCase = true) }
                else -> encounters
            }
            if (isNewestFirst) {
                filtered.sortedByDescending { it.timestamp }
            } else {
                filtered.sortedBy { it.timestamp }
            }
        }
    }

    val trajectory = remember(encounters) { ClinicalTrendAnalyzer.analyze(encounters) }

    val redCount = remember(encounters) { encounters.count { it.triageTier.equals("RED", ignoreCase = true) } }
    val yellowCount = remember(encounters) { encounters.count { it.triageTier.equals("YELLOW", ignoreCase = true) } }
    val greenCount = remember(encounters) { encounters.count { it.triageTier.equals("GREEN", ignoreCase = true) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top Header Row: Patient Demographic summary and Close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = patientName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$patientAge y / $patientGender",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "ABHA: ${AbhaValidator.mask(abhaNumber)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (AbhaValidator.isValid(abhaNumber)) {
                        Text(
                            text = "✓ ABDM Verified",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TriageGreen
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("triage_history_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Triage History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("triage_history_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            // Section 1: Clinical Trend Trajectory Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vitals_trend_summary_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            trajectory.isDeteriorating -> TriageRed.copy(alpha = 0.12f)
                            trajectory.isImproving -> TriageGreen.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = when {
                            trajectory.isDeteriorating -> TriageRed.copy(alpha = 0.5f)
                            trajectory.isImproving -> TriageGreen.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                if (trajectory.isDeteriorating) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = TriageRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Trends",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Longitudinal Clinical Trend",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    trajectory.isDeteriorating -> TriageRed
                                    trajectory.isImproving -> TriageGreen
                                    else -> PrimaryTeal
                                }
                            ) {
                                Text(
                                    text = trajectory.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = trajectory.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Vitals Metric Trajectory Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Blood Pressure Card
                            VitalsTrendMetricPill(
                                label = "Blood Pressure",
                                baselineVal = trajectory.initialBp,
                                latestVal = trajectory.latestBp,
                                deltaText = if (trajectory.bpSystolicDelta >= 0) "+${trajectory.bpSystolicDelta}" else "${trajectory.bpSystolicDelta}",
                                isWorse = trajectory.bpSystolicDelta >= 20,
                                modifier = Modifier.weight(1f)
                            )

                            // SpO2 Card
                            VitalsTrendMetricPill(
                                label = "SpO2 Saturation",
                                baselineVal = "${trajectory.initialSpo2}%",
                                latestVal = "${trajectory.latestSpo2}%",
                                deltaText = if (trajectory.spo2Delta >= 0) "+${trajectory.spo2Delta}%" else "${trajectory.spo2Delta}%",
                                isWorse = trajectory.spo2Delta <= -3 || trajectory.latestSpo2 < 94,
                                modifier = Modifier.weight(1f)
                            )

                            // Heart Rate Card
                            VitalsTrendMetricPill(
                                label = "Heart Rate",
                                baselineVal = "${trajectory.initialHr} bpm",
                                latestVal = "${trajectory.latestHr} bpm",
                                deltaText = if (trajectory.hrDelta >= 0) "+${trajectory.hrDelta}" else "${trajectory.hrDelta}",
                                isWorse = trajectory.hrDelta >= 25 || trajectory.latestHr > 105,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Sparkline Multi-Visit Vitals Curve
                        if (encounters.size >= 2) {
                            LongitudinalVitalsGraph(
                                encounters = encounters.sortedBy { it.timestamp },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Section 2: Sorting and Tier Filtering Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Encounters (${sortedEncounters.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Sort Order Toggle Button
                    OutlinedButton(
                        onClick = { isNewestFirst = !isNewestFirst },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sort_order_toggle"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isNewestFirst) "Newest First ▾" else "Oldest First ▴",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Filter Chips Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedTierFilter == "ALL",
                            onClick = { selectedTierFilter = "ALL" },
                            label = { Text("All (${encounters.size})") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_tier_chip_ALL")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTierFilter == "RED",
                            onClick = { selectedTierFilter = "RED" },
                            label = { Text("Red Emergent ($redCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TriageRed.copy(alpha = 0.2f),
                                selectedLabelColor = TriageRed
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_tier_chip_RED")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTierFilter == "YELLOW",
                            onClick = { selectedTierFilter = "YELLOW" },
                            label = { Text("Yellow Urgent ($yellowCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TriageYellow.copy(alpha = 0.2f),
                                selectedLabelColor = TriageYellow
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_tier_chip_YELLOW")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTierFilter == "GREEN",
                            onClick = { selectedTierFilter = "GREEN" },
                            label = { Text("Green Routine ($greenCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TriageGreen.copy(alpha = 0.2f),
                                selectedLabelColor = TriageGreen
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_tier_chip_GREEN")
                        )
                    }
                }
            }

            // Section 3: Encounter Timeline Cards
            if (sortedEncounters.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No encounters match the selected filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(sortedEncounters, key = { it.encounterId }) { encounter ->
                    val isExpanded = expandedEncounterIds[encounter.encounterId] ?: false
                    EncounterTimelineCard(
                        encounter = encounter,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedEncounterIds[encounter.encounterId] = !isExpanded
                        }
                    )
                }
            }
        }
    }
}

/**
 * Metric pill displaying initial baseline vs latest reading with clinical delta badge.
 */
@Composable
private fun VitalsTrendMetricPill(
    label: String,
    baselineVal: String,
    latestVal: String,
    deltaText: String,
    isWorse: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isWorse) TriageRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = latestVal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWorse) TriageRed else MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isWorse) TriageRed.copy(alpha = 0.15f) else TriageGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = deltaText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isWorse) TriageRed else TriageGreen,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = "Prior: $baselineVal",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Custom Canvas drawing a longitudinal vitals trend line connecting previous visits.
 */
@Composable
private fun LongitudinalVitalsGraph(
    encounters: List<EncounterEntity>,
    modifier: Modifier = Modifier
) {
    if (encounters.size < 2) return

    val sysVals = remember(encounters) { encounters.map { it.bpSystolic.toFloat() } }
    val spo2Vals = remember(encounters) { encounters.map { it.spo2.toFloat() } }

    val minSys = (sysVals.minOrNull() ?: 100f) - 10f
    val maxSys = (sysVals.maxOrNull() ?: 180f) + 10f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(PrimaryTeal))
                        Text("Systolic BP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                        Text("SpO2 %", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Longitudinal Timeline (${encounters.size} pts)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                val width = size.width
                val height = size.height
                val pointCount = encounters.size
                val stepX = if (pointCount > 1) width / (pointCount - 1) else width

                // Draw Systolic BP line (Teal)
                val bpPath = Path()
                val bpPoints = mutableListOf<Offset>()

                for (i in 0 until pointCount) {
                    val x = i * stepX
                    val normalizedSys = (sysVals[i] - minSys) / (maxSys - minSys).coerceAtLeast(1f)
                    val y = height - (normalizedSys * height).coerceIn(4f, height - 4f)
                    bpPoints.add(Offset(x, y))
                    if (i == 0) bpPath.moveTo(x, y) else bpPath.lineTo(x, y)
                }

                drawPath(
                    path = bpPath,
                    color = PrimaryTeal,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw nodes color-coded by triage tier
                bpPoints.forEachIndexed { idx, pt ->
                    val enc = encounters[idx]
                    val tierColor = when (enc.triageTier.uppercase(Locale.ROOT)) {
                        "RED" -> TriageRed
                        "YELLOW" -> TriageYellow
                        else -> TriageGreen
                    }
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = pt)
                    drawCircle(color = tierColor, radius = 3.5.dp.toPx(), center = pt)
                }
            }
        }
    }
}

/**
 * Individual Chronological Encounter Timeline Item Card.
 */
@Composable
private fun EncounterTimelineCard(
    encounter: EncounterEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tierColor = when (encounter.triageTier.uppercase(Locale.ROOT)) {
        "RED" -> TriageRed
        "YELLOW" -> TriageYellow
        else -> TriageGreen
    }

    val tier = when (encounter.triageTier.uppercase(Locale.ROOT)) {
        "RED" -> TriageTier.RED
        "YELLOW" -> TriageTier.YELLOW
        else -> TriageTier.GREEN
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(encounter.timestamp) { dateFormat.format(Date(encounter.timestamp)) }
    val relativeTime = remember(encounter.timestamp) { getRelativeTimeSpan(encounter.timestamp) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("encounter_item_${encounter.encounterId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (encounter.referralRequired) TriageRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Urgency color stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(tierColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Date, Relative time & Triage Urgency badge
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
                            text = formattedDate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = relativeTime,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    TriageUrgencyBadge(
                        tier = tier,
                        isReferralRequired = encounter.referralRequired
                    )
                }

                // Chief Complaint
                Text(
                    text = encounter.chiefComplaint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Vitals Row Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VitalsPill(text = "BP: ${encounter.bloodPressure}")
                    VitalsPill(text = "HR: ${encounter.heartRate} bpm")
                    VitalsPill(
                        text = "SpO2: ${encounter.spo2}%",
                        isAlert = encounter.spo2 < 94
                    )
                    if (encounter.temperatureF != null) {
                        VitalsPill(
                            text = "${encounter.temperatureF}°F",
                            isAlert = encounter.temperatureF > 100.4f
                        )
                    }
                }

                // Prescribed medications summary
                if (encounter.prescribedMedications.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Prescribed:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = encounter.prescribedMedications,
                            fontSize = 11.sp,
                            color = PrimaryTeal,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expandable Details (Rationale, Differential Diagnoses, Immediate Actions)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

                        if (encounter.triageRationale.isNotBlank()) {
                            Text(
                                text = "Triage Rationale: ${encounter.triageRationale}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (encounter.referralRequired) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TriageRed.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = TriageRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "108 Tertiary Hospital Referral Dispatched",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TriageRed
                                    )
                                }
                            }
                        }

                        if (encounter.rawNarrative.isNotBlank()) {
                            Text(
                                text = "Regional Voice Narrative: \"${encounter.rawNarrative}\"",
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Facility: ${encounter.facilityId}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Operator: ${encounter.operatorId}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expand / Collapse Action Button (touch target >= 48.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(vertical = 4.dp)
                        .testTag("btn_expand_encounter_${encounter.encounterId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isExpanded) "Show Less" else "View Clinical Notes & Rationale",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTeal
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VitalsPill(
    text: String,
    isAlert: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isAlert) TriageRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAlert) TriageRed else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Medium,
            color = if (isAlert) TriageRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun getRelativeTimeSpan(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 30 -> "${days / 30}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}
