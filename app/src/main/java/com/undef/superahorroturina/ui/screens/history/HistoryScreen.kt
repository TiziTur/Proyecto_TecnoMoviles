// Pantalla de historial de compras conectada al backend real.
// Los chips de filtro se generan desde los supermercados de las compras cargadas.
// El filtrado es reactivo: searchQuery y selectedFilter se manejan local (solo UI).
package com.undef.superahorroturina.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPurchaseDetail: (Int) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val moneyFormat   = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))

    var searchQuery    by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }

    val filterOptions = listOf("Todos") + uiState.purchases
        .map { it.supermarket }
        .distinct()
        .sorted()

    val filtered = uiState.purchases.filter { purchase ->
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
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = {
                                Text(stringResource(R.string.history_search),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                                        Text(text = option, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, softWrap = false)
                                    }
                                )
                            }
                        }
                    }

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
    }
}
