package com.undef.superahorroturina.ui.state

data class ProductFormUiState(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val quantity: String = "1",
    val priceError: Boolean = false,
    val quantityError: Boolean = false,
    val isSaving: Boolean = false
) {
    // Subtotal reactivo calculado desde price y quantity
    val subtotal: Double get() = (price.toDoubleOrNull() ?: 0.0) * (quantity.toIntOrNull() ?: 1)
}
