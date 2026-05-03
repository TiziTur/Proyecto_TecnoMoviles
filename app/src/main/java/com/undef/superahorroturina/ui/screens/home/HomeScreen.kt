package com.undef.superahorroturina.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.*
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNewPurchase: () -> Unit,
    onNavigateToPurchaseDetail: (Int) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val moneyFormat   = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onNavigateToProfile = {
                    scope.launch { drawerState.close() }
                    onNavigateToProfile()
                },
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onLogout = onLogout
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.action_menu)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.screen_settings)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                AppBottomNavigationBar(
                    currentRoute = "home",
                    onNavigate = { route ->
                        when (route) {
                            "history" -> onNavigateToHistory()
                            "stats"   -> onNavigateToStats()
                            "profile" -> onNavigateToProfile()
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToNewPurchase,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_new_purchase)) }
                )
            }
        ) { padding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Welcome header
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.home_welcome, uiState.userName),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(R.string.home_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Summary card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.home_month_total),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$ ${moneyFormat.format(uiState.totalThisMonth)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatCard(
                                        label = stringResource(R.string.stat_purchases),
                                        value = uiState.purchaseCount.toString(),
                                        icon = Icons.Default.ShoppingCart,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = stringResource(R.string.stat_supermarkets),
                                        value = uiState.supermarketCount.toString(),
                                        icon = Icons.Default.Store,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Recent purchases section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.home_recent),
                            actionLabel = stringResource(R.string.action_see_all),
                            onAction = onNavigateToHistory
                        )
                    }

                    if (uiState.recentPurchases.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.ShoppingBag,
                                message = stringResource(R.string.home_empty),
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                    } else {
                        items(uiState.recentPurchases) { purchase ->
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

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Drawer ────────────────────────────────────────────────────

@Composable
private fun AppDrawerContent(
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Default.ShoppingBasket,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${MockData.currentUser.firstName} ${MockData.currentUser.lastName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = MockData.currentUser.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon  = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text(stringResource(R.string.screen_profile)) },
                selected = false,
                onClick = onNavigateToProfile
            )
            NavigationDrawerItem(
                icon  = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.screen_settings)) },
                selected = false,
                onClick = onNavigateToSettings
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                icon  = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                label = { Text(stringResource(R.string.action_logout)) },
                selected = false,
                onClick = onLogout
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────

@Preview(showBackground = true, name = "Home Screen – Light")
@Composable
private fun HomeScreenPreview() {
    SuperAhorroTheme(darkTheme = false) {
        HomeScreen(
            onNavigateToNewPurchase    = {},
            onNavigateToPurchaseDetail = {},
            onNavigateToHistory        = {},
            onNavigateToStats          = {},
            onNavigateToProfile        = {},
            onNavigateToSettings       = {},
            onLogout                   = {}
        )
    }
}
