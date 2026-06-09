package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(purchases: List<PurchaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC, purchaseTime DESC")
    fun getAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getById(id: Int): PurchaseEntity?

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
}
