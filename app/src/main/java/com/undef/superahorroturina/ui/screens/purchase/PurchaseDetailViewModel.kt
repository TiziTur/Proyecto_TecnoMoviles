// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
// También maneja el flujo de fotos de ticket: guardarlas como registro de la compra,
// y desde ahí cargar los productos a mano o pedirle ayuda a la IA (escanear → matchear
// contra la seed → confirmar inserción).
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
import com.undef.superahorroturina.data.network.dto.ScanTicketRequest
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.network.dto.TicketImageDto
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.data.repository.TicketPhotoRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val purchase: Purchase? = null,
    val error: String = ""
)

// Un producto detectado en el ticket junto con su estado de vínculo a la seed.
// seedMatch = nombre exacto de reference_prices.product_name, o null si no está vinculado.
data class ScannedProductUi(
    val product: ScannedProductDto,
    val seedMatch: String? = null,
    val seedCandidates: List<String> = emptyList()
)

// Estado del flujo de escaneo de ticket
sealed class TicketScanState {
    object Idle : TicketScanState()
    object Scanning : TicketScanState()
    data class Confirm(val items: List<ScannedProductUi>, val supermarket: String?) : TicketScanState()
    object Inserting : TicketScanState()
    data class Error(val message: String) : TicketScanState()
    object Done : TicketScanState()
}

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val productRepository: ProductRepository,
    private val ticketPhotoRepository: TicketPhotoRepository,
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    private val _ticketScanState = MutableStateFlow<TicketScanState>(TicketScanState.Idle)
    val ticketScanState: StateFlow<TicketScanState> = _ticketScanState.asStateFlow()

    fun resetTicketScan() { _ticketScanState.value = TicketScanState.Idle }

    // Fotos del ticket ya guardadas como registro de esta compra (Room, reactivo).
    fun ticketPhotosFlow(purchaseId: Int): Flow<List<TicketPhotoEntity>> =
        ticketPhotoRepository.getPhotos(purchaseId)

    fun loadPurchase(purchaseId: Int) {
        viewModelScope.launch {
            _uiState.value = PurchaseDetailUiState(isLoading = true)
            when (val result = purchaseRepository.getPurchase(purchaseId)) {
                is ApiResult.Success -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    purchase  = result.data
                )
                is ApiResult.Error   -> _uiState.value = PurchaseDetailUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }

    fun deletePurchase(onSuccess: () -> Unit) {
        val id = _uiState.value.purchase?.id ?: return
        viewModelScope.launch {
            purchaseRepository.deletePurchase(id)
            onSuccess()
        }
    }

    fun deleteProduct(purchaseId: Int, productId: Int) {
        viewModelScope.launch {
            productRepository.deleteProduct(purchaseId, productId)
            // Recargar la compra para reflejar el nuevo total y lista de productos
            loadPurchase(purchaseId)
        }
    }

    // ── Fotos del ticket ──────────────────────────────────────────
    // Guarda las fotos staged como registro permanente de la compra. No escanea nada todavía:
    // eso es una decisión separada que el usuario toma después (botón "Ayudame con IA").
    fun savePhotosForPurchase(context: Context, imageUris: List<Uri>, purchaseId: Int) {
        viewModelScope.launch {
            try {
                val photoBytes = imageUris.map { uri ->
                    resizeImageForUpload(context, uri)
                        ?: run {
                            _ticketScanState.value = TicketScanState.Error("No se pudo leer la imagen")
                            return@launch
                        }
                }
                ticketPhotoRepository.savePhotos(purchaseId, photoBytes)
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("No se pudieron guardar las fotos: ${e.message}")
            }
        }
    }

    // ── Ticket OCR ────────────────────────────────────────────────
    // Antes había un fallback silencioso a ML Kit (OCR de texto crudo + un regex de precios) si
    // Gemini fallaba por cualquier motivo — eso convertía cualquier error transitorio (un 503 de
    // Gemini, un redeploy del backend, una mala conexión) en una pantalla de confirmación con
    // basura que parecía un escaneo exitoso. Ahora reintenta la llamada real un par de veces y,
    // si de verdad no se pudo escanear, lo dice — no inventa productos falsos.
    // Lee las fotos ya persistidas (guardadas por savePhotosForPurchase) en vez de URIs
    // transitorias — el usuario puede pedir esto en cualquier momento, no solo justo después
    // de sacar la foto.
    fun scanTicketFromSavedPhotos(purchaseId: Int) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Scanning
            try {
                val savedPhotos = ticketPhotoRepository.getPhotosOnce(purchaseId)
                if (savedPhotos.isEmpty()) {
                    _ticketScanState.value = TicketScanState.Error("No hay fotos del ticket guardadas para escanear")
                    return@launch
                }

                val images = savedPhotos.map { photo ->
                    val bytes = File(photo.filePath).readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    TicketImageDto(base64, "image/jpeg")
                }

                val token = session.bearerToken.first()
                val request = ScanTicketRequest(images)

                val maxAttempts = 3
                for (attempt in 1..maxAttempts) {
                    val response = try {
                        api.scanTicket(token, purchaseId, request)
                    } catch (e: Exception) {
                        if (attempt == maxAttempts) {
                            _ticketScanState.value = TicketScanState.Error(
                                "No se pudo conectar para escanear el ticket. Revisá tu conexión e intentá de nuevo."
                            )
                            return@launch
                        }
                        delay(attempt * 1500L)
                        continue
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        val products = body?.products ?: emptyList()
                        if (products.isNotEmpty()) {
                            _ticketScanState.value = buildConfirmState(products, body?.supermarket)
                        } else {
                            _ticketScanState.value = TicketScanState.Error(
                                "No se reconoció ningún producto en el ticket. Probá con fotos más nítidas."
                            )
                        }
                        return@launch
                    }

                    if (attempt == maxAttempts) {
                        _ticketScanState.value = TicketScanState.Error(
                            "No se pudo escanear el ticket (error del servidor). Intentá de nuevo en unos segundos."
                        )
                        return@launch
                    }
                    delay(attempt * 1500L)
                }
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("Error al escanear: ${e.message}")
            }
        }
    }

    // Las fotos de cámara salen a resolución completa (varios MB cada una) — subirlas crudas
    // hace que el body JSON supere el límite del backend y la llamada a Gemini falle en silencio
    // (cae al fallback de ML Kit, que es mucho peor). Las reescalamos a un ancho máximo razonable
    // para OCR y las recomprimimos a JPEG antes de mandarlas, corrigiendo además la rotación EXIF
    // (al recomprimir se pierden los metadatos, así que hay que rotar los píxeles a mano).
    // maxDimension generoso a propósito: el texto de un ticket térmico es chico y cualquier
    // downscale agresivo le come legibilidad al OCR. Esto solo actúa como freno para fotos
    // extremas (cámaras de 50+ MP); una foto de celular típica (3000-6000px de lado largo)
    // pasa prácticamente intacta. Esta misma versión comprimida es la que se persiste como
    // registro de la compra (no se guarda el original sin comprimir).
    private fun resizeImageForUpload(context: Context, uri: Uri, maxDimension: Int = 6000, quality: Int = 90): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null

        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else original

        val scale = maxDimension.toFloat() / maxOf(rotated.width, rotated.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
        } else rotated

        return java.io.ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    // Llama a /products/match-seed para los productos detectados y arma el estado de confirmación
    // con el resultado del matching (auto-vinculado o candidatos para elegir manualmente).
    private suspend fun buildConfirmState(products: List<ScannedProductDto>, supermarket: String?): TicketScanState {
        val matchResult = productRepository.matchSeed(products.map { it.name })
        val matches = (matchResult as? ApiResult.Success)?.data
        val items = products.mapIndexed { index, p ->
            val match = matches?.getOrNull(index)
            ScannedProductUi(
                product        = p,
                seedMatch      = match?.seedMatch,
                seedCandidates = match?.candidates ?: emptyList()
            )
        }
        return TicketScanState.Confirm(items, supermarket)
    }

    // Cambia o quita el vínculo de un producto a la seed (elegido a mano por el usuario).
    fun updateSeedLink(index: Int, seedProductName: String?) {
        val current = _ticketScanState.value
        if (current is TicketScanState.Confirm) {
            val updated = current.items.toMutableList()
            updated[index] = updated[index].copy(seedMatch = seedProductName)
            _ticketScanState.value = current.copy(items = updated)
        }
    }

    // Corrección manual de un producto detectado por la IA (nombre, precio o cantidad mal leídos).
    fun updateScannedProduct(index: Int, name: String, price: Double, quantity: Int) {
        val current = _ticketScanState.value
        if (current is TicketScanState.Confirm) {
            val updated = current.items.toMutableList()
            val item = updated[index]
            updated[index] = item.copy(product = item.product.copy(name = name, price = price, quantity = quantity))
            _ticketScanState.value = current.copy(items = updated)
        }
    }

    // Búsqueda libre en el catálogo para el buscador manual de vínculo.
    suspend fun searchSeedProducts(query: String): List<SeedSearchResultDto> {
        val result = productRepository.searchSeedProducts(query)
        return (result as? ApiResult.Success)?.data ?: emptyList()
    }

    // Confirmar e insertar los productos detectados en la compra, con su vínculo a la seed (si lo hay).
    fun confirmScannedProducts(purchaseId: Int, items: List<ScannedProductUi>) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Inserting
            try {
                items.forEach { item ->
                    val p = item.product
                    productRepository.createProduct(
                        purchaseId      = purchaseId,
                        code            = p.code,
                        name            = p.name,
                        description     = p.description,
                        price           = p.price,
                        quantity        = p.quantity,
                        category        = p.category,
                        seedProductName = item.seedMatch
                    )
                }
                _ticketScanState.value = TicketScanState.Done
                loadPurchase(purchaseId)
            } catch (e: Exception) {
                _ticketScanState.value = TicketScanState.Error("Error al guardar productos: ${e.message}")
            }
        }
    }
}
