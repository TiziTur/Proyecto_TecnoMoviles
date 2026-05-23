// Repositorio de productos: CRUD anidado bajo una compra.
// El backend recalcula el total de la compra automáticamente al agregar/editar/borrar productos.
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
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
    private val session: SessionDataStore
) {
    suspend fun getProducts(purchaseId: Int): ApiResult<List<Product>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getProducts(token, purchaseId)
        if (response.isSuccessful) {
            ApiResult.Success(response.body()!!.map {
                Product(it.id, it.code, it.name, it.description, it.price, it.quantity)
            })
        } else {
            ApiResult.Error("Error al cargar productos: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createProduct(
        purchaseId: Int, code: String, name: String,
        description: String, price: Double, quantity: Int
    ): ApiResult<Product> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createProduct(
            token, purchaseId,
            CreateProductRequest(code, name, description, price, quantity)
        )
        if (response.isSuccessful) {
            val p = response.body()!!
            ApiResult.Success(Product(p.id, p.code, p.name, p.description, p.price, p.quantity))
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
            ApiResult.Success(Product(p.id, p.code, p.name, p.description, p.price, p.quantity))
        } else {
            ApiResult.Error("Error al actualizar producto: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deleteProduct(purchaseId: Int, productId: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deleteProduct(token, purchaseId, productId)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else ApiResult.Error("Error al eliminar producto: ${response.code()}")
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }
}
