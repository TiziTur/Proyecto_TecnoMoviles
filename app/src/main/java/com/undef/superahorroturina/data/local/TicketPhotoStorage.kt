// TicketPhotoStorage.kt — guarda en disco las fotos de ticket ya comprimidas y limpia los
// archivos de una compra cuando se la borra. No depende de Context/Android a propósito: recibe
// el directorio base (en producción, context.filesDir) para poder testearlo con JUnit normal,
// sin Robolectric ni un dispositivo/emulador.
package com.undef.superahorroturina.data.local

import java.io.File
import java.util.UUID

object TicketPhotoStorage {
    private const val DIR_NAME = "ticket_photos"

    fun savePhoto(baseDir: File, purchaseId: Int, bytes: ByteArray): String {
        val dir = purchaseDir(baseDir, purchaseId).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun deletePurchasePhotos(baseDir: File, purchaseId: Int) {
        purchaseDir(baseDir, purchaseId).deleteRecursively()
    }

    private fun purchaseDir(baseDir: File, purchaseId: Int): File =
        File(baseDir, "$DIR_NAME/$purchaseId")
}
