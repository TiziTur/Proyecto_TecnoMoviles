// Pestaña "Supermercados": gasto por supermercado (barra custom de una sola pieza) y ticket promedio.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsSupermarketsTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    chartColors: List<Color>,
    isDark: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── By supermarket (con SegmentBar) ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_by_supermarket))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val total = uiState.supermarketStats.sumOf { it.amount }
                    if (uiState.supermarketStats.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.supermarketStats.forEachIndexed { idx, stat ->
                            val pct = if (total > 0) (stat.amount / total).toFloat() else 0f
                            val color = chartColors[idx % chartColors.size]
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Text(
                                            text = stat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$ ${moneyFormat.format(stat.amount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = color
                                        )
                                        Text(
                                            text = "${(pct * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                SegmentBar(progress = pct, color = color)
                            }
                            if (idx < uiState.supermarketStats.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Ticket promedio por supermercado ──────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_avg_ticket))
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.avgTicketBySupermarket.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.avgTicketBySupermarket.forEachIndexed { idx, stat ->
                            val color = chartColors[idx % chartColors.size]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = stat.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$ ${moneyFormat.format(stat.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = color
                                )
                            }
                            if (idx < uiState.avgTicketBySupermarket.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
