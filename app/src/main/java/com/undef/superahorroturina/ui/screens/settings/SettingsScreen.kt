// Pantalla de configuración conectada a SettingsViewModel.
// Todo el estado (darkMode, notifications, language, sort, slider) vive en el ViewModel
// y se expone como StateFlow — corrige el antipatrón señalado por el profesor.
// En la segunda entrega, darkMode y language se persistirán con DataStore.
package com.undef.superahorroturina.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages   = listOf("Español", "English")
    val sortOptions = listOf("Más reciente", "Más antiguo", "Mayor gasto")

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.screen_settings),
                showBack = true,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Apariencia ────────────────────────────────────────
            SettingsCategoryHeader(stringResource(R.string.settings_appearance))

            SettingsToggleItem(
                icon            = Icons.Default.DarkMode,
                title           = stringResource(R.string.settings_dark_mode),
                subtitle        = stringResource(R.string.settings_dark_mode_desc),
                checked         = uiState.darkMode,
                onCheckedChange = { viewModel.onDarkModeChange(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSelectorItem(
                icon    = Icons.Default.Language,
                title   = stringResource(R.string.settings_language),
                subtitle = uiState.language,
                onClick = { viewModel.onLanguageExpandedChange(true) }
            )

            DropdownMenu(
                expanded = uiState.languageExpanded,
                onDismissRequest = { viewModel.onLanguageExpandedChange(false) }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = { viewModel.onLanguageChange(lang) },
                        leadingIcon = {
                            if (uiState.language == lang)
                                Icon(Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Notificaciones ────────────────────────────────────
            SettingsCategoryHeader(stringResource(R.string.settings_notifications))

            SettingsToggleItem(
                icon            = Icons.Default.Notifications,
                title           = stringResource(R.string.settings_notifications_label),
                subtitle        = stringResource(R.string.settings_notifications_desc),
                checked         = uiState.notifications,
                onCheckedChange = { viewModel.onNotificationsChange(it) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.priceAlerts,
                    onCheckedChange = { viewModel.onPriceAlertsChange(it) }
                )
                Column {
                    Text("Alertas de precio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Notificar cuando un producto suba de precio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Límite mensual (Slider) ───────────────────────────
            SettingsCategoryHeader("Presupuesto mensual")

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Límite de alerta", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$ ${uiState.monthlyLimit.toInt()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = uiState.monthlyLimit,
                    onValueChange = { viewModel.onMonthlyLimitChange(it) },
                    valueRange = 10000f..200000f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Ordenar historial (RadioButton) ───────────────────
            SettingsCategoryHeader("Ordenar historial")

            sortOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = uiState.selectedSort == option,
                        onClick  = { viewModel.onSortChange(option) }
                    )
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Información ───────────────────────────────────────
            SettingsCategoryHeader(stringResource(R.string.settings_info))

            SettingsSelectorItem(
                icon    = Icons.Default.Info,
                title   = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version),
                onClick = {}
            )
            SettingsSelectorItem(
                icon    = Icons.Default.PrivacyTip,
                title   = stringResource(R.string.settings_privacy),
                subtitle = "",
                onClick = {}
            )
            SettingsSelectorItem(
                icon    = Icons.Default.Description,
                title   = stringResource(R.string.settings_terms),
                subtitle = "",
                onClick = {}
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSelectorItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "Settings Screen")
@Composable
private fun SettingsScreenPreview() {
    SuperAhorroTheme(darkTheme = false) {
        SettingsScreen(onNavigateBack = {})
    }
}
