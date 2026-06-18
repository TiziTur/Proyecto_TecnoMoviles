// Pantalla de historial de compras.
// v3: searchQuery y selectedFilter viven en el ViewModel con debounce(300ms).
// Agregado pull-to-refresh (PullToRefreshBox) y swipe-to-delete (SwipeToDismissBox).
package com.undef.superahorroturina.ui.screens.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
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

    val filterOptions = listOf("Todos") + uiState.purchases
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
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
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
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.searchQuery.value = it },
                                    label = {
                                        Text(
                                            stringResource(R.string.history_search),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = null)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large
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
                                            onClick  = { viewModel.selectedFilter.value = option },
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
                                    // Swipe-to-delete
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                viewModel.deletePurchase(purchase.id)
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
                                            onClick      = { onNavigateToPurchaseDetail(purchase.id) }
                                        )
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
}
