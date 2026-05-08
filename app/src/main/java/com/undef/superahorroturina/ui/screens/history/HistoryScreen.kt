// Pantalla de historial de compras con búsqueda y filtros por supermercado.
// Los chips de filtro están en LazyRow para que nunca se corten ni envuelvan en
// múltiples líneas, sin importar cuántos supermercados haya.
// El filtrado es reactivo: cada vez que cambia searchQuery o selectedFilter,
// Compose recompone automáticamente la lista — no necesito llamar a ninguna función.
package com.undef.superahorroturina.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPurchaseDetail: (Int) -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val moneyFormat   = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))

    var searchQuery    by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }
    val filterOptions  = listOf("Todos") + MockData.supermarkets.filter { market ->
        MockData.purchases.any { it.supermarket == market }
    }

    val filtered = MockData.purchases
        .sortedByDescending { it.date }
        .filter { purchase ->
            val matchesSearch = searchQuery.isBlank() ||
                purchase.supermarket.contains(searchQuery, ignoreCase = true)
            val matchesFilter = selectedFilter == "Todos" || purchase.supermarket == selectedFilter
            matchesSearch && matchesFilter
        }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_history),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Search bar — singleLine garantiza 1 renglón
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = {
                        Text(
                            stringResource(R.string.history_search),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Filter chips en LazyRow → nunca cortan ni envuelven
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(filterOptions) { option ->
                        FilterChip(
                            selected = selectedFilter == option,
                            onClick  = { selectedFilter = option },
                            label    = {
                                Text(
                                    text = option,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }
            }

            // Result count
            item {
                Text(
                    text = stringResource(R.string.history_result_count, filtered.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        message = stringResource(R.string.history_empty),
                        modifier = Modifier.padding(vertical = 48.dp)
                    )
                }
            } else {
                items(filtered) { purchase ->
                    PurchaseCard(
                        supermarket  = purchase.supermarket,
                        date         = purchase.date.format(dateFormatter),
                        time         = purchase.time.format(timeFormatter),
                        total        = "$ ${moneyFormat.format(purchase.total)}",
                        productCount = purchase.products.size,
                        onClick      = { onNavigateToPurchaseDetail(purchase.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
