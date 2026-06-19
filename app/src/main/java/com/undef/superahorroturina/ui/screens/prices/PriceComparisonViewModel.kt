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
    val isLoadingMore: Boolean = false,
    val allComparisons: List<PriceComparisonItemDto> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val availableBrands: List<String> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
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

    companion object { private const val PAGE_SIZE = 50 }

    private val _uiState = MutableStateFlow(PriceComparisonUiState())
    val uiState: StateFlow<PriceComparisonUiState> = _uiState.asStateFlow()

    val searchQuery      = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("")
    val selectedBrand    = MutableStateFlow("")
    val minPriceInput    = MutableStateFlow("")
    val maxPriceInput    = MutableStateFlow("")

    private var currentOffset = 0

    init {
        reload()
        viewModelScope.launch {
            combine(searchQuery.debounce(500), selectedCategory, selectedBrand.debounce(400)) { q, c, b ->
                Triple(q, c, b)
            }.distinctUntilChanged().drop(1).collect { reload() }
        }
        viewModelScope.launch {
            combine(minPriceInput.debounce(700), maxPriceInput.debounce(700)) { a, b -> a to b }
                .distinctUntilChanged().drop(1).collect { reload() }
        }
    }

    fun reload() {
        currentOffset = 0
        _uiState.value = _uiState.value.copy(
            isLoading = true, error = "", allComparisons = emptyList(), hasMore = false
        )
        viewModelScope.launch { fetchPage(0, append = false) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch { fetchPage(currentOffset + PAGE_SIZE, append = true) }
    }

    fun clearFilters() {
        selectedBrand.value = ""
        minPriceInput.value = ""
        maxPriceInput.value = ""
    }

    val hasActiveFilters: Boolean
        get() = selectedBrand.value.isNotBlank() ||
                minPriceInput.value.isNotBlank() ||
                maxPriceInput.value.isNotBlank()

    private suspend fun fetchPage(offset: Int, append: Boolean) {
        try {
            val token = session.bearerToken.first()
            val response = api.getPriceComparisons(
                token    = token,
                query    = searchQuery.value.ifBlank { null },
                category = selectedCategory.value.ifBlank { null },
                brand    = selectedBrand.value.ifBlank { null },
                minPrice = minPriceInput.value.toIntOrNull(),
                maxPrice = maxPriceInput.value.toIntOrNull(),
                offset   = offset
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                currentOffset = offset
                val prev = _uiState.value
                _uiState.value = prev.copy(
                    isLoading       = false,
                    isLoadingMore   = false,
                    allComparisons  = if (append) prev.allComparisons + body.comparisons
                                      else body.comparisons,
                    categoryCounts  = if (body.categoryCounts.isNotEmpty()) body.categoryCounts
                                      else prev.categoryCounts,
                    availableBrands = if (body.brands.isNotEmpty()) body.brands
                                      else prev.availableBrands,
                    total           = body.total,
                    hasMore         = body.hasMore,
                    source          = body.source,
                    lastUpdated     = body.lastUpdated,
                    isEmpty         = body.isEmpty,
                    error           = ""
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isLoadingMore = false,
                    error = "Error ${response.code()}"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false, isLoadingMore = false,
                error = e.message ?: "Error de conexión"
            )
        }
    }
}
