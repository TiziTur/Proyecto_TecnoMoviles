package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreateProductRequest
import com.undef.superahorroturina.data.network.dto.UpdateProductRequest
import com.undef.superahorroturina.model.Product
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val productDao: ProductDao
) {
    suspend fun getProducts(purchaseId: Int): ApiResult<List<Product>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getProducts(token, purchaseId)
        if (response.isSuccessful) {
            val products = response.body()!!.map {
                Product(it.id, it.code, it.name, it.description, it.price, it.quantity)
            }
            productDao.upsertAll(products.map { p ->
                ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity)
            })
            ApiResult.Success(products)
        } else {
            ApiResult.Error("Error al cargar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createProduct(
        purchaseId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int, category: String = ""
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createProduct(
            token, purchaseId,
            CreateProductRequest(code, name, description, price, quantity, category)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            val product = Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity, p.category))
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
            productDao.upsert(ProductEntity(p.id, purchaseId, p.code, p.name, p.description, p.price, p.quantity))
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
        productDao.getByPurchaseId(purchaseId).first().map { e ->
            Product(e.id, e.code, e.name, e.description, e.price, e.quantity)
        }
}
