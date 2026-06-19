// DTOs para las funcionalidades de IA: OCR de ticket, chat y comparativa de precios.
package com.undef.superahorroturina.data.network.dto

import com.google.gson.annotations.SerializedName

// ── Ticket OCR ──────────────────────────────────────────────────

data class ScanTicketRequest(
    @SerializedName("imageBase64") val imageBase64: String,
    @SerializedName("mimeType")    val mimeType: String = "image/jpeg"
)

data class ScannedProductDto(
    @SerializedName("name")        val name: String,
    @SerializedName("price")       val price: Double,
    @SerializedName("quantity")    val quantity: Int = 1,
    @SerializedName("code")        val code: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("category")    val category: String = ""
)

data class ScanTicketResponse(
    @SerializedName("supermarket") val supermarket: String?,
    @SerializedName("date")        val date: String?,
    @SerializedName("products")    val products: List<ScannedProductDto>
)

// ── Chat IA ─────────────────────────────────────────────────────

data class ChatMessage(
    @SerializedName("role") val role: String,   // "user" | "model"
    @SerializedName("text") val text: String
)

data class ChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<ChatMessage> = emptyList()
)

data class ChatResponse(
    @SerializedName("reply") val reply: String
)

// ── Comparativa de precios ───────────────────────────────────────

data class PriceEntryDto(
    @SerializedName("supermarket") val supermarket: String,
    @SerializedName("price")       val price: Double,
    @SerializedName("isUserData")  val isUserData: Boolean
)

data class PriceComparisonItemDto(
    @SerializedName("productName")    val productName: String,
    @SerializedName("category")       val category: String = "",
    @SerializedName("prices")         val prices: List<PriceEntryDto>,
    @SerializedName("cheapestAt")     val cheapestAt: String,
    @SerializedName("cheapestPrice")  val cheapestPrice: Double,
    @SerializedName("maxSavings")     val maxSavings: Double,
    @SerializedName("savingsPct")     val savingsPct: Int
)

data class PriceComparisonResponse(
    @SerializedName("comparisons")      val comparisons: List<PriceComparisonItemDto>,
    @SerializedName("source")           val source: String = "",
    @SerializedName("lastUpdated")      val lastUpdated: String? = null,
    @SerializedName("isEmpty")          val isEmpty: Boolean = false,
    @SerializedName("categoryCounts")   val categoryCounts: Map<String, Int> = emptyMap()
)

// ── Comparativa de compra completa contra SEPA ──────────────────

data class PurchaseProductMatchDto(
    @SerializedName("ticketName")   val ticketName: String,
    @SerializedName("matchedName")  val matchedName: String,
    @SerializedName("sepaPrice")    val sepaPrice: Double,
    @SerializedName("ticketPrice")  val ticketPrice: Double,
    @SerializedName("category")     val category: String = ""
)

data class PurchaseSupermarketComparisonDto(
    @SerializedName("supermarket")   val supermarket: String,
    @SerializedName("total")         val total: Double,
    @SerializedName("matchedCount")  val matchedCount: Int,
    @SerializedName("savings")       val savings: Double,
    @SerializedName("savingsPct")    val savingsPct: Int,
    @SerializedName("products")      val products: List<PurchaseProductMatchDto>
)

data class PurchaseComparisonResponse(
    @SerializedName("purchaseId")          val purchaseId: Int,
    @SerializedName("userTotal")           val userTotal: Double,
    @SerializedName("supermarket")         val supermarket: String,
    @SerializedName("comparisons")         val comparisons: List<PurchaseSupermarketComparisonDto>,
    @SerializedName("unmatchedProducts")   val unmatchedProducts: List<String> = emptyList()
)
