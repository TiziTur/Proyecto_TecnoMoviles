// Pantalla de estadísticas conectada al StatsViewModel (datos reales del backend).
// Organizada en 4 pestañas: General, Presupuesto, Supermercados y Productos.
package com.undef.superahorroturina.ui.screens.stats

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.dotPatternBackground

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState     = viewModel.uiState.collectAsStateWithLifecycle().value
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR")) }

    val chartColors = listOf(
        Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6)
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.stats_tab_general),
        stringResource(R.string.stats_tab_budget),
        stringResource(R.string.stats_tab_supermarkets),
        stringResource(R.string.stats_tab_products)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_stats),
                showBack = true,
                onBack = onNavigateBack
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
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> StatsGeneralTab(uiState, moneyFormat, chartColors, isDark)
                    1 -> StatsBudgetTab(uiState, moneyFormat, isDark)
                    2 -> StatsSupermarketsTab(uiState, moneyFormat, chartColors, isDark)
                    3 -> StatsProductsTab(uiState, moneyFormat, chartColors, isDark)
                }
            }
        }
    }
}
