package com.undef.superahorroturina.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TicketPhotoStorageTest {

    @Test
    fun `savePhoto escribe los bytes en filesDir-ticket_photos-purchaseId y devuelve la ruta`() {
        val baseDir = createTempDir()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val path = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 7, bytes = bytes)

        val file = File(path)
        assertTrue(file.exists())
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(file.parentFile!!.path.replace('\\', '/').endsWith("ticket_photos/7"))
    }

    @Test
    fun `dos llamadas a savePhoto para la misma compra generan archivos distintos`() {
        val baseDir = createTempDir()

        val path1 = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(1))
        val path2 = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(2))

        assertTrue(path1 != path2)
        assertTrue(File(path1).exists())
        assertTrue(File(path2).exists())
    }

    @Test
    fun `deletePurchasePhotos borra el directorio de esa compra`() {
        val baseDir = createTempDir()
        val path = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 3, bytes = byteArrayOf(9))

        TicketPhotoStorage.deletePurchasePhotos(baseDir, purchaseId = 3)

        assertFalse(File(path).exists())
    }

    @Test
    fun `deletePurchasePhotos no afecta los archivos de otra compra`() {
        val baseDir = createTempDir()
        val keepPath = TicketPhotoStorage.savePhoto(baseDir, purchaseId = 1, bytes = byteArrayOf(1))
        TicketPhotoStorage.savePhoto(baseDir, purchaseId = 2, bytes = byteArrayOf(2))

        TicketPhotoStorage.deletePurchasePhotos(baseDir, purchaseId = 2)

        assertTrue(File(keepPath).exists())
    }
}
