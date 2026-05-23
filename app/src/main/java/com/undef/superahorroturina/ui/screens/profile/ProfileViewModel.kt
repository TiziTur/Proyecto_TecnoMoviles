// ViewModel de perfil conectado al AuthRepository real.
// Carga los datos del usuario desde DataStore (sin llamada extra de red).
// Al guardar, llama a la API para actualizar el perfil.
package com.undef.superahorroturina.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.AuthRepository
import com.undef.superahorroturina.ui.state.ProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Cargar datos del usuario desde DataStore (ya almacenados al hacer login)
        viewModelScope.launch {
            val session = authRepository.getSessionFlow().first()
            _uiState.value = ProfileUiState(
                firstName = session.firstName,
                lastName  = session.lastName,
                email     = session.email,
                phone     = session.phone
            )
        }
    }

    fun onFirstNameChange(value: String) { _uiState.value = _uiState.value.copy(firstName = value) }
    fun onLastNameChange(value: String)  { _uiState.value = _uiState.value.copy(lastName = value) }
    fun onEmailChange(value: String)     { _uiState.value = _uiState.value.copy(email = value) }
    fun onPhoneChange(value: String)     { _uiState.value = _uiState.value.copy(phone = value) }
    fun onToggleEditing()                { _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing, saveError = "") }

    fun onSave() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saveError = "")
            when (val result = authRepository.updateProfile(
                state.firstName, state.lastName, state.email, state.phone
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isEditing = false)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveError = result.message)
                }
            }
        }
    }
}
