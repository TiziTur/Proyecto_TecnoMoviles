// Pestaña "Presupuesto": presupuesto del mes vs gasto real + proyección, y gasto por día de semana.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun StatsBudgetTab(
    uiState: StatsUiState,
    moneyFormat: java.text.NumberFormat,
    isDark: Boolean
) {
    val density    = LocalDensity.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── Presupuesto del mes ────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_budget_title))
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.monthlyLimit <= 0.0) {
                        Text(
                            text = stringResource(R.string.stats_budget_no_limit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val pct = (uiState.currentMonthSpent / uiState.monthlyLimit).toFloat()
                        val barColor = when {
                            pct < 0.8f -> Color(0xFF10B981)
                            pct <= 1.0f -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        SegmentBar(progress = pct, color = barColor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$ ${moneyFormat.format(uiState.currentMonthSpent)} / $ ${moneyFormat.format(uiState.monthlyLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = barColor
                            )
                        }
                        val overLimit = uiState.projectedMonthSpent > uiState.monthlyLimit
                        Text(
                            text = "${stringResource(R.string.stats_projection)}: $ ${moneyFormat.format(uiState.projectedMonthSpent)}" +
                                if (overLimit) " — ${stringResource(R.string.stats_projection_over_limit)}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overLimit) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Gasto por día de la semana ────────────────────
        item {
            SectionHeader(title = stringResource(R.string.stats_weekday_spending))
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
                Column(modifier = Modifier.padding(16.dp)) {
                    val data = uiState.weekdayStats
                    val maxVal = data.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
                    val labelColorArgb = labelColor.toArgb()

                    if (data.all { it.amount == 0.0 }) {
                        Text(
                            stringResource(R.string.stats_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val totalW = size.width
                            val totalH = size.height
                            val bottomLabelPx = with(density) { 18.dp.toPx() }
                            val barAreaH = totalH - bottomLabelPx
                            val n = data.size
                            val groupW = totalW / n
                            val barW = groupW * 0.5f
                            val barRadius = with(density) { 4.dp.toPx() }
                            val shortLabels = listOf("L", "M", "M", "J", "V", "S", "D")

                            data.forEachIndexed { idx, stat ->
                                val ratio = (stat.amount / maxVal).toFloat().coerceIn(0f, 1f)
                                val barH = (barAreaH * ratio).coerceAtLeast(with(density) { 4.dp.toPx() })
                                val left = groupW * idx + (groupW - barW) / 2f
                                val top  = barAreaH - barH

                                drawRoundRect(
                                    color        = Color(0xFF3B82F6),
                                    topLeft      = Offset(left, top),
                                    size         = Size(barW, barH),
                                    cornerRadius = CornerRadius(barRadius, barRadius)
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    shortLabels[idx],
                                    left + barW / 2f,
                                    totalH - with(density) { 4.dp.toPx() },
                                    android.graphics.Paint().apply {
                                        this.color = labelColorArgb
                                        this.textSize = with(density) { 10.sp.toPx() }
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}
