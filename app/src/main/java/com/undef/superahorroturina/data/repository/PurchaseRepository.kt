package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.ProductEntity
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.PurchaseDao
import com.undef.superahorroturina.data.local.db.PurchaseEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.CreatePurchaseRequest
import com.undef.superahorroturina.data.network.dto.PurchaseDto
import com.undef.superahorroturina.data.network.dto.UpdatePurchaseRequest
import com.undef.superahorroturina.model.Product
import com.undef.superahorroturina.model.Purchase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao
) {
    fun getPurchasesFlow(): Flow<List<Purchase>> =
        purchaseDao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun refreshPurchases(): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchases(token)
        if (response.isSuccessful) {
            val dtos = response.body()!!
            purchaseDao.upsertAll(dtos.map { it.toEntity() })
            dtos.forEach { dto ->
                if (dto.products.isNotEmpty()) {
                    productDao.upsertAll(dto.products.map { p ->
                        ProductEntity(p.id, dto.id, p.code, p.name, p.description, p.price, p.quantity)
                    })
                }
            }
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al cargar compras: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun getPurchase(id: Int): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPurchase(token, id)
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            productDao.upsertAll(dto.products.map { p ->
                ProductEntity(p.id, id, p.code, p.name, p.description, p.price, p.quantity)
            })
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Compra no encontrada")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun createPurchase(supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.createPurchase(token, CreatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Error al crear compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updatePurchase(id: Int, supermarket: String, date: String, time: String): ApiResult<Purchase> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updatePurchase(token, id, UpdatePurchaseRequest(date, time, supermarket))
        if (response.isSuccessful) {
            val dto = response.body()!!
            purchaseDao.upsert(dto.toEntity())
            ApiResult.Success(dto.toDomain())
        } else {
            ApiResult.Error("Error al actualizar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun deletePurchase(id: Int): ApiResult<Unit> = runCatching {
        val token = session.bearerToken.first()
        val response = api.deletePurchase(token, id)
        if (response.isSuccessful) {
            purchaseDao.delete(id)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Error al eliminar compra: ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    private fun PurchaseDto.toEntity() = PurchaseEntity(
        id           = id,
        purchaseDate = purchaseDate,
        purchaseTime = purchaseTime,
        supermarket  = supermarket,
        total        = total,
        productCount = maxOf(productCount, products.size)
    )

    private fun PurchaseEntity.toDomain() = Purchase(
        id           = id,
        date         = runCatching { LocalDate.parse(purchaseDate) }.getOrElse { LocalDate.now() },
        time         = runCatching { LocalTime.parse(purchaseTime.take(5)) }.getOrElse { LocalTime.MIDNIGHT },
        supermarket  = supermarket,
        total        = total,
        productCount = productCount,
        products     = emptyList()
    )

    private fun PurchaseDto.toDomain(): Purchase {
        val mappedProducts = products.map { p ->
            Product(p.id, p.code, p.name, p.description, p.price, p.quantity)
        }
        val resolvedCount = maxOf(productCount, mappedProducts.size)
        return Purchase(
            id           = id,
            date         = runCatching { LocalDate.parse(purchaseDate) }.getOrElse { LocalDate.now() },
            time         = runCatching { LocalTime.parse(purchaseTime.take(5)) }.getOrElse { LocalTime.MIDNIGHT },
            supermarket  = supermarket,
            total        = total,
            productCount = resolvedCount,
            products     = mappedProducts
        )
    }
}
