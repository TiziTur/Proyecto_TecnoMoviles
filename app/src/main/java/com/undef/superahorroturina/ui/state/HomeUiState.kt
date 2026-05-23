package com.undef.superahorroturina.ui.state

import com.undef.superahorroturina.model.Purchase

// Estado de UI para la pantalla Home.
// Separado del ViewModel para seguir el principio de separación de responsabilidades:
// el ViewModel maneja lógica, el UiState solo transporta datos hacia la UI.
data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val totalThisMonth: Double = 0.0,
    val recentPurchases: List<Purchase> = emptyList(),
    val purchaseCount: Int = 0,
    val supermarketCount: Int = 0
)
