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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    var darkMode      by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    var language      by remember { mutableStateOf("Español") }
    var langExpanded  by remember { mutableStateOf(false) }
    val languages     = listOf("Español", "English")

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
                icon    = Icons.Default.DarkMode,
                title   = stringResource(R.string.settings_dark_mode),
                subtitle = stringResource(R.string.settings_dark_mode_desc),
                checked  = darkMode,
                onCheckedChange = { darkMode = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Language selector
            SettingsSelectorItem(
                icon     = Icons.Default.Language,
                title    = stringResource(R.string.settings_language),
                subtitle = language,
                onClick  = { langExpanded = true }
            )

            DropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = { language = lang; langExpanded = false },
                        leadingIcon = {
                            if (language == lang) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Notificaciones ────────────────────────────────────
            SettingsCategoryHeader(stringResource(R.string.settings_notifications))

            SettingsToggleItem(
                icon     = Icons.Default.Notifications,
                title    = stringResource(R.string.settings_notifications_label),
                subtitle = stringResource(R.string.settings_notifications_desc),
                checked  = notifications,
                onCheckedChange = { notifications = it }
            )

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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSelectorItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
