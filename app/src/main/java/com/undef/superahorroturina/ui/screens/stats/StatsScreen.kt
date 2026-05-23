// Pantalla de estadísticas conectada al StatsViewModel (datos reales del backend).
// El gráfico de barras lo dibujé con Canvas puro usando drawRoundRect.
// Para el texto de las etiquetas uso nativeCanvas.drawText con android.graphics.Paint
// porque la API de Canvas de Compose no expone drawText directamente.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.StatCard
import com.undef.superahorroturina.ui.components.SectionHeader

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState     = viewModel.uiState.collectAsStateWithLifecycle().value
    val moneyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))
    val avgPurchase = if (uiState.monthlyStats.isNotEmpty())
        uiState.totalAllTime / uiState.monthlyStats.size else 0.0

    val chartColors = listOf(
        Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6)
    )
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density    = LocalDensity.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_stats),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Summary cards ─────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = stringResource(R.string.stat_total_spent),
                        value = "$ ${moneyFormat.format(uiState.totalAllTime)}",
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

            // ── Monthly bar chart ─────────────────────────────
            item {
                SectionHeader(title = stringResource(R.string.stats_monthly_evolution))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val data   = uiState.monthlyStats
                        val maxVal = data.maxOfOrNull { it.amount } ?: 1.0
                        val labelColorArgb = labelColor.toArgb()
                        val canvasHeight   = 200.dp
                        val labelAreaDp    = 24.dp

                        if (data.isEmpty()) {
                            Text("Sin datos aún", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp))
                        } else {
                            Canvas(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
                                val totalW      = size.width
                                val totalH      = size.height
                                val labelAreaPx = with(density) { labelAreaDp.toPx() }
                                val barAreaH    = totalH - labelAreaPx
                                val n           = data.size
                                val groupW      = totalW / n
                                val barW        = groupW * 0.55f
                                val barRadius   = with(density) { 4.dp.toPx() }
                                val textSize    = with(density) { 10.sp.toPx() }

                                data.forEachIndexed { idx, stat ->
                                    val ratio = (stat.amount / maxVal).toFloat().coerceIn(0f, 1f)
                                    val barH  = barAreaH * ratio
                                    val left  = groupW * idx + (groupW - barW) / 2f
                                    val top   = barAreaH - barH
                                    val color = chartColors[idx % chartColors.size]

                                    drawRoundRect(color = color, topLeft = Offset(left, top),
                                        size = Size(barW, barH),
                                        cornerRadius = CornerRadius(barRadius, barRadius))

                                    drawContext.canvas.nativeCanvas.drawText(
                                        stat.label, groupW * idx + groupW / 2f, totalH,
                                        android.graphics.Paint().apply {
                                            this.color = labelColorArgb
                                            this.textSize = textSize
                                            this.textAlign = android.graphics.Paint.Align.CENTER
                                            isAntiAlias = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── By supermarket ────────────────────────────────
            item {
                SectionHeader(title = stringResource(R.string.stats_by_supermarket))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val total = uiState.supermarketStats.sumOf { it.amount }
                        if (uiState.supermarketStats.isEmpty()) {
                            Text("Sin datos aún", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            uiState.supermarketStats.forEachIndexed { idx, stat ->
                                val pct = if (total > 0) (stat.amount / total).toFloat() else 0f
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = stat.label, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "$ ${moneyFormat.format(stat.amount)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = chartColors[idx % chartColors.size], maxLines = 1)
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
            }

            // ── Top products ──────────────────────────────────
            item {
                SectionHeader(title = stringResource(R.string.stats_top_products))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.topProducts.isEmpty()) {
                            Text("Sin datos aún", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            uiState.topProducts.forEachIndexed { idx, stat ->
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)) {
                                        Badge(containerColor = chartColors[idx % chartColors.size]) {
                                            Text("${idx + 1}", color = Color.White)
                                        }
                                        Text(text = stat.label, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(text = "$ ${moneyFormat.format(stat.amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (idx < uiState.topProducts.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
