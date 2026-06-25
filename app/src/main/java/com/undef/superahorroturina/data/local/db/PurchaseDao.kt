package com.undef.superahorroturina.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    // @Upsert (no @Insert(onConflict = REPLACE)) a propósito: REPLACE resuelve el conflicto de
    // PK borrando la fila vieja e insertando una nueva, lo que dispara el ON DELETE CASCADE hacia
    // products y ticket_photos en CADA reload de una compra ya existente. Para products no se
    // notaba porque se re-insertan en el mismo request; para ticket_photos (que no tienen de dónde
    // re-sincronizarse) significaba perder las fotos en cada refresh. @Upsert hace un UPDATE real
    // sobre la fila existente, sin pasar por el DELETE.
    @Upsert
    suspend fun upsertAll(purchases: List<PurchaseEntity>)

    @Upsert
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
