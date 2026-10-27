package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.SyncQueueEntity
import com.example.ui.theme.OnPrimaryContainerTeal
import com.example.ui.theme.OnTriageGreenContainer
import com.example.ui.theme.OnTriageRedContainer
import com.example.ui.theme.OnTriageYellowContainer
import com.example.ui.theme.PrimaryContainerTeal
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageGreenContainer
import com.example.ui.theme.TriageRed
import com.example.ui.theme.TriageRedContainer
import com.example.ui.theme.TriageYellow
import com.example.ui.theme.TriageYellowContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local Sync Dashboard View
 *
 * Displays:
 * 1. Current count and priority breakdown of pending clinical records in the SQLite sync queue.
 * 2. Granular list of failed sync attempts with detailed error diagnostic rationale.
 * 3. Functional 'Retry' and 'Delete' actions for each pending/failed record.
 * 4. Visibility into records that will be automatically flushed once network connectivity is restored.
 */
@Composable
fun SyncMonitorView(
    isOnline: Boolean,
    isSimulationOffline: Boolean,
    onToggleOfflineSimulation: (Boolean) -> Unit,
    syncEntries: List<SyncQueueEntity>,
    onTriggerSync: () -> Unit,
    onRetryItem: (Long) -> Unit = {},
    onDeleteItem: (Long) -> Unit = {},
    onRetryAllFailed: () -> Unit = {},
    onClearCompleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "FAILED", "SYNCED"
    var itemToDelete by remember { mutableStateOf<SyncQueueEntity?>(null) }

    val pendingEntries = remember(syncEntries) { syncEntries.filter { it.status == "PENDING" || it.status == "QUEUED" } }
    val failedEntries = remember(syncEntries) { syncEntries.filter { it.status == "FAILED" } }
    val syncedEntries = remember(syncEntries) { syncEntries.filter { it.status == "SYNCED" } }

    val pendingCount = pendingEntries.size
    val failedCount = failedEntries.size
    val syncedCount = syncedEntries.size

    val redPendingCount = remember(pendingEntries) { pendingEntries.count { it.priority == 1 } }
    val yellowPendingCount = remember(pendingEntries) { pendingEntries.count { it.priority == 2 } }
    val greenPendingCount = remember(pendingEntries) { pendingEntries.count { it.priority == 3 } }

    val filteredList by remember(syncEntries, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                "PENDING" -> pendingEntries
                "FAILED" -> failedEntries
                "SYNCED" -> syncedEntries
                else -> syncEntries
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Connectivity Status Banner with Local SQLite Visibility
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connectivity_status_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) TriageGreenContainer else TriageYellowContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) TriageGreen else TriageYellow)
                        )
                        Column {
                            Text(
                                text = if (isOnline) "Network Connected (Online)" else "Offline Native Mode (Encrypted SQLite)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isOnline) OnTriageGreenContainer else OnTriageYellowContainer
                            )
                            Text(
                                text = if (isOnline) "WorkManager background auto-flush active" else "$pendingCount pending records cached locally in SQLite",
                                fontSize = 12.sp,
                                color = if (isOnline) OnTriageGreenContainer.copy(alpha = 0.8f) else OnTriageYellowContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Simulate Offline", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isSimulationOffline,
                            onCheckedChange = onToggleOfflineSimulation,
                            modifier = Modifier.testTag("switch_offline_simulation")
                        )
                    }
                }

                if (!isOnline && pendingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Visibility Advisory: $pendingCount records safely held in local SQLite queue. They will upload automatically once network connectivity is restored.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnTriageYellowContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 2. Local Dashboard Metrics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pending Records Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_pending_count_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Pending in SQLite",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$pendingCount records",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingCount > 0) TriageYellow else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Red: $redPendingCount | Ylw: $yellowPendingCount | Grn: $greenPendingCount",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Failed Attempts Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_failed_count_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (failedCount > 0) TriageRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (failedCount > 0) TriageRed.copy(alpha = 0.5f) else Color.Transparent
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Failed Attempts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (failedCount > 0) TriageRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$failedCount failed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (failedCount > 0) TriageRed else MaterialTheme.colorScheme.onSurface
                    )
                    if (failedCount > 0) {
                        Text(
                            text = "Tap to retry or delete",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TriageRed
                        )
                    } else {
                        Text(
                            text = "Zero sync errors",
                            fontSize = 10.sp,
                            color = TriageGreen
                        )
                    }
                }
            }

            // Synced to Gateway Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_synced_count_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Synced Gateways",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$syncedCount items",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTeal
                    )
                    Text(
                        text = "NHCX & FHIR live",
                        fontSize = 10.sp,
                        color = PrimaryTeal
                    )
                }
            }
        }

        // 3. Batch Action Bar: Flush Sync / Retry All Failed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTriggerSync,
                enabled = isOnline,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_trigger_sync_now"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync Now", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) "Flush SQLite to NHCX ($pendingCount)" else "Offline (Waiting for Net)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            if (failedCount > 0) {
                OutlinedButton(
                    onClick = onRetryAllFailed,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_retry_all_failed"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TriageRed)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry All", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry All Failed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Granular Queue Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${syncEntries.size})") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("filter_sync_all")
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "PENDING",
                    onClick = { selectedFilter = "PENDING" },
                    label = { Text("Pending Upload ($pendingCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TriageYellow.copy(alpha = 0.2f),
                        selectedLabelColor = TriageYellow
                    ),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("filter_sync_pending")
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "FAILED",
                    onClick = { selectedFilter = "FAILED" },
                    label = { Text("Failed ($failedCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TriageRed.copy(alpha = 0.2f),
                        selectedLabelColor = TriageRed
                    ),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("filter_sync_failed")
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "SYNCED",
                    onClick = { selectedFilter = "SYNCED" },
                    label = { Text("Synced ($syncedCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TriageGreen.copy(alpha = 0.2f),
                        selectedLabelColor = TriageGreen
                    ),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("filter_sync_synced")
                )
            }
        }

        // 5. Granular List of Records in SQLite Sync Queue
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No records in selected sync category.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("sync_queue_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    SyncQueueItemCard(
                        item = item,
                        isOnline = isOnline,
                        onRetry = { onRetryItem(item.id) },
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }
    }

    // Deletion Confirmation Dialog
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Delete Queue Record?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${target.payloadType} (${target.recordId}) from the local SQLite sync queue? Unsynced changes will be permanently discarded.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(target.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TriageRed),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_confirm_delete_queue_item")
                ) {
                    Text("Delete Record", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Granular Card displaying an individual SQLite sync queue item with 'retry' or 'delete' actions.
 */
@Composable
private fun SyncQueueItemCard(
    item: SyncQueueEntity,
    isOnline: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFailed = item.status == "FAILED"
    val isPending = item.status == "PENDING" || item.status == "QUEUED"
    val isSynced = item.status == "SYNCED"

    val stripeColor = when {
        isFailed -> TriageRed
        isPending -> TriageYellow
        else -> TriageGreen
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(item.createdAt) { dateFormat.format(Date(item.createdAt)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_queue_item_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) TriageRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFailed) TriageRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Status color stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Target Endpoint, Priority & Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.targetEndpoint == "NHCX") PrimaryContainerTeal else TriageGreenContainer
                        ) {
                            Text(
                                text = item.targetEndpoint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.targetEndpoint == "NHCX") OnPrimaryContainerTeal else OnTriageGreenContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (item.priority) {
                                1 -> TriageRedContainer
                                2 -> TriageYellowContainer
                                else -> TriageGreenContainer
                            }
                        ) {
                            Text(
                                text = when (item.priority) {
                                    1 -> "P1 Emergent"
                                    2 -> "P2 Urgent"
                                    else -> "P3 Routine"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (item.priority) {
                                    1 -> OnTriageRedContainer
                                    2 -> OnTriageYellowContainer
                                    else -> OnTriageGreenContainer
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = item.payloadType,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isFailed -> TriageRedContainer
                            isPending -> TriageYellowContainer
                            else -> TriageGreenContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isFailed -> Icons.Default.Warning
                                    isPending -> Icons.Default.Refresh
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = when {
                                    isFailed -> TriageRed
                                    isPending -> TriageYellow
                                    else -> TriageGreen
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isFailed) "FAILED (Att ${item.retryCount})" else item.status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isFailed -> OnTriageRedContainer
                                    isPending -> OnTriageYellowContainer
                                    else -> OnTriageGreenContainer
                                }
                            )
                        }
                    }
                }

                // Record details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Record: ${item.recordId}${if (item.patientName.isNotBlank()) " • ${item.patientName}" else ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Error Diagnostic Banner (For failed attempts)
                if (isFailed && !item.lastError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TriageRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TriageRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Failure Details",
                                tint = TriageRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Column {
                                Text(
                                    text = "Failure Diagnostic:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TriageRed
                                )
                                Text(
                                    text = item.lastError ?: "Unknown transmission error",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Granular Action Buttons: 'Retry' & 'Delete'
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFailed || isPending) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_retry_sync_${item.id}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry Sync", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_delete_sync_${item.id}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TriageRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Record", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
