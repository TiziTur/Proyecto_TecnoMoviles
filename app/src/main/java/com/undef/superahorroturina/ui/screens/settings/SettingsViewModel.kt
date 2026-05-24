package com.undef.superahorroturina.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.state.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel para Settings. darkMode se persiste en ThemeDataStore (DataStore Preferences)
// y se lee en MainActivity para aplicar el tema globalmente.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Cargar ambas preferencias guardadas al abrir Settings
        viewModelScope.launch {
            themeDataStore.isDarkMode.collect { saved ->
                _uiState.value = _uiState.value.copy(darkMode = saved)
            }
        }
        viewModelScope.launch {
            themeDataStore.monthlyLimit.collect { saved ->
                _uiState.value = _uiState.value.copy(monthlyLimit = saved)
            }
        }
    }

    fun onDarkModeChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = enabled)
        viewModelScope.launch { themeDataStore.setDarkMode(enabled) }
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
        viewModelScope.launch { themeDataStore.setMonthlyLimit(limit) }
    }
}
