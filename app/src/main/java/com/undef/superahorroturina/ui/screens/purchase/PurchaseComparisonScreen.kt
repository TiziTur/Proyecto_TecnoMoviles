package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.undef.superahorroturina.data.network.dto.PurchaseSupermarketComparisonDto
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.dotPatternBackground

@Composable
fun PurchaseComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: PurchaseComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val moneyFmt = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = "¿Dónde hubiera sido más barato?",
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Analizando precios SEPA...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.error.isNotBlank() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) { Text("Reintentar") }
                    }
                }
                uiState.data != null -> {
                    val data = uiState.data!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // Header: lo que pagaste
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(if (isDark) 0xFF1E3A5F else 0xFF1D4ED8),
                                                Color(if (isDark) 0xFF164E63 else 0xFF0E7490)
                                            )
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Compraste en ${data.supermarket}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "$ ${moneyFmt.format(data.userTotal.toLong())}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    if (data.comparisons.isNotEmpty()) {
                                        val cheapest = data.comparisons.first()
                                        if (cheapest.savings > 0) {
                                            Text(
                                                "Podías ahorrar hasta $ ${moneyFmt.format(cheapest.savings.toLong())} en ${cheapest.supermarket.replaceFirstChar { it.uppercaseChar() }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF86EFAC)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (data.comparisons.isEmpty()) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            "No se encontraron precios SEPA para comparar estos productos.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Tarjeta por supermercado
                        itemsIndexed(data.comparisons) { index, comparison ->
                            SupermarketComparisonCard(
                                comparison  = comparison,
                                userTotal   = data.userTotal,
                                rank        = index + 1,
                                isDark      = isDark,
                                moneyFmt    = moneyFmt
                            )
                        }

                        // Productos sin match
                        if (data.unmatchedProducts.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Sin datos SEPA (${data.unmatchedProducts.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        data.unmatchedProducts.forEach { name ->
                                            Text(
                                                "• $name",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupermarketComparisonCard(
    comparison: PurchaseSupermarketComparisonDto,
    userTotal: Double,
    rank: Int,
    isDark: Boolean,
    moneyFmt: java.text.NumberFormat
) {
    val isCheapest = rank == 1
    val savingsColor = if (comparison.savings >= 0) Color(0xFF059669) else Color(0xFFDC2626)
    val moneyFormat = moneyFmt

    var expanded by remember { mutableStateOf(rank == 1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCheapest)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.3f else 0.2f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Ranking badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCheapest) Color(0xFF059669).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "#$rank",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            comparison.supermarket.replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${comparison.matchedCount} productos coinciden",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$ ${moneyFormat.format(comparison.total.toLong())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (comparison.savings != 0.0) {
                        Text(
                            "${if (comparison.savings >= 0) "-" else "+"}$ ${moneyFormat.format(Math.abs(comparison.savings).toLong())} (${Math.abs(comparison.savingsPct)}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = savingsColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Expandir/contraer detalle de productos
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    if (expanded) "Ocultar detalle" else "Ver productos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                comparison.products.forEach { prod ->
                    val hasMatch = prod.matchedName.isNotBlank()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                prod.ticketName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasMatch && prod.matchedName != prod.ticketName) {
                                Text(
                                    "≈ ${prod.matchedName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (prod.category.isNotBlank()) {
                                Text(
                                    prod.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$ ${moneyFormat.format(prod.sepaPrice.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (prod.sepaPrice < prod.ticketPrice) Color(0xFF059669)
                                        else if (prod.sepaPrice > prod.ticketPrice) Color(0xFFDC2626)
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (!hasMatch) {
                                Text(
                                    "sin dato",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
