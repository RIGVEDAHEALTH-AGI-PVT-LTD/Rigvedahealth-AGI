package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TriageTier
import com.example.ui.theme.OnTriageGreenContainer
import com.example.ui.theme.OnTriageRedContainer
import com.example.ui.theme.OnTriageYellowContainer
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageGreenContainer
import com.example.ui.theme.TriageRed
import com.example.ui.theme.TriageRedContainer
import com.example.ui.theme.TriageYellow
import com.example.ui.theme.TriageYellowContainer

object TriageVisualTokens {
    fun getPrimaryColor(tier: TriageTier): Color = when (tier) {
        TriageTier.RED -> TriageRed
        TriageTier.YELLOW -> TriageYellow
        TriageTier.GREEN -> TriageGreen
    }

    fun getContainerColor(tier: TriageTier): Color = when (tier) {
        TriageTier.RED -> TriageRedContainer
        TriageTier.YELLOW -> TriageYellowContainer
        TriageTier.GREEN -> TriageGreenContainer
    }

    fun getOnContainerColor(tier: TriageTier): Color = when (tier) {
        TriageTier.RED -> OnTriageRedContainer
        TriageTier.YELLOW -> OnTriageYellowContainer
        TriageTier.GREEN -> OnTriageGreenContainer
    }
}

@Composable
fun TriageUrgencyBadge(
    tier: TriageTier,
    modifier: Modifier = Modifier,
    isReferralRequired: Boolean = false,
    showFullDetails: Boolean = false
) {
    val primaryColor = TriageVisualTokens.getPrimaryColor(tier)
    val containerColor = TriageVisualTokens.getContainerColor(tier)
    val onColor = TriageVisualTokens.getOnContainerColor(tier)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (tier == TriageTier.RED) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            Text(
                text = if (showFullDetails) "${tier.name} • ${tier.title}" else tier.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = onColor
            )
            if (isReferralRequired && tier == TriageTier.RED) {
                Text(
                    text = "• 108 REFERRAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TriageRed
                )
            }
        }
    }
}

@Composable
fun TriageAssessmentCard(
    tier: TriageTier,
    rationale: String,
    onCallAmbulance: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = TriageVisualTokens.getPrimaryColor(tier)
    val containerColor = TriageVisualTokens.getContainerColor(tier)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(
            width = if (tier == TriageTier.RED) 2.dp else 1.dp,
            color = primaryColor
        )
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (tier == TriageTier.RED) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Clinical Triage Tier: ${tier.title}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = primaryColor
                    )
                }
            }

            Text(
                text = rationale.ifBlank { tier.description },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )

            if (tier == TriageTier.RED) {
                Button(
                    onClick = onCallAmbulance,
                    colors = ButtonDefaults.buttonColors(containerColor = TriageRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_call_108_referral")
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Tertiary 108 Referral Advisory", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TriageUrgencySelector(
    selectedTier: TriageTier,
    onTierSelected: (TriageTier) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Operator Urgency Tier Override:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TriageTier.entries.forEach { tier ->
                val isSelected = selectedTier == tier
                val primaryColor = TriageVisualTokens.getPrimaryColor(tier)

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTierSelected(tier) }
                        .testTag("btn_override_${tier.name.lowercase()}"),
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = tier.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TriageFilterBar(
    selectedTier: String,
    redCount: Int,
    yellowCount: Int,
    greenCount: Int,
    totalCount: Int,
    onSelectTier: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val filters = listOf(
            Triple("ALL", "All ($totalCount)", MaterialTheme.colorScheme.outline),
            Triple("RED", "Red ($redCount)", TriageRed),
            Triple("YELLOW", "Yellow ($yellowCount)", TriageYellow),
            Triple("GREEN", "Green ($greenCount)", TriageGreen)
        )

        filters.forEach { (filterKey, label, color) ->
            val isSelected = selectedTier == filterKey
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectTier(filterKey) }
                    .testTag("filter_tier_${filterKey.lowercase()}"),
                color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
