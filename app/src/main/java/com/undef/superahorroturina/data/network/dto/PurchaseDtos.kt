// DTOs para compras y productos — mapean el JSON del backend Express.
package com.undef.superahorroturina.data.network.dto

import com.google.gson.annotations.SerializedName

// ── Purchase DTOs ─────────────────────────────────────────────

data class PurchaseDto(
    val id: Int,
    @SerializedName("purchase_date") val purchaseDate: String,  // "yyyy-MM-dd"
    @SerializedName("purchase_time") val purchaseTime: String,  // "HH:mm:ss"
    val supermarket: String,
    val total: Double,
    @SerializedName("product_count") val productCount: Int = 0,
    @SerializedName("ticket_image_uri") val ticketImageUri: String? = null,
    val products: List<ProductDto> = emptyList()
)

data class CreatePurchaseRequest(
    @SerializedName("purchase_date") val purchaseDate: String,
    @SerializedName("purchase_time") val purchaseTime: String,
    val supermarket: String
)

data class UpdatePurchaseRequest(
    @SerializedName("purchase_date") val purchaseDate: String,
    @SerializedName("purchase_time") val purchaseTime: String,
    val supermarket: String
)

// ── Product DTOs ──────────────────────────────────────────────

data class ProductDto(
    val id: Int,
    @SerializedName("purchase_id") val purchaseId: Int,
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)

data class CreateProductRequest(
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)

data class UpdateProductRequest(
    val code: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val quantity: Int = 1,
    val category: String = "",
    @SerializedName("seed_product_name") val seedProductName: String? = null
)

// ── Supermarket ───────────────────────────────────────────────

data class SupermarketDto(
    val id: Int,
    val name: String
)
