// Interfaz Retrofit con todos los endpoints del backend.
// Cada función es suspend para usarse desde coroutines en los repositorios.
package com.undef.superahorroturina.data.network

import com.undef.superahorroturina.data.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    // ── Users ─────────────────────────────────────────────────
    @GET("users/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<UserDto>

    @PUT("users/me")
    suspend fun updateMe(
        @Header("Authorization") token: String,
        @Body body: UpdateUserRequest
    ): Response<UserDto>

    // ── Supermarkets ──────────────────────────────────────────
    @GET("supermarkets")
    suspend fun getSupermarkets(): Response<List<SupermarketDto>>

    // ── Purchases ─────────────────────────────────────────────
    @GET("purchases")
    suspend fun getPurchases(
        @Header("Authorization") token: String
    ): Response<List<PurchaseDto>>

    @GET("purchases/{id}")
    suspend fun getPurchase(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<PurchaseDto>

    @POST("purchases")
    suspend fun createPurchase(
        @Header("Authorization") token: String,
        @Body body: CreatePurchaseRequest
    ): Response<PurchaseDto>

    @PUT("purchases/{id}")
    suspend fun updatePurchase(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: UpdatePurchaseRequest
    ): Response<PurchaseDto>

    @DELETE("purchases/{id}")
    suspend fun deletePurchase(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    // ── Products ──────────────────────────────────────────────
    @GET("purchases/{purchaseId}/products")
    suspend fun getProducts(
        @Header("Authorization") token: String,
        @Path("purchaseId") purchaseId: Int
    ): Response<List<ProductDto>>

    @POST("purchases/{purchaseId}/products")
    suspend fun createProduct(
        @Header("Authorization") token: String,
        @Path("purchaseId") purchaseId: Int,
        @Body body: CreateProductRequest
    ): Response<ProductDto>

    @PUT("purchases/{purchaseId}/products/{productId}")
    suspend fun updateProduct(
        @Header("Authorization") token: String,
        @Path("purchaseId") purchaseId: Int,
        @Path("productId") productId: Int,
        @Body body: UpdateProductRequest
    ): Response<ProductDto>

    @DELETE("purchases/{purchaseId}/products/{productId}")
    suspend fun deleteProduct(
        @Header("Authorization") token: String,
        @Path("purchaseId") purchaseId: Int,
        @Path("productId") productId: Int
    ): Response<Unit>

    // ── Ticket OCR ────────────────────────────────────────────
    @POST("purchases/{purchaseId}/scan-ticket")
    suspend fun scanTicket(
        @Header("Authorization") token: String,
        @Path("purchaseId") purchaseId: Int,
        @Body body: ScanTicketRequest
    ): Response<ScanTicketResponse>

    // ── Chat IA ───────────────────────────────────────────────
    @POST("chat")
    suspend fun chat(
        @Header("Authorization") token: String,
        @Body body: ChatRequest
    ): Response<ChatResponse>

    // ── Comparativa de precios ────────────────────────────────
    @GET("prices/compare")
    suspend fun getPriceComparisons(
        @Header("Authorization") token: String,
        @Query("query") query: String? = null
    ): Response<PriceComparisonResponse>
}
