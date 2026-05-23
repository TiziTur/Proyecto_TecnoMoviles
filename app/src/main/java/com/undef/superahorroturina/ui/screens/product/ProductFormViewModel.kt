// ViewModel de formulario de producto conectado al ProductRepository real.
package com.undef.superahorroturina.ui.screens.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.ui.state.ProductFormUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private var currentPurchaseId: Int = 0
    private var currentProductId: Int? = null

    fun loadProduct(purchaseId: Int, productId: Int?) {
        currentPurchaseId = purchaseId
        currentProductId  = productId
        if (productId == null) return

        viewModelScope.launch {
            when (val result = productRepository.getProducts(purchaseId)) {
                is ApiResult.Success -> {
                    val product = result.data.find { it.id == productId } ?: return@launch
                    _uiState.value = ProductFormUiState(
                        code        = product.code,
                        name        = product.name,
                        description = product.description,
                        price       = product.price.toString(),
                        quantity    = product.quantity.toString()
                    )
                }
                is ApiResult.Error -> { /* mantiene estado actual */ }
            }
        }
    }

    fun onCodeChange(value: String)        { _uiState.value = _uiState.value.copy(code = value) }
    fun onNameChange(value: String)        { _uiState.value = _uiState.value.copy(name = value) }
    fun onDescriptionChange(value: String) { _uiState.value = _uiState.value.copy(description = value) }
    fun onPriceChange(value: String)       { _uiState.value = _uiState.value.copy(price = value, priceError = false) }
    fun onQuantityChange(value: String)    { _uiState.value = _uiState.value.copy(quantity = value, quantityError = false) }

    fun onSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        val price = state.price.toDoubleOrNull()
        if (price == null || price <= 0) {
            _uiState.value = state.copy(priceError = true)
            return
        }
        val quantity = state.quantity.toIntOrNull() ?: 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val result = if (currentProductId != null) {
                productRepository.updateProduct(
                    currentPurchaseId, currentProductId!!,
                    state.code, state.name, state.description, price, quantity
                )
            } else {
                productRepository.createProduct(
                    currentPurchaseId,
                    state.code, state.name, state.description, price, quantity
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveError = result.message)
                }
            }
        }
    }
}
