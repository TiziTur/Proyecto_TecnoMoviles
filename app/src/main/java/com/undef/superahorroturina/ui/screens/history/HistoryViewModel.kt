package com.undef.superahorroturina.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val searchQuery    = MutableStateFlow("")
    val selectedFilter = MutableStateFlow("Todos")

    init {
        viewModelScope.launch {
            purchaseRepository.getPurchasesFlow().collect { purchases ->
                val sorted = purchases.sortedByDescending { it.date }
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    isRefreshing = false,
                    purchases    = sorted,
                    error        = ""
                )
            }
        }
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

        loadPurchases()
    }

    fun loadPurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            purchaseRepository.refreshPurchases()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            purchaseRepository.refreshPurchases()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun deletePurchase(purchaseId: Int) {
        viewModelScope.launch {
            purchaseRepository.deletePurchase(purchaseId)
        }
    }
}
