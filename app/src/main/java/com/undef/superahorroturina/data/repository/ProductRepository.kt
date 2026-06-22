package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreateProductRequest
import com.undef.superahorroturina.data.network.dto.MatchSeedItemDto
import com.undef.superahorroturina.data.network.dto.MatchSeedRequest
import com.undef.superahorroturina.data.network.dto.SeedMatchResultDto
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.network.dto.UpdateProductRequest
import com.undef.superahorroturina.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val productDao: ProductDao
) {
    // Room es la fuente de verdad para la UI — refreshProducts() solo actualiza el caché,
    // nunca devuelve los productos directamente (mismo patrón que PurchaseRepository).
    fun getProductsFlow(purchaseId: Int): Flow<List<Product>> =
        productDao.getByPurchaseId(purchaseId).map { entities ->
            entities.map { Product(it.id, it.code, it.name, it.description, it.price, it.quantity) }
        }

    suspend fun refreshProducts(purchaseId: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getProducts(token, purchaseId)
        if (response.isSuccessful) {
            val dtos = response.body()!!
            productDao.upsertAll(dtos.map { p ->
                ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName)
            })
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al cargar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createProduct(
        purchaseId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int,
        category: String = "", seedProductName: String? = null
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createProduct(
            token, purchaseId,
            CreateProductRequest(code, name, description, price, quantity, category, seedProductName)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al crear producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updateProduct(
        purchaseId: Int, productId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updateProduct(
            token, purchaseId, productId,
            UpdateProductRequest(code, name, description, price, quantity)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category, p.seedProductName))
            ApiResult.Success(product)
        } else {
            ApiResult.Error("Error al actualizar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deleteProduct(purchaseId: Int, productId: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deleteProduct(token, purchaseId, productId)
        if (response.isSuccessful) {
            productDao.delete(productId)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun getLocalProductsForPurchase(purchaseId: Int): List<Product> =
        getProductsFlow(purchaseId).first()

    // ── Matching contra la seed ──────────────────────────────────
    suspend fun matchSeed(names: List<String>): ApiResult<List<SeedMatchResultDto>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.matchSeedProducts(token, MatchSeedRequest(names.map { MatchSeedItemDto(it) }))
        if (response.isSuccessful) {
            val matches: List<SeedMatchResultDto> = response.body()?.matches ?: emptyList()
            ApiResult.Success(matches)
        } else {
            ApiResult.Error("Error al vincular productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun searchSeedProducts(query: String): ApiResult<List<SeedSearchResultDto>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.searchSeedProducts(token, query)
        if (response.isSuccessful) {
            val results: List<SeedSearchResultDto> = response.body() ?: emptyList()
            ApiResult.Success(results)
        } else {
            ApiResult.Error("Error al buscar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }
}
