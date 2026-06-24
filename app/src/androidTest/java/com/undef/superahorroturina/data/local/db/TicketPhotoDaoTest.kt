package com.undef.superahorroturina.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TicketPhotoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var purchaseDao: PurchaseDao
    private lateinit var ticketPhotoDao: TicketPhotoDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        purchaseDao = db.purchaseDao()
        ticketPhotoDao = db.ticketPhotoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun samplePurchase(id: Int) = PurchaseEntity(
        id = id,
        purchaseDate = "2026-06-24",
        purchaseTime = "10:00:00",
        supermarket = "Coto",
        total = 100.0,
        productCount = 0
    )

    @Test
    fun insertAll_y_getByPurchaseId_devuelve_las_fotos_ordenadas() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        ticketPhotoDao.insertAll(
            listOf(
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/2.jpg", displayOrder = 1, capturedAt = 1000L),
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L)
            )
        )

        val result = ticketPhotoDao.getByPurchaseId(1).first()

        assertEquals(2, result.size)
        assertEquals("/foo/1.jpg", result[0].filePath)
        assertEquals("/foo/2.jpg", result[1].filePath)
    }

    @Test
    fun borrar_la_compra_borra_sus_fotos_por_cascade() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        ticketPhotoDao.insertAll(
            listOf(TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L))
        )

        purchaseDao.delete(1)

        val result = ticketPhotoDao.getByPurchaseId(1).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun deleteByPurchaseId_borra_solo_las_fotos_de_esa_compra() = runBlocking {
        purchaseDao.upsert(samplePurchase(1))
        purchaseDao.upsert(samplePurchase(2))
        ticketPhotoDao.insertAll(
            listOf(
                TicketPhotoEntity(purchaseId = 1, filePath = "/foo/1.jpg", displayOrder = 0, capturedAt = 1000L),
                TicketPhotoEntity(purchaseId = 2, filePath = "/foo/2.jpg", displayOrder = 0, capturedAt = 1000L)
            )
        )

        ticketPhotoDao.deleteByPurchaseId(1)

        assertTrue(ticketPhotoDao.getByPurchaseId(1).first().isEmpty())
        assertEquals(1, ticketPhotoDao.getByPurchaseId(2).first().size)
    }
}
