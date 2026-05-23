// ViewModel de registro conectado al AuthRepository real.
package com.undef.superahorroturina.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.AuthRepository
import com.undef.superahorroturina.ui.state.RegisterUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String)        { _uiState.value = _uiState.value.copy(firstName = value, apiError = "") }
    fun onLastNameChange(value: String)         { _uiState.value = _uiState.value.copy(lastName = value, apiError = "") }
    fun onEmailChange(value: String)            { _uiState.value = _uiState.value.copy(email = value, apiError = "") }
    fun onPhoneChange(value: String)            { _uiState.value = _uiState.value.copy(phone = value) }
    fun onPasswordChange(value: String)         { _uiState.value = _uiState.value.copy(password = value, passwordError = false) }
    fun onConfirmPasswordChange(value: String)  { _uiState.value = _uiState.value.copy(confirmPassword = value, passwordError = false) }
    fun onTogglePasswordVisibility()            { _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword) }

    fun onRegister(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.email.isBlank()) return
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(passwordError = true)
            return
        }
        if (state.password.length < 6) {
            _uiState.value = state.copy(passwordError = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, apiError = "")
            when (val result = authRepository.register(
                state.firstName, state.lastName, state.email, state.password, state.phone
            )) {
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
}
