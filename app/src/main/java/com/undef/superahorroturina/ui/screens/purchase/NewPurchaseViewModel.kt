package com.undef.superahorroturina.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.state.NewPurchaseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewPurchaseViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(NewPurchaseUiState())
    val uiState: StateFlow<NewPurchaseUiState> = _uiState.asStateFlow()

    // Carga datos si se está editando una compra existente
    fun loadPurchase(purchaseId: Int?) {
        if (purchaseId == null) return
        val existing = MockData.purchases.find { it.id == purchaseId } ?: return
        _uiState.value = NewPurchaseUiState(
            supermarket = existing.supermarket,
            date        = existing.date.toString(),
            time        = existing.time.toString(),
            products    = existing.products
        )
    }

    fun onSupermarketChange(value: String) {
        _uiState.value = _uiState.value.copy(supermarket = value)
    }

    fun onDateChange(value: String) {
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun onTimeChange(value: String) {
        _uiState.value = _uiState.value.copy(time = value)
    }

    fun onDropdownExpandedChange(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(dropdownExpanded = expanded)
    }

    fun onSave(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            delay(300L) // TODO: reemplazar con llamada real a PurchaseRepository
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSuccess()
        }
    }
}
