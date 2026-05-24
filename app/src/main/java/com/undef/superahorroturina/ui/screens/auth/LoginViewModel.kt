// ViewModel para Login conectado al AuthRepository real.
// v2: detecta sesión guardada para ofrecer autenticación biométrica.
package com.undef.superahorroturina.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.AuthRepository
import com.undef.superahorroturina.ui.state.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Nombre del usuario guardado en sesión (para mostrar "Bienvenido de vuelta, Juan")
    private val _savedUserName = MutableStateFlow("")
    val savedUserName: StateFlow<String> = _savedUserName.asStateFlow()

    private val _hasSavedSession = MutableStateFlow(false)
    val hasSavedSession: StateFlow<Boolean> = _hasSavedSession.asStateFlow()

    init {
        // Verificar si hay una sesión JWT válida guardada en DataStore
        viewModelScope.launch {
            val session = authRepository.getSessionFlow().first()
            if (session.token.isNotBlank()) {
                _hasSavedSession.value = true
                _savedUserName.value   = session.firstName
            }
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
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, apiError = result.message)
                }
            }
        }
    }

    // Llamado cuando la biometría autentica al usuario exitosamente.
    // El token ya está en DataStore — solo navegamos al Home.
    fun onBiometricSuccess(onSuccess: () -> Unit) {
        onSuccess()
    }
}
