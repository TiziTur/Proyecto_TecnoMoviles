package com.undef.superahorroturina.ui.screens.prices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

private val CATEGORY_ORDER = listOf(
    "Bebida", "Lácteo", "Limpieza", "Perfumería", "Snack",
    "Panadería", "Carne", "Congelado", "Condimento", "Enlatado", "Almacén", "Alimento"
)

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Bebida"     -> Color(0xFF0EA5E9)
    "Lácteo"     -> Color(0xFFF59E0B)
    "Limpieza"   -> Color(0xFF6366F1)
    "Perfumería" -> Color(0xFFEC4899)
    "Snack"      -> Color(0xFFF97316)
    "Panadería"  -> Color(0xFF92400E)
    "Carne"      -> Color(0xFFEF4444)
    "Congelado"  -> Color(0xFF38BDF8)
    "Condimento" -> Color(0xFFEAB308)
    "Enlatado"   -> Color(0xFF78716C)
    "Almacén"    -> Color(0xFF84CC16)
    else         -> Color(0xFF64748B)
}

@Composable
fun PriceComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: PriceComparisonViewModel = hiltViewModel()
) {
    val uiState             by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery         by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory    by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredComparisons by viewModel.filteredComparisons.collectAsStateWithLifecycle()
    val isDark              = isSystemInDarkTheme()
    val moneyFormat = remember {
        java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Search bar ────────────────────────────────────────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    label         = { Text("Buscar producto") },
                    leadingIcon   = { Icon(Icons.Default.Search, null) },
                    trailingIcon  = {
                        if (searchQuery.isNotBlank())
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                    },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    singleLine = true,
                    shape      = MaterialTheme.shapes.large
                )
                Spacer(Modifier.height(8.dp))

                // ── Category chips ────────────────────────────────────────────
                val visibleCats = CATEGORY_ORDER.filter { (uiState.categoryCounts[it] ?: 0) > 0 }
                if (visibleCats.isNotEmpty()) {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory.isBlank(),
                                onClick  = { viewModel.selectedCategory.value = "" },
                                label    = { Text("Todos (${uiState.allComparisons.size})") }
                            )
                        }
                        items(visibleCats) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick  = {
                                    viewModel.selectedCategory.value =
                                        if (selectedCategory == cat) "" else cat
                                },
                                label = { Text("$cat (${uiState.categoryCounts[cat] ?: 0})") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Content ───────────────────────────────────────────────────
                when {
                    uiState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error.isNotBlank() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(uiState.error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    filteredComparisons.isEmpty() -> {
                        EmptyState(
                            icon    = Icons.Default.CompareArrows,
                            message = when {
                                searchQuery.isNotBlank() ->
                                    "No se encontraron productos con \"$searchQuery\""
                                selectedCategory.isNotBlank() ->
                                    "No hay productos en la categoría \"$selectedCategory\""
                                else ->
                                    "Registrá compras en varios supermercados para ver comparativas"
                            },
                            modifier = Modifier.fillMaxSize().padding(32.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                val totalSavings = filteredComparisons.sumOf { it.maxSavings }
                                SavingsBanner(
                                    totalSavings = totalSavings,
                                    isDark       = isDark,
                                    moneyFormat  = moneyFormat,
                                    modifier     = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            items(filteredComparisons, key = { it.productName + it.category }) { item ->
                                CompactPriceCard(
                                    item        = item,
                                    isDark      = isDark,
                                    moneyFormat = moneyFormat,
                                    modifier    = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            if (uiState.source.isNotBlank()) {
                                item {
                                    Text(
                                        text  = "Fuente: ${uiState.source}" +
                                                if (uiState.lastUpdated != null)
                                                    " · Act: ${uiState.lastUpdated!!.take(10)}"
                                                else "",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ── Savings banner ─────────────────────────────────────────────────────────────

@Composable
private fun SavingsBanner(
    totalSavings: Double,
    isDark: Boolean,
    moneyFormat: java.text.NumberFormat,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
                text       = "$ ${moneyFormat.format(totalSavings.toLong())}",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White
            )
            Text(
                text  = "si comprás cada producto en el super más barato",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Compact expandable card ────────────────────────────────────────────────────

@Composable
private fun CompactPriceCard(
    item: PriceComparisonItemDto,
    isDark: Boolean,
    moneyFormat: java.text.NumberFormat,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val catColor = categoryColor(item.category)

    Card(
        onClick   = { expanded = !expanded },
        modifier  = modifier
            .fillMaxWidth()
            .coloredShadow(
                color        = MaterialTheme.colorScheme.primary,
                borderRadius = 16.dp,
                blurRadius   = 6.dp,
                offsetY      = 2.dp
            )
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Header row ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text       = item.productName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (item.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(catColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text       = item.category,
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = catColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (item.prices.isNotEmpty()) {
                            Text(
                                text       = "${item.cheapestAt.replaceFirstChar { it.uppercaseChar() }} · $ ${moneyFormat.format(item.cheapestPrice.toLong())}",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = Color(0xFF059669),
                                fontWeight = FontWeight.Medium,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.savingsPct > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF059669).copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text       = "-${item.savingsPct}%",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = Color(0xFF059669),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(
                        imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            // ── Expanded detail ───────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    GradientDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))

                    item.prices.forEach { entry ->
                        PriceRow(
                            entry      = entry,
                            isCheapest = entry.supermarket == item.cheapestAt,
                            moneyFormat = moneyFormat
                        )
                    }

                    if (item.maxSavings > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TipsAndUpdates,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text       = "Compralo en ${item.cheapestAt.replaceFirstChar { it.uppercaseChar() }} y ahorrás $ ${moneyFormat.format(item.maxSavings.toLong())}",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Icon(
                imageVector        = if (isCheapest) Icons.Default.CheckCircle else Icons.Default.Store,
                contentDescription = null,
                tint     = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text       = entry.supermarket.replaceFirstChar { it.uppercaseChar() },
                style      = MaterialTheme.typography.bodySmall,
                color      = if (isCheapest) MaterialTheme.colorScheme.onSurface
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
            text       = "$ ${moneyFormat.format(entry.price.toLong())}",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Normal,
            color      = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
