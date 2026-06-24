package com.undef.superahorroturina.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketPhotoDao {
    @Insert
    suspend fun insertAll(photos: List<TicketPhotoEntity>)

    @Query("SELECT * FROM ticket_photos WHERE purchaseId = :purchaseId ORDER BY displayOrder ASC")
    fun getByPurchaseId(purchaseId: Int): Flow<List<TicketPhotoEntity>>

    @Query("SELECT * FROM ticket_photos WHERE purchaseId = :purchaseId ORDER BY displayOrder ASC")
    suspend fun getByPurchaseIdOnce(purchaseId: Int): List<TicketPhotoEntity>

    @Query("DELETE FROM ticket_photos WHERE purchaseId = :purchaseId")
    suspend fun deleteByPurchaseId(purchaseId: Int)
}
