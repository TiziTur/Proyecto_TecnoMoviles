// Pantalla principal de la app. Conecta con HomeViewModel usando hiltViewModel()
// y observa el estado con collectAsStateWithLifecycle (más eficiente que collectAsState
// porque para la colección cuando la app va a background).
// El drawer lateral se abre/cierra con un CoroutineScope + drawerState — necesito el
// scope porque drawerState.open() es una suspend function.
package com.undef.superahorroturina.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.*
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNewPurchase: () -> Unit,
    onNavigateToPurchaseDetail: (Int) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: () -> Unit = {},
    onNavigateToPriceComparison: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark  = isSystemInDarkTheme()

    // Recarga cada vez que el usuario vuelve a Home (tras crear compra, etc.)
    LifecycleResumeEffect(Unit) {
        viewModel.loadData()
        onPauseOrDispose { }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val moneyFormat   = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    // Animación del total (cuenta desde 0)
    var totalTarget by remember { mutableStateOf(0.0) }
    LaunchedEffect(uiState.totalThisMonth) { totalTarget = uiState.totalThisMonth }
    val animatedTotal by animateFloatAsState(
        targetValue    = totalTarget.toFloat(),
        animationSpec  = tween(durationMillis = 900),
        label          = "totalAnim"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = uiState.userName,
                isDark   = isDark,
                onNavigateToProfile = {
                    scope.launch { drawerState.close() }
                    onNavigateToProfile()
                },
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onNavigateToChat = {
                    scope.launch { drawerState.close() }
                    onNavigateToChat()
                },
                onNavigateToPriceComparison = {
                    scope.launch { drawerState.close() }
                    onNavigateToPriceComparison()
                },
                onLogout = onLogout
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.screen_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
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
                // FAB con gradiente premium
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 16.dp,
                            blurRadius   = 18.dp,
                            offsetY      = 4.dp
                        )
                ) {
                    ExtendedFloatingActionButton(
                        onClick           = onNavigateToNewPurchase,
                        icon              = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                        text              = { Text(stringResource(R.string.action_new_purchase), color = Color.White, fontWeight = FontWeight.SemiBold) },
                        containerColor    = Color.Transparent,
                        contentColor      = Color.White,
                        elevation         = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    )
                }
            }
        ) { padding ->
            // Fondo con textura de puntos
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
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        // Welcome header
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text  = stringResource(R.string.home_welcome, uiState.userName),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text  = stringResource(R.string.home_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Summary card — glassmorphism simulado con gradiente profundo
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .coloredShadow(
                                        color        = MaterialTheme.colorScheme.primary,
                                        borderRadius = 24.dp,
                                        blurRadius   = 24.dp,
                                        offsetY      = 8.dp
                                    )
                                    .background(
                                        Brush.linearGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color(if (isDark) 0xFF1E3A8A else 0xFF2563EB),
                                                0.6f to Color(if (isDark) 0xFF164E63 else 0xFF0E7490),
                                                1.0f to Color(if (isDark) 0xFF065F46 else 0xFF065F46)
                                            )
                                        )
                                    )
                                    .glowBorder(cornerRadius = 24.dp, isDark = isDark)
                            ) {
                                Column(modifier = Modifier.padding(22.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text  = stringResource(R.string.home_month_total),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text  = "Este mes",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text  = "$ ${moneyFormat.format(animatedTotal)}",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )

                                    // ── Barra de presupuesto mensual ──────────────
                                    val budgetProgress = (uiState.totalThisMonth / uiState.monthlyLimit)
                                        .toFloat().coerceIn(0f, 1f)
                                    val budgetColor = when {
                                        budgetProgress >= 1f  -> Color(0xFFEF4444) // rojo
                                        budgetProgress >= 0.8f -> Color(0xFFF59E0B) // amarillo
                                        else -> Color.White.copy(alpha = 0.85f)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text  = "Presupuesto mensual",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text  = "${(budgetProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = budgetColor
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    com.undef.superahorroturina.ui.screens.stats.SegmentBar(
                                        progress   = budgetProgress,
                                        color      = budgetColor,
                                        height     = 6.dp,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text  = "$ ${moneyFormat.format(uiState.totalThisMonth.toLong())} de $ ${moneyFormat.format(uiState.monthlyLimit.toLong())}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )

                                    Spacer(Modifier.height(16.dp))
                                    GradientDivider(color = Color.White.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        StatCard(
                                            label          = stringResource(R.string.stat_purchases),
                                            value          = uiState.purchaseCount.toString(),
                                            icon           = Icons.Default.ShoppingCart,
                                            modifier       = Modifier.weight(1f),
                                            containerColor = Color.White.copy(alpha = 0.14f),
                                            contentColor   = Color.White,
                                            accentColor    = Color.White
                                        )
                                        StatCard(
                                            label          = stringResource(R.string.stat_supermarkets),
                                            value          = uiState.supermarketCount.toString(),
                                            icon           = Icons.Default.Store,
                                            modifier       = Modifier.weight(1f),
                                            containerColor = Color.White.copy(alpha = 0.14f),
                                            contentColor   = Color.White,
                                            accentColor    = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // ── Funciones IA ──────────────────────────────
                        item {
                            SectionHeader(
                                title       = "Herramientas IA",
                                actionLabel = null
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Chat IA card
                                AiFeatureCard(
                                    modifier  = Modifier.weight(1f),
                                    icon      = Icons.Default.AutoAwesome,
                                    title     = "Asistente IA",
                                    subtitle  = "Consultá tu historial",
                                    gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)),
                                    onClick   = onNavigateToChat
                                )
                                // Comparativa card
                                AiFeatureCard(
                                    modifier  = Modifier.weight(1f),
                                    icon      = Icons.Default.CompareArrows,
                                    title     = "Comparativa",
                                    subtitle  = "Precios por super",
                                    gradientColors = listOf(Color(0xFF059669), Color(0xFF0E7490)),
                                    onClick   = onNavigateToPriceComparison
                                )
                            }
                        }

                        item {
                            CheapestSummaryCard(
                                loading  = uiState.cheapestSummaryLoading,
                                error    = uiState.cheapestSummaryError,
                                headline = uiState.cheapestSummary?.headline,
                                onClick  = onNavigateToPriceComparison
                            )
                        }

                        // Recent purchases section
                        item {
                            SectionHeader(
                                title       = stringResource(R.string.home_recent),
                                actionLabel = stringResource(R.string.action_see_all),
                                onAction    = onNavigateToHistory
                            )
                        }

                        if (uiState.recentPurchases.isEmpty()) {
                            item {
                                EmptyState(
                                    icon     = Icons.Default.ShoppingBag,
                                    message  = stringResource(R.string.home_empty),
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
                                    productCount = purchase.displayProductCount,
                                    onClick      = { onNavigateToPurchaseDetail(purchase.id) }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                    } // PullToRefreshBox
                }
            }
        }
    }
}

// ── Drawer ────────────────────────────────────────────────────

@Composable
private fun AppDrawerContent(
    userName: String,
    isDark: Boolean,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToPriceComparison: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(Modifier.height(24.dp))

            // Header del drawer con gradiente
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KlarityLogoIcon(size = 40)
                    Column {
                        Text(
                            text  = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text  = userName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GradientDivider(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon     = { Icon(Icons.Default.Person, contentDescription = null) },
                label    = { Text(stringResource(R.string.screen_profile)) },
                selected = false,
                onClick  = onNavigateToProfile
            )
            NavigationDrawerItem(
                icon     = { Icon(Icons.Default.Settings, contentDescription = null) },
                label    = { Text(stringResource(R.string.screen_settings)) },
                selected = false,
                onClick  = onNavigateToSettings
            )
            Spacer(Modifier.height(8.dp))
            GradientDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            // ── Herramientas IA ────────────────────────────────
            NavigationDrawerItem(
                icon     = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF3B82F6)) },
                label    = { Text("Asistente IA") },
                selected = false,
                onClick  = onNavigateToChat
            )
            NavigationDrawerItem(
                icon     = { Icon(Icons.Default.CompareArrows, contentDescription = null, tint = Color(0xFF059669)) },
                label    = { Text("Comparativa de precios") },
                selected = false,
                onClick  = onNavigateToPriceComparison
            )
            Spacer(Modifier.height(8.dp))
            GradientDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                icon     = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                label    = { Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error) },
                selected = false,
                onClick  = onLogout
            )
        }
    }
}

// ── CheapestSummaryCard ──────────────────────────────────────

@Composable
private fun CheapestSummaryCard(
    loading: Boolean,
    error: Boolean,
    headline: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TipsAndUpdates,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = "¿Dónde conviene comprar este mes?",
                    style = MaterialTheme.typography.titleSmall
                )
                when {
                    loading -> Text(
                        text  = "Calculando…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    error || headline.isNullOrBlank() -> Text(
                        text  = "Aún no hay suficientes datos para calcularlo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        text     = headline,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

// ── AiFeatureCard ─────────────────────────────────────────────

@Composable
private fun AiFeatureCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradientColors))
            .coloredShadow(
                color        = gradientColors.first(),
                borderRadius = 20.dp,
                blurRadius   = 14.dp,
                offsetY      = 3.dp
            )
    ) {
        Surface(
            onClick = onClick,
            color   = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
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
            onNavigateToChat           = {},
            onNavigateToPriceComparison = {},
            onLogout                   = {}
        )
    }
}
