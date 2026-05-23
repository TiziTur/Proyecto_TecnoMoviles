// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val purchase: Purchase? = null,
    val error: String = ""
)

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    fun loadPurchase(purchaseId: Int) {
        viewModelScope.launch {
            _uiState.value = PurchaseDetailUiState(isLoading = true)
            when (val result = purchaseRepository.getPurchase(purchaseId)) {
                is ApiResult.Success -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    purchase  = result.data
                )
                is ApiResult.Error   -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }

    fun deletePurchase(onSuccess: () -> Unit) {
        val id = _uiState.value.purchase?.id ?: return
        viewModelScope.launch {
            purchaseRepository.deletePurchase(id)
            onSuccess()
        }
    }

    fun deleteProduct(purchaseId: Int, productId: Int) {
        viewModelScope.launch {
            productRepository.deleteProduct(purchaseId, productId)
            // Recargar la compra para reflejar el nuevo total y lista de productos
            loadPurchase(purchaseId)
        }
    }
}
