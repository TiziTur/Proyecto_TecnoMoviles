package com.undef.superahorroturina.model

import java.time.LocalDate
import java.time.LocalTime

data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String = "",
    val avatarUrl: String? = null
)

data class Purchase(
    val id: Int,
    val date: LocalDate,
    val time: LocalTime,
    val supermarket: String,
    val total: Double,
    val products: List<Product> = emptyList(),
    val ticketImageUri: String? = null
)

data class Product(
    val id: Int,
    val code: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int = 1
)

data class StatSummary(
    val label: String,
    val amount: Double
)
