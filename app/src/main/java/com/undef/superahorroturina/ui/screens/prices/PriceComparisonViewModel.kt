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
    val allComparisons: List<PriceComparisonItemDto> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
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

    val searchQuery      = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("")

    // Client-side category filter — no extra network call needed
    val filteredComparisons: StateFlow<List<PriceComparisonItemDto>> = combine(
        _uiState, selectedCategory
    ) { state, cat ->
        if (cat.isBlank()) state.allComparisons
        else state.allComparisons.filter { it.category == cat }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadComparisons()
        viewModelScope.launch {
            searchQuery.debounce(500).drop(1).distinctUntilChanged()
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
                    val body   = response.body()!!
                    val counts = body.categoryCounts.ifEmpty {
                        body.comparisons.groupingBy { it.category }.eachCount()
                    }
                    _uiState.value = PriceComparisonUiState(
                        isLoading      = false,
                        allComparisons = body.comparisons,
                        categoryCounts = counts,
                        source         = body.source,
                        lastUpdated    = body.lastUpdated,
                        isEmpty        = body.isEmpty
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
