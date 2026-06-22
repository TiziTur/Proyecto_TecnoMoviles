package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupermarketDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(supermarkets: List<SupermarketEntity>)

    @Query("SELECT * FROM supermarkets ORDER BY name ASC")
    fun getAll(): Flow<List<SupermarketEntity>>

    @Query("SELECT COUNT(*) FROM supermarkets")
    suspend fun count(): Int

    @Query("DELETE FROM supermarkets")
    suspend fun deleteAll()

    // Reemplaza todo el contenido por la lista fresca del servidor — evita que queden
    // supermercados viejos en caché si el servidor deja de devolverlos.
    @Transaction
    suspend fun replaceAll(supermarkets: List<SupermarketEntity>) {
        deleteAll()
        insertAll(supermarkets)
    }
}
