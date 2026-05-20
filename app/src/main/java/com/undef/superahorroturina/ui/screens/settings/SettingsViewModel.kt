package com.undef.superahorroturina.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.undef.superahorroturina.ui.state.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// ViewModel para Settings. Expone todo el estado de la pantalla como StateFlow,
// sacando los remember/mutableStateOf del composable — antipatrón señalado por el profesor.
// En la segunda entrega, darkMode y language se persistirán con DataStore.
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onDarkModeChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = enabled)
    }

    fun onNotificationsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifications = enabled)
    }

    fun onPriceAlertsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(priceAlerts = enabled)
    }

    fun onLanguageChange(language: String) {
        _uiState.value = _uiState.value.copy(language = language, languageExpanded = false)
    }

    fun onLanguageExpandedChange(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(languageExpanded = expanded)
    }

    fun onSortChange(sort: String) {
        _uiState.value = _uiState.value.copy(selectedSort = sort)
    }

    fun onMonthlyLimitChange(limit: Float) {
        _uiState.value = _uiState.value.copy(monthlyLimit = limit)
    }
}
