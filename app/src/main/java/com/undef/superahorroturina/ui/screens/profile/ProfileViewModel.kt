package com.undef.superahorroturina.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.state.ProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            firstName = MockData.currentUser.firstName,
            lastName  = MockData.currentUser.lastName,
            email     = MockData.currentUser.email,
            phone     = MockData.currentUser.phone
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String) {
        _uiState.value = _uiState.value.copy(firstName = value)
    }

    fun onLastNameChange(value: String) {
        _uiState.value = _uiState.value.copy(lastName = value)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = value)
    }

    fun onToggleEditing() {
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }

    fun onSave() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            delay(300L) // TODO: reemplazar con llamada real a UserRepository
            _uiState.value = _uiState.value.copy(isSaving = false, isEditing = false)
        }
    }
}
