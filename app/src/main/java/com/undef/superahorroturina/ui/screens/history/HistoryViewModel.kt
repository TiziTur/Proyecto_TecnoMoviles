// ViewModel para el historial de compras.
// Carga todas las compras del usuario desde el backend.
// v2: searchQuery y selectedFilter viven aquí con debounce(300ms) para no filtrar
// en cada keystroke sino cuando el usuario para de escribir.
package com.undef.superahorroturina.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val purchases: List<Purchase> = emptyList(),
    val filteredPurchases: List<Purchase> = emptyList(),
    val error: String = ""
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // Estados de búsqueda y filtro — viven en el ViewModel, no en la UI
    val searchQuery    = MutableStateFlow("")
    val selectedFilter = MutableStateFlow("Todos")

    init {
        loadPurchases()
        // Combinar los tres flows con debounce para filtrado reactivo eficiente
        combine(
            _uiState.map { it.purchases },
            searchQuery.debounce(300),
            selectedFilter
        ) { purchases, query, filter ->
            purchases.filter { purchase ->
                val matchesSearch = query.isBlank() ||
                    purchase.supermarket.contains(query, ignoreCase = true)
                val matchesFilter = filter == "Todos" || purchase.supermarket == filter
                matchesSearch && matchesFilter
            }
        }
            .onEach { filtered ->
                _uiState.value = _uiState.value.copy(filteredPurchases = filtered)
            }
            .launchIn(viewModelScope)
    }

    fun loadPurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = purchaseRepository.getPurchases()) {
                is ApiResult.Success -> {
                    val sorted = result.data.sortedByDescending { it.date }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        purchases = sorted,
                        error = ""
                    )
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = result.message
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadPurchases()
    }

    fun deletePurchase(purchaseId: Int) {
        viewModelScope.launch {
            purchaseRepository.deletePurchase(purchaseId)
            // Recargar lista tras eliminar
            loadPurchases()
        }
    }
}
