// Pantalla de historial de compras.
// v3: searchQuery y selectedFilter viven en el ViewModel con debounce(300ms).
// Agregado pull-to-refresh (PullToRefreshBox) y swipe-to-delete (SwipeToDismissBox).
// v4: Filtros avanzados (rango de fechas, monto, supermercado) con FilterPanel expandible.
package com.undef.superahorroturina.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPurchaseDetail: (Int) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery    by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isDark          = isSystemInDarkTheme()
    val context         = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.loadPurchases()
        onPauseOrDispose { }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val moneyFormat   = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    val filterOptions = listOf(stringResource(R.string.action_all)) + uiState.purchases
        .map { it.supermarket }
        .distinct()
        .sorted()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.exportToCsv(
                            context = context,
                            onReady = { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Historial de compras — Super Ahorro")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Exportar compras"))
                            },
                            onError = { /* no-op en demo */ }
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_export_csv))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh    = { viewModel.refresh() },
                        modifier     = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }

                            item {
                                HistorySearchField(
                                    searchQuery = searchQuery,
                                    onSearchChange = { viewModel.searchQuery.value = it }
                                )
                            }

                            item {
                                HistoryFilterChipsRow(
                                    filterOptions  = filterOptions,
                                    selectedFilter = selectedFilter,
                                    onFilterChange = { viewModel.selectedFilter.value = it }
                                )
                            }

                            item {
                                AdvancedFiltersToggleRow(
                                    activeCount = uiState.filters.activeCount,
                                    onToggle    = { viewModel.toggleShowFilters() },
                                    onClear     = { viewModel.clearFilters() }
                                )
                            }

                            // Panel expandible
                            item {
                                AnimatedVisibility(
                                    visible = uiState.showFilters,
                                    enter   = expandVertically(),
                                    exit    = shrinkVertically()
                                ) {
                                    FilterPanel(
                                        filters         = uiState.filters,
                                        onFiltersChange = { viewModel.updateFilters(it) },
                                        onClear         = { viewModel.clearFilters() }
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = stringResource(R.string.history_result_count, uiState.filteredPurchases.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (uiState.filteredPurchases.isEmpty()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Default.SearchOff,
                                        message = stringResource(R.string.history_empty),
                                        modifier = Modifier.padding(vertical = 48.dp)
                                    )
                                }
                            } else {
                                items(
                                    items = uiState.filteredPurchases,
                                    key   = { it.id }
                                ) { purchase ->
                                    SwipeablePurchaseRow(
                                        purchase      = purchase,
                                        dateFormatter = dateFormatter,
                                        timeFormatter = timeFormatter,
                                        moneyFormat   = moneyFormat,
                                        onDelete      = { viewModel.deletePurchase(purchase.id) },
                                        onClick       = { onNavigateToPurchaseDetail(purchase.id) }
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

// ── Campo de búsqueda ─────────────────────────────────────────────────────────

@Composable
private fun HistorySearchField(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = {
            Text(
                stringResource(R.string.history_search),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotBlank()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.large
    )
}

// ── Chips de filtro por supermercado ──────────────────────────────────────────

@Composable
private fun HistoryFilterChipsRow(
    filterOptions: List<String>,
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(filterOptions) { option ->
            FilterChip(
                selected = selectedFilter == option,
                onClick  = { onFilterChange(option) },
                label    = {
                    Text(
                        text = option, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, softWrap = false
                    )
                }
            )
        }
    }
}

// ── Botón para mostrar/ocultar filtros avanzados ──────────────────────────────

@Composable
private fun AdvancedFiltersToggleRow(
    activeCount: Int,
    onToggle: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onToggle) {
            Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (activeCount > 0) stringResource(R.string.history_filters_count, activeCount)
                else stringResource(R.string.history_advanced_filters)
            )
        }
        if (activeCount > 0) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Item de compra con swipe-to-delete ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeablePurchaseRow(
    purchase: com.undef.superahorroturina.model.Purchase,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    moneyFormat: java.text.NumberFormat,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(200),
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        PurchaseCard(
            supermarket  = purchase.supermarket,
            date         = purchase.date.format(dateFormatter),
            time         = purchase.time.format(timeFormatter),
            total        = "$ ${moneyFormat.format(purchase.total)}",
            productCount = purchase.displayProductCount,
            onClick      = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    filters: PurchaseFilters,
    onFiltersChange: (PurchaseFilters) -> Unit,
    onClear: () -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_advanced_filters), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (filters.activeCount > 0)
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
            }

            OutlinedTextField(
                value         = filters.supermarket,
                onValueChange = { onFiltersChange(filters.copy(supermarket = it)) },
                label         = { Text(stringResource(R.string.field_supermarket)) },
                leadingIcon   = { Icon(Icons.Default.Store, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = MaterialTheme.shapes.medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val disabledColors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor      = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = filters.dateFrom?.toString() ?: "", onValueChange = {},
                    label = { Text(stringResource(R.string.history_date_from)) }, leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                    readOnly = true, enabled = false, colors = disabledColors,
                    modifier = Modifier.weight(1f).clickable { showFromPicker = true },
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = filters.dateTo?.toString() ?: "", onValueChange = {},
                    label = { Text(stringResource(R.string.history_date_to)) }, leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                    readOnly = true, enabled = false, colors = disabledColors,
                    modifier = Modifier.weight(1f).clickable { showToPicker = true },
                    shape = MaterialTheme.shapes.medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.minAmount?.toInt()?.toString() ?: "",
                    onValueChange = { onFiltersChange(filters.copy(minAmount = it.toDoubleOrNull())) },
                    label = { Text(stringResource(R.string.history_amount_min)) }, leadingIcon = { Icon(Icons.Default.MonetizationOn, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = filters.maxAmount?.toInt()?.toString() ?: "",
                    onValueChange = { onFiltersChange(filters.copy(maxAmount = it.toDoubleOrNull())) },
                    label = { Text(stringResource(R.string.history_amount_max)) }, leadingIcon = { Icon(Icons.Default.MonetizationOn, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium
                )
            }
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filters.dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onFiltersChange(filters.copy(dateFrom = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                    showFromPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filters.dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onFiltersChange(filters.copy(dateTo = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                    showToPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}
