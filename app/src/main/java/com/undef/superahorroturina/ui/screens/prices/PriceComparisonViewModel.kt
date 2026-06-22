package com.undef.superahorroturina.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PriceComparisonRepository
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
    private val repository: PriceComparisonRepository
) : ViewModel() {

    companion object { private const val PAGE_SIZE = 50 }

    private val _uiState = MutableStateFlow(PriceComparisonUiState())
    val uiState: StateFlow<PriceComparisonUiState> = _uiState.asStateFlow()

    val searchQuery      = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("")
    val selectedBrand    = MutableStateFlow("")
    val minPriceInput    = MutableStateFlow("")
    val maxPriceInput    = MutableStateFlow("")
    // "name" | "price_asc" | "price_desc"
    val sortOrder        = MutableStateFlow("name")

    private var currentOffset = 0

    init {
        // Room como fuente de verdad para la primera pintura: mientras se resuelve la
        // llamada de red real, mostrar lo que ya hay cacheado de la última vista base.
        viewModelScope.launch {
            val cached = repository.getCachedComparisonsFlow().first()
            if (cached.isNotEmpty() && _uiState.value.allComparisons.isEmpty()) {
                _uiState.value = _uiState.value.copy(allComparisons = cached, isLoading = true)
            }
        }
        reload()
        viewModelScope.launch {
            combine(
                searchQuery.debounce(500),
                selectedCategory,
                selectedBrand.debounce(400),
                sortOrder
            ) { q, c, b, s -> listOf(q, c, b, s) }
                .distinctUntilChanged()
                .drop(1)
                .collect { reload() }
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
        sortOrder.value     = "name"
    }

    val hasActiveFilters: Boolean
        get() = selectedBrand.value.isNotBlank() ||
                minPriceInput.value.isNotBlank() ||
                maxPriceInput.value.isNotBlank() ||
                sortOrder.value != "name"

    private suspend fun fetchPage(offset: Int, append: Boolean) {
        val sort = sortOrder.value.takeIf { it != "name" }
        when (val result = repository.fetchPage(
            query    = searchQuery.value.ifBlank { null },
            category = selectedCategory.value.ifBlank { null },
            brand    = selectedBrand.value.ifBlank { null },
            minPrice = minPriceInput.value.toIntOrNull(),
            maxPrice = maxPriceInput.value.toIntOrNull(),
            sort     = sort,
            offset   = offset
        )) {
            is ApiResult.Success -> {
                val body = result.data
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
            }
            is ApiResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isLoadingMore = false,
                    error = result.message
                )
            }
        }
    }
}
