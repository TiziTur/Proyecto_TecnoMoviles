// Repositorio de comparativa de precios. La búsqueda/filtro/orden/paginado de este catálogo
// los resuelve el servidor (son miles de precios de referencia, no tiene sentido traerlos
// todos al dispositivo para reimplementar esa lógica en SQL local). Lo que sí cachea en Room
// es la vista BASE (sin ningún filtro activo, primera página) — así la pantalla siempre tiene
// algo para mostrar al abrir, incluso sin conexión, en vez de depender 100% de la red como
// antes (el ViewModel llamaba a ApiService directo, sin pasar nunca por Room).
package com.undef.superahorroturina.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.PriceComparisonDao
import com.undef.superahorroturina.data.local.db.PriceComparisonEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.PriceComparisonItemDto
import com.undef.superahorroturina.data.network.dto.PriceComparisonResponse
import com.undef.superahorroturina.data.network.dto.PriceEntryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceComparisonRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val dao: PriceComparisonDao
) {
    private val gson = Gson()
    private val pricesListType = object : TypeToken<List<PriceEntryDto>>() {}.type

    // Caché de la vista base — la observa el ViewModel para tener algo que mostrar de
    // entrada (incluso offline) mientras se resuelve la llamada de red real.
    fun getCachedComparisonsFlow(): Flow<List<PriceComparisonItemDto>> =
        dao.getAll().map { entities -> entities.map { it.toDto() } }

    suspend fun fetchPage(
        query: String?, category: String?, brand: String?,
        minPrice: Int?, maxPrice: Int?, sort: String?, offset: Int?
    ): ApiResult<PriceComparisonResponse> = runCatching {
        val token = session.bearerToken.first()
        val response = api.getPriceComparisons(token, query, category, brand, minPrice, maxPrice, sort, offset)
        if (response.isSuccessful) {
            val body = response.body()!!
            val isBaseView = query.isNullOrBlank() && category.isNullOrBlank() && brand.isNullOrBlank() &&
                minPrice == null && maxPrice == null && (offset == null || offset == 0)
            if (isBaseView) {
                dao.replaceAll(body.comparisons.map { it.toEntity() })
            }
            ApiResult.Success(body)
        } else {
            ApiResult.Error("Error ${response.code()}")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    private fun PriceComparisonItemDto.toEntity() = PriceComparisonEntity(
        productName   = productName,
        brand         = brand,
        category      = category,
        pricesJson    = gson.toJson(prices),
        cheapestAt    = cheapestAt,
        cheapestPrice = cheapestPrice,
        maxSavings    = maxSavings,
        savingsPct    = savingsPct
    )

    private fun PriceComparisonEntity.toDto() = PriceComparisonItemDto(
        productName   = productName,
        brand         = brand,
        category      = category,
        prices        = gson.fromJson(pricesJson, pricesListType),
        cheapestAt    = cheapestAt,
        cheapestPrice = cheapestPrice,
        maxSavings    = maxSavings,
        savingsPct    = savingsPct
    )
}
