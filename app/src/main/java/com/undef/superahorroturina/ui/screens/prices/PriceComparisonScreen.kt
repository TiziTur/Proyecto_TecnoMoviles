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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import com.undef.superahorroturina.data.network.dto.PriceEntryDto
import com.undef.superahorroturina.ui.components.*

private val CATEGORY_ORDER = listOf(
    "Bebida", "Lácteo", "Carne y Fiambre", "Panadería", "Almacén",
    "Cereales", "Aceite", "Condimento", "Enlatado", "Congelado",
    "Golosinas", "Snack", "Limpieza", "Papel", "Perfumería",
    "Bebé", "Mascotas", "Alimento"
)

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Bebida"          -> Color(0xFF0EA5E9)
    "Lácteo"          -> Color(0xFFF59E0B)
    "Carne y Fiambre" -> Color(0xFFEF4444)
    "Panadería"       -> Color(0xFF92400E)
    "Almacén"         -> Color(0xFF84CC16)
    "Cereales"        -> Color(0xFFF97316)
    "Aceite"          -> Color(0xFFD97706)
    "Condimento"      -> Color(0xFFEAB308)
    "Enlatado"        -> Color(0xFF78716C)
    "Congelado"       -> Color(0xFF38BDF8)
    "Golosinas"       -> Color(0xFFEC4899)
    "Snack"           -> Color(0xFFFF6B35)
    "Limpieza"        -> Color(0xFF6366F1)
    "Papel"           -> Color(0xFF8B5CF6)
    "Perfumería"      -> Color(0xFFDB2777)
    "Bebé"            -> Color(0xFF06B6D4)
    "Mascotas"        -> Color(0xFF10B981)
    else              -> Color(0xFF64748B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: PriceComparisonViewModel = hiltViewModel()
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery      by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedBrand    by viewModel.selectedBrand.collectAsStateWithLifecycle()
    val minPriceInput    by viewModel.minPriceInput.collectAsStateWithLifecycle()
    val maxPriceInput    by viewModel.maxPriceInput.collectAsStateWithLifecycle()
    val sortOrder        by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isDark           = isSystemInDarkTheme()
    val moneyFormat      = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }
    val listState        = rememberLazyListState()
    var showFilterSheet  by remember { mutableStateOf(false) }

    // Cantidad de filtros extra activos (excluyendo búsqueda y categoría)
    val activeFilterCount = remember(selectedBrand, minPriceInput, maxPriceInput, sortOrder) {
        listOf(selectedBrand, minPriceInput, maxPriceInput).count { it.isNotBlank() } +
            if (sortOrder != "name") 1 else 0
    }

    // Infinite scroll: pedir más cuando quedan ≤ 5 ítems al fondo
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }

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
                // ── Búsqueda + botón de filtros ───────────────────────────────
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        label         = { Text("Buscar producto…") },
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = {
                            if (searchQuery.isNotBlank())
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                        },
                        modifier   = Modifier.weight(1f),
                        singleLine = true,
                        shape      = MaterialTheme.shapes.large
                    )
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0)
                                Badge { Text("$activeFilterCount") }
                        }
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filtros",
                                tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ── Chips de ordenamiento ────────────────────────────────────
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = sortOrder == "price_asc",
                        onClick  = {
                            viewModel.sortOrder.value =
                                if (sortOrder == "price_asc") "name" else "price_asc"
                        },
                        label        = { Text("Precio ↑") },
                        leadingIcon  = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = sortOrder == "price_desc",
                        onClick  = {
                            viewModel.sortOrder.value =
                                if (sortOrder == "price_desc") "name" else "price_desc"
                        },
                        label        = { Text("Precio ↓") },
                        leadingIcon  = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(14.dp)) }
                    )
                }
                Spacer(Modifier.height(4.dp))

                // ── Chips de categoría ────────────────────────────────────────
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
                                label    = { Text("Todos (${uiState.total})") }
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

                // ── Contenido ─────────────────────────────────────────────────
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
                    uiState.allComparisons.isEmpty() -> {
                        EmptyState(
                            icon    = Icons.Default.SearchOff,
                            message = when {
                                searchQuery.isNotBlank() ->
                                    "Sin resultados para \"$searchQuery\"\nProbá sin tilde o con otra ortografía"
                                selectedCategory.isNotBlank() || activeFilterCount > 0 ->
                                    "Sin productos con los filtros aplicados"
                                else ->
                                    "Registrá compras en varios supermercados para ver comparativas"
                            },
                            modifier = Modifier.fillMaxSize().padding(32.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                SavingsBanner(
                                    totalSavings = uiState.allComparisons.sumOf { it.maxSavings },
                                    shown        = uiState.allComparisons.size,
                                    total        = uiState.total,
                                    isDark       = isDark,
                                    moneyFormat  = moneyFormat,
                                    modifier     = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            items(uiState.allComparisons, key = { it.productName + it.category }) { item ->
                                CompactPriceCard(
                                    item        = item,
                                    isDark      = isDark,
                                    moneyFormat = moneyFormat,
                                    modifier    = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                                }
                            }

                            if (uiState.source.isNotBlank()) {
                                item {
                                    Text(
                                        text  = "Fuente: ${uiState.source}" +
                                                if (uiState.lastUpdated != null)
                                                    " · Act: ${uiState.lastUpdated!!.take(10)}" else "",
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

    // ── Bottom sheet de filtros ───────────────────────────────────────────────
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterSheetContent(
                brands        = uiState.availableBrands,
                selectedBrand = selectedBrand,
                minPrice      = minPriceInput,
                maxPrice      = maxPriceInput,
                onBrandChange = { viewModel.selectedBrand.value = it },
                onMinChange   = { viewModel.minPriceInput.value = it },
                onMaxChange   = { viewModel.maxPriceInput.value = it },
                onClear       = { viewModel.clearFilters(); showFilterSheet = false },
                onDone        = { showFilterSheet = false }
            )
        }
    }
}

// ── Filter bottom sheet content ───────────────────────────────────────────────

@Composable
private fun FilterSheetContent(
    brands: List<String>,
    selectedBrand: String,
    minPrice: String,
    maxPrice: String,
    onBrandChange: (String) -> Unit,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp)
    ) {
        Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Marca
        OutlinedTextField(
            value         = selectedBrand,
            onValueChange = onBrandChange,
            label         = { Text("Marca") },
            leadingIcon   = { Icon(Icons.Default.Store, null) },
            trailingIcon  = {
                if (selectedBrand.isNotBlank())
                    IconButton(onClick = { onBrandChange("") }) { Icon(Icons.Default.Clear, null) }
            },
            modifier   = Modifier.fillMaxWidth(),
            singleLine = true,
            shape      = MaterialTheme.shapes.large
        )

        // Sugerencias de marcas
        if (brands.isNotEmpty() && selectedBrand.isBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(brands.take(12)) { brand ->
                    SuggestionChip(
                        onClick = { onBrandChange(brand) },
                        label   = { Text(brand, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        HorizontalDivider()

        // Rango de precios
        Text("Rango de precio ($)", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value         = minPrice,
                onValueChange = onMinChange,
                label         = { Text("Mínimo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                shape         = MaterialTheme.shapes.large
            )
            OutlinedTextField(
                value         = maxPrice,
                onValueChange = onMaxChange,
                label         = { Text("Máximo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                shape         = MaterialTheme.shapes.large
            )
        }

        // Botones
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick  = onClear,
                modifier = Modifier.weight(1f)
            ) { Text("Limpiar") }
            Button(
                onClick  = onDone,
                modifier = Modifier.weight(1f)
            ) { Text("Listo") }
        }
    }
}

// ── Savings banner ─────────────────────────────────────────────────────────────

@Composable
private fun SavingsBanner(
    totalSavings: Double,
    shown: Int,
    total: Int,
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
            Text("Ahorro potencial", style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f))
            Text(
                text       = "$ ${moneyFormat.format(totalSavings.toLong())}",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White
            )
            Text(
                text  = "Mostrando $shown de $total productos · deslizá para ver más",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Tarjeta compacta expandible ───────────────────────────────────────────────

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
            .coloredShadow(color = MaterialTheme.colorScheme.primary, borderRadius = 16.dp, blurRadius = 6.dp, offsetY = 2.dp)
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabecera
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
                        if (item.brand.isNotBlank()) {
                            Text(
                                text  = item.brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (item.prices.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text       = "${supermarketLabel(item.cheapestAt)} · $ ${moneyFormat.format(item.cheapestPrice.toLong())}",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = Color(0xFF059669),
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
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

            // Detalle expandido
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
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
                            Icon(Icons.Default.TipsAndUpdates, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                text       = "Compralo en ${supermarketLabel(item.cheapestAt)} y ahorrás $ ${moneyFormat.format(item.maxSavings.toLong())}",
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

private fun supermarketLabel(raw: String): String =
    raw.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }

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
                text       = supermarketLabel(entry.supermarket),
                style      = MaterialTheme.typography.bodySmall,
                color      = if (isCheapest) MaterialTheme.colorScheme.onSurface
                             else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCheapest) FontWeight.SemiBold else FontWeight.Normal
            )
            // "ref." eliminado — el ícono ya diferencia entre precio propio y de referencia
        }
        Text(
            text       = "$ ${moneyFormat.format(entry.price.toLong())}",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Normal,
            color      = if (isCheapest) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
