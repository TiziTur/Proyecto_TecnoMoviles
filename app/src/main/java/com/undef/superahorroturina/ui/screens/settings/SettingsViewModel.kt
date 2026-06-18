package com.undef.superahorroturina.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.state.SettingsUiState
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { themeDataStore.isDarkMode.collect { _uiState.value = _uiState.value.copy(darkMode = it) } }
        viewModelScope.launch { themeDataStore.monthlyLimit.collect { _uiState.value = _uiState.value.copy(monthlyLimit = it) } }
        viewModelScope.launch { themeDataStore.priceAlertsEnabled.collect { _uiState.value = _uiState.value.copy(priceAlerts = it) } }
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
        viewModelScope.launch {
            themeDataStore.setPriceAlertsEnabled(enabled)
            if (enabled) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PriceAlertWorker.WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
                        .addTag(PriceAlertWorker.WORK_TAG)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(PriceAlertWorker.WORK_TAG)
            }
        }
    }

    fun onLanguageChange(language: String) { _uiState.value = _uiState.value.copy(language = language, languageExpanded = false) }
    fun onLanguageExpandedChange(expanded: Boolean) { _uiState.value = _uiState.value.copy(languageExpanded = expanded) }
    fun onSortChange(sort: String) { _uiState.value = _uiState.value.copy(selectedSort = sort) }
    fun onMonthlyLimitChange(limit: Float) {
        _uiState.value = _uiState.value.copy(monthlyLimit = limit)
        viewModelScope.launch { themeDataStore.setMonthlyLimit(limit) }
    }
}
