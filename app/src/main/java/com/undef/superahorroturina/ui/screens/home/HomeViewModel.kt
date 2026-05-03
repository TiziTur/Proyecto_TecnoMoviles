package com.undef.superahorroturina.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ─────────────────────────────────────────────────

data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val totalThisMonth: Double = 0.0,
    val recentPurchases: List<Purchase> = emptyList(),
    val purchaseCount: Int = 0,
    val supermarketCount: Int = 0
)

// ── ViewModel ────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Simula una carga asincrónica (p.ej. llamada a red o BD)
            delay(300L)

            _uiState.value = HomeUiState(
                isLoading        = false,
                userName         = MockData.currentUser.firstName,
                totalThisMonth   = MockData.totalThisMonth,
                recentPurchases  = MockData.recentPurchases,
                purchaseCount    = MockData.recentPurchases.size,
                supermarketCount = MockData.purchases.map { it.supermarket }.distinct().size
            )
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadData()
    }
}
