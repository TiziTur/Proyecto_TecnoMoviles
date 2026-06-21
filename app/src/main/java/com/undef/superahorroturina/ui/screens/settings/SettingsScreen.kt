package com.undef.superahorroturina.ui.screens.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.biometric.canUseBiometric
import com.undef.superahorroturina.ui.biometric.showBiometricPrompt
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark      = isSystemInDarkTheme()
    val languages   = listOf("Español", "English")
    val sortOptions = listOf(
        stringResource(R.string.settings_sort_newest),
        stringResource(R.string.settings_sort_oldest),
        stringResource(R.string.settings_sort_highest)
    )
    val activity = LocalContext.current as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val biometricAvailable = activity != null && canUseBiometric(activity)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = stringResource(R.string.screen_settings),
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
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                SettingsCard(isDark = isDark) {
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
                        icon     = Icons.Default.Language,
                        title    = stringResource(R.string.settings_language),
                        subtitle = uiState.language,
                        onClick  = { viewModel.onLanguageExpandedChange(true) }
                    )

                    DropdownMenu(
                        expanded        = uiState.languageExpanded,
                        onDismissRequest = { viewModel.onLanguageExpandedChange(false) }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang) },
                                onClick = { viewModel.onLanguageChange(lang) },
                                leadingIcon = {
                                    if (uiState.language == lang)
                                        Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                        }
                    }
                }

                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader("Seguridad")

                    SettingsToggleItem(
                        icon            = Icons.Default.Fingerprint,
                        title           = "Inicio con huella",
                        subtitle        = if (biometricAvailable)
                            "Usá tu huella o PIN para entrar más rápido"
                        else
                            "Tu dispositivo no tiene biometría ni PIN configurado",
                        checked         = uiState.biometricEnabled && biometricAvailable,
                        onCheckedChange = { enabled ->
                            if (biometricAvailable) {
                                if (!enabled) {
                                    viewModel.onBiometricDisabled()
                                } else {
                                    coroutineScope.launch {
                                        val cipher = viewModel.prepareBiometricEnrollCipher()
                                        if (cipher != null && activity != null) {
                                            showBiometricPrompt(
                                                activity     = activity,
                                                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                                                title        = "Activar huella",
                                                subtitle     = "Confirmá tu huella para activar el acceso rápido",
                                                onSuccess    = { result ->
                                                    viewModel.onBiometricEnrollConfirmed(result.cryptoObject!!.cipher!!)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_notifications))

                    SettingsToggleItem(
                        icon            = Icons.Default.Notifications,
                        title           = stringResource(R.string.settings_notifications_label),
                        subtitle        = stringResource(R.string.settings_notifications_desc),
                        checked         = uiState.notifications,
                        onCheckedChange = { viewModel.onNotificationsChange(it) }
                    )

                    SettingsToggleItem(
                        icon            = Icons.Default.NotificationsActive,
                        title           = stringResource(R.string.settings_price_alerts),
                        subtitle        = stringResource(R.string.settings_price_alerts_desc),
                        checked         = uiState.priceAlerts,
                        onCheckedChange = { viewModel.onPriceAlertsChange(it) }
                    )
                }

                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_budget))
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.settings_budget_limit),
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "$ ${uiState.monthlyLimit.toInt()}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value        = uiState.monthlyLimit,
                            onValueChange = { viewModel.onMonthlyLimitChange(it) },
                            valueRange   = 10000f..200000f,
                            steps        = 18,
                            modifier     = Modifier.fillMaxWidth()
                        )
                    }
                }

                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_sort_history))
                    sortOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = uiState.selectedSort == option,
                                onClick  = { viewModel.onSortChange(option) }
                            )
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader(stringResource(R.string.settings_info))
                    SettingsSelectorItem(
                        icon     = Icons.Default.Info,
                        title    = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_version),
                        onClick  = {}
                    )
                    SettingsSelectorItem(
                        icon     = Icons.Default.PrivacyTip,
                        title    = stringResource(R.string.settings_privacy),
                        subtitle = "",
                        onClick  = {}
                    )
                    SettingsSelectorItem(
                        icon     = Icons.Default.Description,
                        title    = stringResource(R.string.settings_terms),
                        subtitle = "",
                        onClick  = {}
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color        = MaterialTheme.colorScheme.primary,
                borderRadius = 16.dp,
                blurRadius   = 10.dp,
                offsetY      = 2.dp
            )
            .glowBorder(cornerRadius = 16.dp, isDark = isDark),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
