// ViewModel para el historial de compras.
// Carga todas las compras del usuario desde el backend.
package com.undef.superahorroturina.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val purchases: List<Purchase> = emptyList(),
    val error: String = ""
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { loadPurchases() }

    fun loadPurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = purchaseRepository.getPurchases()) {
                is ApiResult.Success -> _uiState.value = HistoryUiState(
                    isLoading = false,
                    purchases = result.data.sortedByDescending { it.date }
                )
                is ApiResult.Error   -> _uiState.value = HistoryUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }
}
