package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.StatCard
import com.undef.superahorroturina.ui.components.SectionHeader

@Composable
fun StatsScreen(onNavigateBack: () -> Unit) {
    val moneyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))
    val totalAll    = MockData.purchases.sumOf { it.total }
    val avgPurchase = if (MockData.purchases.isNotEmpty()) totalAll / MockData.purchases.size else 0.0

    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val chartColors = listOf(
        Color(0xFF3B82F6),
        Color(0xFF06B6D4),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFEF4444),
        Color(0xFF8B5CF6)
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_stats),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Summary cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = stringResource(R.string.stat_total_spent),
                        value = "$ ${moneyFormat.format(totalAll)}",
                        icon  = Icons.Default.AccountBalanceWallet,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(R.string.stat_avg_purchase),
                        value = "$ ${moneyFormat.format(avgPurchase)}",
                        icon  = Icons.AutoMirrored.Filled.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Monthly bar chart
            item {
                SectionHeader(title = stringResource(R.string.stats_monthly_evolution))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val maxVal = MockData.monthlyExpenses.maxOf { it.amount }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            val barW = (maxWidth / MockData.monthlyExpenses.size) - 8.dp
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                MockData.monthlyExpenses.forEachIndexed { idx, stat ->
                                    val ratio = (stat.amount / maxVal).toFloat()
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Canvas(modifier = Modifier.width(barW).fillMaxHeight(ratio)) {
                                            drawRect(
                                                color = chartColors[idx % chartColors.size],
                                                size = Size(size.width, size.height)
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stat.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // By supermarket
            item {
                SectionHeader(title = stringResource(R.string.stats_by_supermarket))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val total = MockData.expensesBySupermarket.sumOf { it.amount }
                        MockData.expensesBySupermarket.forEachIndexed { idx, stat ->
                            val pct = if (total > 0) (stat.amount / total).toFloat() else 0f
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stat.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "$ ${moneyFormat.format(stat.amount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = chartColors[idx % chartColors.size]
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = chartColors[idx % chartColors.size],
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Top products
            item {
                SectionHeader(title = stringResource(R.string.stats_top_products))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MockData.topProducts.forEachIndexed { idx, stat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Badge(containerColor = chartColors[idx % chartColors.size]) {
                                        Text("${idx + 1}", color = Color.White)
                                    }
                                    Text(stat.label, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    "${stat.amount.toInt()} veces",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx < MockData.topProducts.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
