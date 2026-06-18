package com.undef.superahorroturina.ui.screens.history

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository
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

    fun exportToCsv(context: Context, onReady: (Uri) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val purchases = _uiState.value.purchases
                val sb = StringBuilder()
                sb.appendLine("\"Fecha\",\"Hora\",\"Supermercado\",\"Total\"," +
                              "\"Cod Producto\",\"Nombre\",\"Descripcion\",\"Precio\",\"Cantidad\"")

                for (purchase in purchases) {
                    val products = productRepository.getLocalProductsForPurchase(purchase.id)
                    if (products.isEmpty()) {
                        sb.appendLine("\"${purchase.date}\",\"${purchase.time}\"," +
                                      "\"${purchase.supermarket}\",\"${purchase.total}\"," +
                                      "\"\",\"\",\"\",\"\",\"\"")
                    } else {
                        for (p in products) {
                            sb.appendLine("\"${purchase.date}\",\"${purchase.time}\"," +
                                          "\"${purchase.supermarket}\",\"${purchase.total}\"," +
                                          "\"${p.code}\",\"${p.name.replace("\"","\"\"")}\",\"${p.description.replace("\"","\"\"")}\",\"${p.price}\",\"${p.quantity}\"")
                        }
                    }
                }

                val dir  = File(context.filesDir, "exports").also { it.mkdirs() }
                val file = File(dir, "compras_${System.currentTimeMillis()}.csv")
                file.writeText(sb.toString(), Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) { onReady(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Error al exportar") }
            }
        }
    }
}
