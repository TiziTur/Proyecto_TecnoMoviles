package com.undef.superahorroturina.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceComparisonUiState(
    val isLoading: Boolean = true,
    val comparisons: List<PriceComparisonItemDto> = emptyList(),
    val source: String = "",
    val lastUpdated: String? = null,
    val isEmpty: Boolean = false,
    val error: String = ""
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PriceComparisonViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceComparisonUiState())
    val uiState: StateFlow<PriceComparisonUiState> = _uiState.asStateFlow()

    val searchQuery = MutableStateFlow("")

    init {
        loadComparisons()
        viewModelScope.launch {
            searchQuery.debounce(500).distinctUntilChanged()
                .collect { query -> loadComparisons(query) }
        }
    }

    fun loadComparisons(query: String = searchQuery.value) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val token    = session.bearerToken.first()
                val response = api.getPriceComparisons(token, query.ifBlank { null })
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _uiState.value = PriceComparisonUiState(
                        isLoading   = false,
                        comparisons = body.comparisons,
                        source      = body.source,
                        lastUpdated = body.lastUpdated,
                        isEmpty     = body.isEmpty
                    )
                } else {
                    _uiState.value = PriceComparisonUiState(
                        isLoading = false, error = "Error ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PriceComparisonUiState(
                    isLoading = false, error = e.message ?: "Error de conexión"
                )
            }
        }
    }
}
