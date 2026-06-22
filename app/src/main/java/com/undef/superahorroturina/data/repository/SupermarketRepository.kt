// Repositorio de supermercados: Room es la fuente de verdad para la UI (igual que
// PurchaseRepository/ProductRepository) — el dropdown siempre lee del Flow de Room, y
// refreshSupermarkets() es quien llama a la API (el endpoint "externo" del TP, sin auth)
// y graba el resultado en Room. Si todavía no hay nada en caché y la red falla (primer
// arranque sin conexión), se siembra Room con una lista fallback para que el dropdown
// nunca quede vacío.
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.db.SupermarketDao
import com.undef.superahorroturina.data.local.db.SupermarketEntity
import com.undef.superahorroturina.data.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupermarketRepository @Inject constructor(
    private val api: ApiService,
    private val supermarketDao: SupermarketDao
) {
    fun getSupermarketsFlow(): Flow<List<String>> =
        supermarketDao.getAll().map { entities -> entities.map { it.name } }

    suspend fun refreshSupermarkets(): ApiResult<Unit> = runCatching {
        val response = api.getSupermarkets()
        if (response.isSuccessful) {
            val names = response.body()!!.map { it.name }
            supermarketDao.replaceAll(names.map { SupermarketEntity(it) })
            ApiResult.Success(Unit)
        } else {
            seedFallbackIfEmpty()
            ApiResult.Error("Error al cargar supermercados: ${response.code()}")
        }
    }.getOrElse {
        seedFallbackIfEmpty()
        ApiResult.Error(it.message ?: "Error de conexión")
    }

    private suspend fun seedFallbackIfEmpty() {
        if (supermarketDao.count() == 0) {
            supermarketDao.insertAll(fallbackSupermarkets().map { SupermarketEntity(it) })
        }
    }

    private fun fallbackSupermarkets() = listOf(
        "Carrefour", "Día", "Coto", "La Anónima", "Jumbo",
        "Walmart", "Vea", "Disco", "Changomas", "HiperLibertad",
        "Makro", "Otro"
    )
}
