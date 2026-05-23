// Pantalla de estadísticas conectada al StatsViewModel (datos reales del backend).
// El gráfico de barras lo dibujé con Canvas puro usando drawRoundRect.
// Para el texto de las etiquetas uso nativeCanvas.drawText con android.graphics.Paint
// porque la API de Canvas de Compose no expone drawText directamente.
// Mejoras v2: valores sobre barras, eje Y con líneas guía, top productos con medallas.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.*

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState     = viewModel.uiState.collectAsStateWithLifecycle().value
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    val chartColors = listOf(
        Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6)
    )
    val labelColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val guideColor    = MaterialTheme.colorScheme.outlineVariant
    val density       = LocalDensity.current
    val primaryColor  = MaterialTheme.colorScheme.primary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_stats),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
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
                        value = "$ ${moneyFormat.format(uiState.avgPurchase)}",
                        icon  = Icons.AutoMirrored.Filled.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Monthly bar chart ─────────────────────────────
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
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) {
                        val data   = uiState.monthlyStats
                        val maxVal = data.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0

                        if (data.isEmpty()) {
                            Text(
                                stringResource(R.string.stats_no_data),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            val labelColorArgb  = labelColor.toArgb()
                            val guideColorArgb  = guideColor.toArgb()
                            val primaryArgb     = primaryColor.toArgb()
                            val canvasHeight    = 220.dp
                            val bottomLabelArea = 28.dp   // espacio para labels del mes
                            val topValueArea    = 22.dp   // espacio para valores sobre barra
                            val yAxisArea       = 12.dp   // pequeño margen izquierdo

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(canvasHeight)
                            ) {
                                val totalW         = size.width
                                val totalH         = size.height
                                val bottomLabelPx  = with(density) { bottomLabelArea.toPx() }
                                val topValuePx     = with(density) { topValueArea.toPx() }
                                val yAxisPx        = with(density) { yAxisArea.toPx() }
                                val barAreaH       = totalH - bottomLabelPx - topValuePx
                                val drawW          = totalW - yAxisPx
                                val n              = data.size
                                val groupW         = drawW / n
                                val barW           = groupW * 0.52f
                                val barRadius      = with(density) { 6.dp.toPx() }
                                val labelTextSize  = with(density) { 10.sp.toPx() }
                                val valueTextSize  = with(density) { 9.5.sp.toPx() }

                                // Líneas guía horizontales (3 niveles: 25%, 50%, 75%, 100%)
                                val guideLines = listOf(0.25f, 0.5f, 0.75f, 1.0f)
                                val guidePaint = android.graphics.Paint().apply {
                                    color     = guideColorArgb
                                    strokeWidth = with(density) { 1.dp.toPx() }
                                    pathEffect = android.graphics.DashPathEffect(
                                        floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 4.dp.toPx() }), 0f
                                    )
                                }
                                guideLines.forEach { ratio ->
                                    val y = topValuePx + barAreaH * (1f - ratio)
                                    drawContext.canvas.nativeCanvas.drawLine(
                                        yAxisPx, y, totalW, y, guidePaint
                                    )
                                }

                                // Barras y etiquetas
                                data.forEachIndexed { idx, stat ->
                                    val ratio = (stat.amount / maxVal).toFloat().coerceIn(0f, 1f)
                                    val barH  = barAreaH * ratio
                                    val left  = yAxisPx + groupW * idx + (groupW - barW) / 2f
                                    val top   = topValuePx + barAreaH - barH
                                    val color = chartColors[idx % chartColors.size]

                                    // Barra con gradiente visual (color sólido + opacidad en fondo)
                                    drawRoundRect(
                                        color        = color.copy(alpha = 0.18f),
                                        topLeft      = Offset(left, topValuePx),
                                        size         = Size(barW, barAreaH),
                                        cornerRadius = CornerRadius(barRadius, barRadius)
                                    )
                                    drawRoundRect(
                                        color        = color,
                                        topLeft      = Offset(left, top),
                                        size         = Size(barW, barH.coerceAtLeast(with(density) { 4.dp.toPx() })),
                                        cornerRadius = CornerRadius(barRadius, barRadius)
                                    )

                                    val centerX = yAxisPx + groupW * idx + groupW / 2f

                                    // Valor encima de la barra
                                    val amountText = "$ ${
                                        if (stat.amount >= 1_000_000)
                                            "${(stat.amount / 1_000_000).toInt()}M"
                                        else if (stat.amount >= 1_000)
                                            "${(stat.amount / 1_000).toInt()}k"
                                        else
                                            stat.amount.toInt().toString()
                                    }"
                                    drawContext.canvas.nativeCanvas.drawText(
                                        amountText,
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

                                    // Label del mes abajo
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

            // ── By supermarket ────────────────────────────────
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
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = color,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                if (idx < uiState.supermarketStats.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

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
                                    0 -> Color(0xFFFFB800) // Oro
                                    1 -> Color(0xFFADB5BD) // Plata
                                    2 -> Color(0xFFCD7F32) // Bronce
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
                                        // Medalla / número
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

            item { Spacer(Modifier.height(24.dp)) }
        }
        } // dotPatternBackground Box
    }
}
