// ViewModel para la comparativa de precios entre supermercados.
package com.undef.superahorroturina.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceComparisonUiState(
    val isLoading: Boolean = true,
    val comparisons: List<PriceComparisonItemDto> = emptyList(),
    val error: String = ""
)

@HiltViewModel
class PriceComparisonViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceComparisonUiState())
    val uiState: StateFlow<PriceComparisonUiState> = _uiState.asStateFlow()

    init { loadComparisons() }

    fun loadComparisons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val token    = session.bearerToken.first()
                val response = api.getPriceComparisons(token)
                if (response.isSuccessful) {
                    _uiState.value = PriceComparisonUiState(
                        isLoading    = false,
                        comparisons  = response.body()?.comparisons ?: emptyList()
                    )
                } else {
                    _uiState.value = PriceComparisonUiState(
                        isLoading = false,
                        error = "Error ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PriceComparisonUiState(
                    isLoading = false,
                    error = e.message ?: "Error de conexión"
                )
            }
        }
    }
}
