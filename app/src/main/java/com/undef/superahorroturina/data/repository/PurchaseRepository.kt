// Repositorio de compras: CRUD completo contra el backend.
// Convierte los DTOs de red a los modelos de dominio que usa el resto de la app.
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreatePurchaseRequest
import com.undef.superahorroturina.data.network.dto.PurchaseDto
import com.undef.superahorroturina.data.network.dto.UpdatePurchaseRequest
import com.undef.superahorroturina.model.Product
import com.undef.superahorroturina.model.Purchase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore
) {
    suspend fun getPurchases(): ApiResult<List<Purchase>> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchases(token)
        if (response.isSuccessful) {
            ApiResult.Success(response.body()!!.map { it.toDomain() })
        } else {
            ApiResult.Error("Error al cargar compras: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun getPurchase(id: Int): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchase(token, id)
        if (response.isSuccessful) {
            ApiResult.Success(response.body()!!.toDomain())
        } else {
            ApiResult.Error("Compra no encontrada")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createPurchase(supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createPurchase(token, CreatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            ApiResult.Success(response.body()!!.toDomain())
        } else {
            ApiResult.Error("Error al crear compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updatePurchase(id: Int, supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updatePurchase(token, id, UpdatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            ApiResult.Success(response.body()!!.toDomain())
        } else {
            ApiResult.Error("Error al actualizar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deletePurchase(id: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deletePurchase(token, id)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else ApiResult.Error("Error al eliminar compra: ${response.code()}")
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    // Convierte el DTO de red al modelo de dominio
    private fun PurchaseDto.toDomain(): Purchase = Purchase(
        id           = id,
        date         = runCatching { LocalDate.parse(purchaseDate) }.getOrElse { LocalDate.now() },
        time         = runCatching { LocalTime.parse(purchaseTime.take(5)) }.getOrElse { LocalTime.MIDNIGHT },
        supermarket  = supermarket,
        total        = total,
        productCount = productCount,
        products     = products.map { p ->
            Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
        }
    )
}
