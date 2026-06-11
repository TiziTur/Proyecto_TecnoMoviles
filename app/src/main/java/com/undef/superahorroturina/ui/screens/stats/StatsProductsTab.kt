// Pestaña "Productos": top productos, mayores aumentos de precio y frecuencia/canasta.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsProductsTab(
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

        // ── Top products ──────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_top_products))
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
                    if (uiState.topProducts.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.topProducts.forEachIndexed { idx, stat ->
                            val medalColor = when (idx) {
                                0 -> Color(0xFFFFB800)
                                1 -> Color(0xFFADB5BD)
                                2 -> Color(0xFFCD7F32)
                                else -> chartColors[idx % chartColors.size]
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(medalColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (idx) {
                                                0 -> "🥇"
                                                1 -> "🥈"
                                                2 -> "🥉"
                                                else -> "${idx + 1}"
                                            },
                                            style = if (idx < 3) MaterialTheme.typography.bodyMedium
                                                    else MaterialTheme.typography.labelLarge,
                                            color = medalColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Total gastado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "$ ${moneyFormat.format(stat.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = medalColor,
                                    maxLines = 1
                                )
                            }
                            if (idx < uiState.topProducts.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Productos con mayor aumento de precio ─────────
        item {
            SectionHeader(title = stringResource(R.string.stats_price_increases))
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.priceIncreases.isEmpty()) {
                        Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.priceIncreases.forEachIndexed { idx, change ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = change.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "+${change.pctChange.toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                            if (idx < uiState.priceIncreases.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── Frecuencia y tamaño de canasta ────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_purchase_frequency))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${uiState.purchaseCountThisMonth}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stats_purchases_this_month),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "%.1f".format(uiState.avgItemsPerPurchase),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stats_avg_items),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
