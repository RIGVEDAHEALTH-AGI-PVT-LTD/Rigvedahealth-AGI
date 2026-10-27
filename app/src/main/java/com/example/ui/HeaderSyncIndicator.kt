package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun HeaderSyncIndicator(
    pendingCount: Int,
    isOnline: Boolean,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    // Pulsing animation when pending items exist
    val infiniteTransition = rememberInfiniteTransition(label = "SyncPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (pendingCount > 0) 1.22f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SyncPulseScale"
    )

    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { showDialog = true }
            .testTag("header_sync_indicator"),
        color = if (pendingCount > 0) TriageYellowContainer else TriageGreenContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (pendingCount > 0) TriageYellow else TriageGreen
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulsing dot indicator
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(if (pendingCount > 0) TriageYellow else TriageGreen)
            )

            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Encrypted SQLite",
                modifier = Modifier.size(13.dp),
                tint = if (pendingCount > 0) OnTriageYellowContainer else OnTriageGreenContainer
            )

            Text(
                text = if (pendingCount > 0) "$pendingCount Pending" else "Cache Synced",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (pendingCount > 0) OnTriageYellowContainer else OnTriageGreenContainer
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryTeal)
                    Text("Encrypted SQLite Cache Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Storage: SQLCipher AES-256 On-Device Encryption Active\n" +
                               "• Network: ${if (isOnline) "Connected (Online)" else "Offline Native Mode"}\n" +
                               "• Pending SQLite Transactions: $pendingCount items awaiting push to NHCX Payer & ABDM Gateway",
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    if (pendingCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TriageYellowContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "WorkManager will automatically flush these transactions when network connectivity resumes.",
                                    fontSize = 11.sp,
                                    color = OnTriageYellowContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTriggerSync()
                        showDialog = false
                    },
                    enabled = isOnline && pendingCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Now to NHCX")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }
}
