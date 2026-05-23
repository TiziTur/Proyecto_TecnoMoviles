// Repositorio de supermercados: obtiene la lista desde el backend.
// Este endpoint NO requiere autenticación — es la "API externa" del TP.
// Los nombres se cachean en memoria mientras la app esté abierta.
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupermarketRepository @Inject constructor(
    private val api: ApiService
) {
    // Cache en memoria para no hacer la petición en cada apertura del dropdown
    private var cachedSupermarkets: List<String>? = null

    suspend fun getSupermarkets(): ApiResult<List<String>> {
        cachedSupermarkets?.let { return ApiResult.Success(it) }

        return runCatching {
            val response = api.getSupermarkets()
            if (response.isSuccessful) {
                val list = response.body()!!.map { it.name }
                cachedSupermarkets = list
                ApiResult.Success(list)
            } else {
                // Fallback a lista hardcodeada si el servidor falla
                ApiResult.Success(fallbackSupermarkets())
            }
        }.getOrElse {
            // Sin conexión — usar fallback local
            ApiResult.Success(fallbackSupermarkets())
        }
    }

    private fun fallbackSupermarkets() = listOf(
        "Carrefour", "Día", "Coto", "La Anónima", "Jumbo",
        "Walmart", "Vea", "Disco", "Changomas", "HiperLibertad",
        "Makro", "Otro"
    )
}
