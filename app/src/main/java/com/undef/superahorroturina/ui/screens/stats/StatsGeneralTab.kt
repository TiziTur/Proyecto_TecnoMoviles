// Pestaña "General": total gastado, promedio, evolución mensual y comparación con mes anterior.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.SectionHeader
import com.undef.superahorroturina.ui.components.StatCard
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsGeneralTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    chartColors: List<Color>,
    isDark: Boolean
) {
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant
    val guideColor   = MaterialTheme.colorScheme.outlineVariant
    val density      = LocalDensity.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                    value = "$ ${moneyFormat.format(uiState.avgPurchase)}",
                    icon  = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Monthly bar chart (modernizado) ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_monthly_evolution))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) {
                    val data = uiState.monthlyStats

                    if (data.isEmpty()) {
                        Text(
                            stringResource(R.string.stats_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
                        )
                    } else {
                        val axisMax        = niceAxisMax(data.maxOf { it.amount })
                        val labelColorArgb = labelColor.toArgb()
                        val guideColorArgb = guideColor.toArgb()
                        val canvasHeight   = 220.dp
                        val bottomLabelArea = 28.dp
                        val topValueArea    = 22.dp
                        val yAxisArea       = 44.dp

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(canvasHeight)
                        ) {
                            val totalW        = size.width
                            val totalH        = size.height
                            val bottomLabelPx = with(density) { bottomLabelArea.toPx() }
                            val topValuePx    = with(density) { topValueArea.toPx() }
                            val yAxisPx       = with(density) { yAxisArea.toPx() }
                            val barAreaH      = totalH - bottomLabelPx - topValuePx
                            val drawW         = totalW - yAxisPx
                            val n             = data.size
                            val groupW        = drawW / n
                            val barW          = groupW * 0.52f
                            val barRadius     = with(density) { 6.dp.toPx() }
                            val labelTextSize = with(density) { 10.sp.toPx() }
                            val valueTextSize = with(density) { 9.5.sp.toPx() }
                            val axisTextSize  = with(density) { 9.sp.toPx() }

                            // Eje Y: 4 etiquetas (0, 1/3, 2/3, máximo) + líneas guía
                            val axisPaint = android.graphics.Paint().apply {
                                color    = labelColorArgb
                                textSize = axisTextSize
                                textAlign = android.graphics.Paint.Align.RIGHT
                                isAntiAlias = true
                            }
                            val guidePaint = android.graphics.Paint().apply {
                                color = guideColorArgb
                                strokeWidth = with(density) { 1.dp.toPx() }
                            }
                            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { ratio ->
                                val y = topValuePx + barAreaH * (1f - ratio)
                                drawContext.canvas.nativeCanvas.drawText(
                                    formatCompactCurrency(axisMax * ratio),
                                    yAxisPx - with(density) { 6.dp.toPx() },
                                    y + axisTextSize / 3f,
                                    axisPaint
                                )
                                if (ratio > 0f) {
                                    drawContext.canvas.nativeCanvas.drawLine(yAxisPx, y, totalW, y, guidePaint)
                                }
                            }

                            // Barras y etiquetas
                            data.forEachIndexed { idx, stat ->
                                val ratio = (stat.amount / axisMax).toFloat().coerceIn(0f, 1f)
                                val barH  = barAreaH * ratio
                                val left  = yAxisPx + groupW * idx + (groupW - barW) / 2f
                                val top   = topValuePx + barAreaH - barH
                                val color = chartColors[idx % chartColors.size]

                                drawRoundRect(
                                    color        = color.copy(alpha = 0.18f),
                                    topLeft      = Offset(left, topValuePx),
                                    size         = Size(barW, barAreaH),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )
                                drawRoundRect(
                                    brush        = Brush.verticalGradient(
                                        colors = listOf(color.copy(alpha = 0.7f), color),
                                        startY = top,
                                        endY   = topValuePx + barAreaH
                                    ),
                                    topLeft      = Offset(left, top),
                                    size         = Size(barW, barH.coerceAtLeast(with(density) { 4.dp.toPx() })),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )

                                val centerX = yAxisPx + groupW * idx + groupW / 2f

                                drawContext.canvas.nativeCanvas.drawText(
                                    formatCompactCurrency(stat.amount),
                                    centerX,
                                    (top - with(density) { 4.dp.toPx() }).coerceAtLeast(topValuePx - with(density) { 2.dp.toPx() }),
                                    android.graphics.Paint().apply {
                                        this.color    = color.toArgb()
                                        this.textSize = valueTextSize
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias   = true
                                        isFakeBoldText = true
                                    }
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    stat.label,
                                    centerX,
                                    totalH - with(density) { 4.dp.toPx() },
                                    android.graphics.Paint().apply {
                                        this.color    = labelColorArgb
                                        this.textSize = labelTextSize
                                        this.textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias   = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Comparación con el mes anterior ───────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_month_comparison))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.secondary,
                        borderRadius = 16.dp,
                        blurRadius   = 10.dp,
                        offsetY      = 3.dp
                    )
                    .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$ ${moneyFormat.format(uiState.currentMonthSpent)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val pct = uiState.monthOverMonthPct
                    if (pct == null) {
                        Text(
                            text = stringResource(R.string.stats_no_previous_month),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val isUp = pct >= 0
                        val color = if (isUp) Color(0xFFEF4444) else Color(0xFF10B981)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${if (isUp) "+" else ""}${pct.toInt()}% ${stringResource(R.string.stats_vs_last_month)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
