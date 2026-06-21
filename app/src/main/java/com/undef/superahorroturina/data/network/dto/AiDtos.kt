// DTOs para las funcionalidades de IA: OCR de ticket, chat y comparativa de precios.
package com.undef.superahorroturina.data.network.dto

import com.google.gson.annotations.SerializedName

// ── Ticket OCR ──────────────────────────────────────────────────

data class TicketImageDto(
    @SerializedName("imageBase64") val imageBase64: String,
    @SerializedName("mimeType")    val mimeType: String = "image/jpeg"
)

data class ScanTicketRequest(
    @SerializedName("images") val images: List<TicketImageDto>
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
    @SerializedName("brand")          val brand: String = "",
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
    @SerializedName("categoryCounts")   val categoryCounts: Map<String, Int> = emptyMap(),
    @SerializedName("total")            val total: Int = 0,
    @SerializedName("hasMore")          val hasMore: Boolean = false,
    @SerializedName("offset")           val offset: Int = 0,
    @SerializedName("brands")           val brands: List<String> = emptyList()
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

// ── Matching ticket → seed ───────────────────────────────────────

data class MatchSeedItemDto(
    @SerializedName("name") val name: String
)

data class MatchSeedRequest(
    @SerializedName("products") val products: List<MatchSeedItemDto>
)

data class SeedMatchResultDto(
    @SerializedName("seedMatch")  val seedMatch: String?,
    @SerializedName("candidates") val candidates: List<String> = emptyList()
)

data class MatchSeedResponse(
    @SerializedName("matches") val matches: List<SeedMatchResultDto>
)

data class SeedSearchResultDto(
    @SerializedName("productName") val productName: String,
    @SerializedName("brand")       val brand: String = ""
)

// ── Sugerencia: donde conviene comprar en general ────────────────

data class CheapestSummaryResponse(
    @SerializedName("isEmpty")             val isEmpty: Boolean = false,
    @SerializedName("cheapestSupermarket") val cheapestSupermarket: String = "",
    @SerializedName("productsCompared")    val productsCompared: Int = 0,
    @SerializedName("productsWon")         val productsWon: Int = 0,
    @SerializedName("totalSavings")        val totalSavings: Double = 0.0,
    @SerializedName("avgSavingsPct")       val avgSavingsPct: Int = 0,
    @SerializedName("headline")            val headline: String = ""
)
