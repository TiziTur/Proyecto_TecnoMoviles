package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceComparisonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PriceComparisonEntity>)

    @Query("SELECT * FROM price_comparisons ORDER BY productName ASC")
    fun getAll(): Flow<List<PriceComparisonEntity>>

    @Query("DELETE FROM price_comparisons")
    suspend fun deleteAll()

    // Reemplaza el caché de la vista base (sin filtros) por la página fresca del servidor.
    @Transaction
    suspend fun replaceAll(items: List<PriceComparisonEntity>) {
        deleteAll()
        insertAll(items)
    }
}
