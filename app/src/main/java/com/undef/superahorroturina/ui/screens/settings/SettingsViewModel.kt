package com.undef.superahorroturina.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
import com.undef.superahorroturina.ui.state.SettingsUiState
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    private val biometricCryptoManager: BiometricCryptoManager,
    private val sessionDataStore: SessionDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { themeDataStore.isDarkMode.collect { _uiState.value = _uiState.value.copy(darkMode = it) } }
        viewModelScope.launch { themeDataStore.monthlyLimit.collect { _uiState.value = _uiState.value.copy(monthlyLimit = it) } }
        viewModelScope.launch { themeDataStore.priceAlertsEnabled.collect { _uiState.value = _uiState.value.copy(priceAlerts = it) } }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(biometricEnabled = biometricCryptoManager.isBiometricEnabled())
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

    // Apagar no necesita huella: solo borra lo guardado.
    fun onBiometricDisabled() {
        viewModelScope.launch {
            biometricCryptoManager.disableBiometricLogin()
            _uiState.value = _uiState.value.copy(biometricEnabled = false)
        }
    }

    // Prender necesita un Cipher autenticado por BiometricPrompt — la Composable se
    // encarga de mostrar el prompt (necesita la Activity) y llama a estas dos
    // funciones para preparar y luego confirmar el cifrado.
    suspend fun prepareBiometricEnrollCipher(): Cipher? =
        (biometricCryptoManager.createEncryptCipherResult() as? BiometricCryptoManager.CipherResult.Ready)?.cipher

    fun onBiometricEnrollConfirmed(cipher: Cipher) {
        viewModelScope.launch {
            val token = sessionDataStore.session.first().token
            if (token.isNotBlank()) {
                biometricCryptoManager.saveEncryptedToken(cipher, token)
                _uiState.value = _uiState.value.copy(biometricEnabled = true)
            }
        }
    }
}
