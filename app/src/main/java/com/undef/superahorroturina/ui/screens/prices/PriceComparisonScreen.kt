// Pantalla de comparativa de precios entre supermercados.
// Muestra para cada producto del historial del usuario en qué supermercado sale más barato.
package com.undef.superahorroturina.ui.screens.prices

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import com.undef.superahorroturina.data.network.dto.PriceEntryDto
import com.undef.superahorroturina.ui.components.*

@Composable
fun PriceComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: PriceComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark   = isSystemInDarkTheme()
    val moneyFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = "Comparativa de Precios",
                showBack = true,
                onBack   = onNavigateBack
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
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.comparisons.isEmpty() && !uiState.isLoading -> {
                    EmptyState(
                        icon     = Icons.Default.CompareArrows,
                        message  = "Registrá compras en varios supermercados para ver la comparativa",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(32.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // Header con ahorro total potencial
                        item {
                            val totalSavings = uiState.comparisons.sumOf { it.maxSavings }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(if (isDark) 0xFF065F46 else 0xFF059669),
                                                Color(if (isDark) 0xFF164E63 else 0xFF0E7490)
                                            )
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text  = "Ahorro potencial",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text  = "$ ${moneyFormat.format(totalSavings.toLong())}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text  = "si comprás cada producto en el super más barato",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        items(uiState.comparisons) { item ->
                            PriceComparisonCard(
                                item        = item,
                                isDark      = isDark,
                                moneyFormat = moneyFormat
                            )
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceComparisonCard(
    item: PriceComparisonItemDto,
    isDark: Boolean,
    moneyFormat: java.text.NumberFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color        = MaterialTheme.colorScheme.primary,
                borderRadius = 16.dp,
                blurRadius   = 8.dp,
                offsetY      = 2.dp
            )
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Nombre del producto + badge de ahorro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = item.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.savingsPct > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF059669).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text  = "Ahorrás ${item.savingsPct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            GradientDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Lista de precios por supermercado
            item.prices.forEach { entry ->
                PriceRow(
                    entry       = entry,
                    isCheapest  = entry.supermarket == item.cheapestAt,
                    moneyFormat = moneyFormat
                )
            }

            // Resumen: mejor opción
            if (item.maxSavings > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text  = "Compralo en ${item.cheapestAt.replaceFirstChar { it.uppercaseChar() }} y ahorrás $ ${moneyFormat.format(item.maxSavings.toLong())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceRow(
    entry: PriceEntryDto,
    isCheapest: Boolean,
    moneyFormat: java.text.NumberFormat
) {
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
            Icon(
                imageVector = if (isCheapest) Icons.Default.CheckCircle else Icons.Default.Store,
                contentDescription = null,
                tint = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text  = entry.supermarket.replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.bodySmall,
                color = if (isCheapest) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCheapest) FontWeight.SemiBold else FontWeight.Normal
            )
            if (!entry.isUserData) {
                Text(
                    text  = "ref.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        Text(
            text  = "$ ${moneyFormat.format(entry.price.toLong())}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Normal,
            color = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
