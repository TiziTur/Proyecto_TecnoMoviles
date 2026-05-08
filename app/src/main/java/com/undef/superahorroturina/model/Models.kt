// Acá defino los modelos de datos de la app. Uso data classes porque Kotlin genera
// equals, hashCode y copy gratis — lo vimos en clase y acá se nota la diferencia con Java.
package com.undef.superahorroturina.model

import java.time.LocalDate
import java.time.LocalTime

// Representa al usuario logueado. Por ahora solo guardo lo básico; si después
// agrego foto de perfil lo tengo contemplado con avatarUrl nullable.
data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String = "",
    val avatarUrl: String? = null
)

// Una compra tiene fecha, hora, supermercado y lista de productos.
// El ticketImageUri es para la feature de adjuntar foto del ticket (por ahora null en todos los mocks).
data class Purchase(
    val id: Int,
    val date: LocalDate,
    val time: LocalTime,
    val supermarket: String,
    val total: Double,
    val products: List<Product> = emptyList(),
    val ticketImageUri: String? = null
)

// Producto individual dentro de una compra. El código EAN es el de barras del super.
data class Product(
    val id: Int,
    val code: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int = 1
)

// StatSummary es una clase genérica que uso para los gráficos: label = nombre, amount = valor.
// La reutilizo para gastos por mes, por supermercado y para los top products.
data class StatSummary(
    val label: String,
    val amount: Double
)
