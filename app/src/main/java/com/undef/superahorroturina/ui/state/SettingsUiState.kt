package com.undef.superahorroturina.ui.state

data class SettingsUiState(
    val darkMode: Boolean = false,
    val notifications: Boolean = true,
    val priceAlerts: Boolean = true,
    val language: String = "Español",
    val languageExpanded: Boolean = false,
    val selectedSort: String = "Más reciente",
    val monthlyLimit: Float = 50000f,
    val biometricEnabled: Boolean = false
)
