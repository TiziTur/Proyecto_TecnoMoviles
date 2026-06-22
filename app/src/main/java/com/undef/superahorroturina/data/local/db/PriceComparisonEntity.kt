package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// pricesJson guarda la lista de precios por supermercado serializada con Gson — Room no
// maneja listas anidadas sin un TypeConverter, y para este caso (un campo de solo lectura
// que nunca se filtra por SQL) serializar es más simple que normalizar en otra tabla.
@Entity(tableName = "price_comparisons")
data class PriceComparisonEntity(
    @PrimaryKey val productName: String,
    val brand: String,
    val category: String,
    val pricesJson: String,
    val cheapestAt: String,
    val cheapestPrice: Double,
    val maxSavings: Double,
    val savingsPct: Int
)
