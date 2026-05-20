package com.undef.superahorroturina.ui.state

import com.undef.superahorroturina.model.Product

data class NewPurchaseUiState(
    val supermarket: String = "",
    val date: String = "",
    val time: String = "",
    val products: List<Product> = emptyList(),
    val dropdownExpanded: Boolean = false,
    val isSaving: Boolean = false
) {
    // El total se calcula reactivamente desde los productos — no se almacena
    val total: Double get() = products.sumOf { it.price * it.quantity }
}
