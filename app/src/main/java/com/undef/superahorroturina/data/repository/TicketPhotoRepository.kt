// TicketPhotoRepository.kt — junta TicketPhotoDao (filas en Room) y TicketPhotoStorage
// (archivos en disco) detrás de una sola API para el ViewModel. Las fotos de ticket son
// puramente locales (no se sincronizan con el backend), por eso no hay llamadas a ApiService aquí.
package com.undef.superahorroturina.data.repository

import android.content.Context
import com.undef.superahorroturina.data.local.TicketPhotoStorage
import com.undef.superahorroturina.data.local.db.TicketPhotoDao
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ticketPhotoDao: TicketPhotoDao
) {
    fun getPhotos(purchaseId: Int): Flow<List<TicketPhotoEntity>> =
        ticketPhotoDao.getByPurchaseId(purchaseId)

    suspend fun getPhotosOnce(purchaseId: Int): List<TicketPhotoEntity> =
        ticketPhotoDao.getByPurchaseIdOnce(purchaseId)

    suspend fun savePhotos(purchaseId: Int, photoBytes: List<ByteArray>) {
        val now = System.currentTimeMillis()
        val entities = photoBytes.mapIndexed { index, bytes ->
            val path = TicketPhotoStorage.savePhoto(context.filesDir, purchaseId, bytes)
            TicketPhotoEntity(purchaseId = purchaseId, filePath = path, displayOrder = index, capturedAt = now)
        }
        ticketPhotoDao.insertAll(entities)
    }

    suspend fun deletePhotosForPurchase(purchaseId: Int) {
        ticketPhotoDao.deleteByPurchaseId(purchaseId)
        TicketPhotoStorage.deletePurchasePhotos(context.filesDir, purchaseId)
    }
}
