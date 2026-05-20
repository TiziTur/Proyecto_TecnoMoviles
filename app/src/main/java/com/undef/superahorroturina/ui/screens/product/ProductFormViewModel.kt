package com.undef.superahorroturina.ui.screens.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.state.ProductFormUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductFormViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    // Carga datos si se está editando un producto existente
    fun loadProduct(purchaseId: Int, productId: Int?) {
        if (productId == null) return
        val product = MockData.purchases
            .find { it.id == purchaseId }
            ?.products
            ?.find { it.id == productId } ?: return

        _uiState.value = ProductFormUiState(
            code        = product.code,
            name        = product.name,
            description = product.description,
            price       = product.price.toString(),
            quantity    = product.quantity.toString()
        )
    }

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(code = value)
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun onPriceChange(value: String) {
        _uiState.value = _uiState.value.copy(price = value, priceError = false)
    }

    fun onQuantityChange(value: String) {
        _uiState.value = _uiState.value.copy(quantity = value, quantityError = false)
    }

    fun onSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.price.toDoubleOrNull() == null) {
            _uiState.value = state.copy(priceError = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            delay(300L) // TODO: reemplazar con llamada real a ProductRepository
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSuccess()
        }
    }
}
