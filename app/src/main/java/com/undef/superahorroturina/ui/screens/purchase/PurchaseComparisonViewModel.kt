package com.undef.superahorroturina.ui.screens.purchase

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.PurchaseComparisonResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseComparisonUiState(
    val isLoading: Boolean = true,
    val data: PurchaseComparisonResponse? = null,
    val error: String = ""
)

@HiltViewModel
class PurchaseComparisonViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    savedState: SavedStateHandle
) : ViewModel() {

    private val purchaseId: Int = savedState.get<Int>("purchaseId") ?: 0

    private val _uiState = MutableStateFlow(PurchaseComparisonUiState())
    val uiState: StateFlow<PurchaseComparisonUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PurchaseComparisonUiState(isLoading = true)
            try {
                val token = session.bearerToken.first()
                val response = api.comparePurchase(token, purchaseId)
                if (response.isSuccessful) {
                    _uiState.value = PurchaseComparisonUiState(isLoading = false, data = response.body())
                } else {
                    _uiState.value = PurchaseComparisonUiState(isLoading = false, error = "Error ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = PurchaseComparisonUiState(isLoading = false, error = e.message ?: "Error de conexión")
            }
        }
    }
}
