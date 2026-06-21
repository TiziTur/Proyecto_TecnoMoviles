// ViewModel para Login conectado al AuthRepository real.
// v3: el token solo se restaura en memoria tras un desbloqueo biométrico real
// (CryptoObject), no se asume válido por el solo hecho de existir.
package com.undef.superahorroturina.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.AuthRepository
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
import com.undef.superahorroturina.ui.state.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

// Estado de qué Cipher (si alguno) está listo para desbloquear con biometría al abrir la app.
sealed class BiometricUnlockState {
    object NotAvailable : BiometricUnlockState()
    data class Ready(val cipher: Cipher) : BiometricUnlockState()
    object KeyInvalidated : BiometricUnlockState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionDataStore: SessionDataStore,
    private val biometricCryptoManager: BiometricCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Nombre del usuario guardado en sesión (para mostrar "Bienvenido de vuelta, Juan")
    private val _savedUserName = MutableStateFlow("")
    val savedUserName: StateFlow<String> = _savedUserName.asStateFlow()

    // true si hay biometría activada (no implica que el token ya esté descifrado)
    private val _hasSavedSession = MutableStateFlow(false)
    val hasSavedSession: StateFlow<Boolean> = _hasSavedSession.asStateFlow()

    // Diálogo de consentimiento para activar huella, mostrado una vez tras un login con contraseña exitoso
    private val _showEnrollDialog = MutableStateFlow(false)
    val showEnrollDialog: StateFlow<Boolean> = _showEnrollDialog.asStateFlow()

    // Mensaje a mostrar si se detectó que la clave de biometría quedó invalidada
    private val _biometricMessage = MutableStateFlow("")
    val biometricMessage: StateFlow<String> = _biometricMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _hasSavedSession.value = biometricCryptoManager.isBiometricEnabled()
            _savedUserName.value   = authRepository.getSessionFlow().first().firstName
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, emailError = false, apiError = "")
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, apiError = "")
    }

    fun onTogglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword)
    }

    fun onLogin(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || !state.email.contains("@")) {
            _uiState.value = state.copy(emailError = true)
            return
        }
        if (state.password.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, apiError = "")
            when (val result = authRepository.login(state.email, state.password)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    if (biometricCryptoManager.isBiometricEnabled()) {
                        onSuccess()
                    } else {
                        // Recién logueado, sin biometría activada todavía: ofrecer activarla.
                        _showEnrollDialog.value = true
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, apiError = result.message)
                }
            }
        }
    }

    // ── Activar biometría tras login con contraseña (o el usuario la rechaza) ──────

    suspend fun prepareEnrollCipher(): Cipher? =
        (biometricCryptoManager.createEncryptCipherResult() as? BiometricCryptoManager.CipherResult.Ready)?.cipher

    fun onEnrollConfirmed(cipher: Cipher, onDone: () -> Unit) {
        viewModelScope.launch {
            val token = sessionDataStore.session.first().token
            if (token.isNotBlank()) {
                biometricCryptoManager.saveEncryptedToken(cipher, token)
                _hasSavedSession.value = true
            }
            _showEnrollDialog.value = false
            onDone()
        }
    }

    fun onEnrollDeclined(onDone: () -> Unit) {
        _showEnrollDialog.value = false
        onDone()
    }

    // ── Desbloqueo biométrico al reabrir la app con biometría ya activada ──────────

    suspend fun prepareUnlockCipher(): BiometricUnlockState {
        if (!biometricCryptoManager.isBiometricEnabled()) return BiometricUnlockState.NotAvailable
        return when (val r = biometricCryptoManager.createDecryptCipherResult()) {
            is BiometricCryptoManager.CipherResult.Ready ->
                BiometricUnlockState.Ready(r.cipher)
            is BiometricCryptoManager.CipherResult.KeyInvalidated ->
                BiometricUnlockState.KeyInvalidated
            is BiometricCryptoManager.CipherResult.NoStoredToken ->
                BiometricUnlockState.NotAvailable
        }
    }

    suspend fun onBiometricUnlockSuccess(cipher: Cipher) {
        val token = biometricCryptoManager.decryptStoredToken(cipher) ?: return
        sessionDataStore.restoreToken(token)
    }

    suspend fun onBiometricKeyInvalidated() {
        biometricCryptoManager.disableBiometricLogin()
        _hasSavedSession.value = false
        _biometricMessage.value = "Tu configuración de huella cambió, iniciá sesión de nuevo."
    }

    fun clearBiometricMessage() {
        _biometricMessage.value = ""
    }
}
