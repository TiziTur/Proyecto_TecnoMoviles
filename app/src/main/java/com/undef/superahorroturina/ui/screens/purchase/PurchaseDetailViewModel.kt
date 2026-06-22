// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
// También maneja el flujo de OCR: escanear ticket → matchear contra la seed → confirmar inserción.
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
import com.undef.superahorroturina.data.network.dto.ScanTicketRequest
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import com.undef.superahorroturina.data.network.dto.TicketImageDto
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.ProductRepository
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    private val _ticketScanState = MutableStateFlow<TicketScanState>(TicketScanState.Idle)
    val ticketScanState: StateFlow<TicketScanState> = _ticketScanState.asStateFlow()

    fun resetTicketScan() { _ticketScanState.value = TicketScanState.Idle }

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

    // ── Ticket OCR ────────────────────────────────────────────────
    // Intenta con Gemini Vision (multi-imagen, para tickets largos escaneados en varias fotos);
    // si falla, usa ML Kit como fallback.
    fun scanTicketFromUris(context: Context, imageUris: List<Uri>, purchaseId: Int) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Scanning
            try {
                val images = mutableListOf<TicketImageDto>()
                for (imageUri in imageUris) {
                    val bytes = resizeImageForUpload(context, imageUri)
                        ?: run {
                            _ticketScanState.value = TicketScanState.Error("No se pudo leer la imagen")
                            return@launch
                        }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    images.add(TicketImageDto(base64, "image/jpeg"))
                }

                val token = session.bearerToken.first()
                val response = api.scanTicket(token, purchaseId, ScanTicketRequest(images))

                if (response.isSuccessful) {
                    val body = response.body()
                    val products = body?.products ?: emptyList()
                    if (products.isNotEmpty()) {
                        _ticketScanState.value = buildConfirmState(products, body?.supermarket)
                        return@launch
                    }
                }

                // Fallback: ML Kit OCR (sin parseo de productos — extrae texto crudo)
                mlKitFallback(context, imageUris, purchaseId)

            } catch (e: Exception) {
                try { mlKitFallback(context, imageUris, purchaseId) }
                catch (ex: Exception) {
                    _ticketScanState.value = TicketScanState.Error("Error al escanear: ${ex.message}")
                }
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
    // pasa prácticamente intacta.
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

    // ML Kit: reconoce el texto de cada foto del ticket y concatena el resultado como si fuera
    // un único documento continuo, antes de armar una lista de producto genérico con el texto
    // completo para que el usuario lo vea y ajuste manualmente.
    private suspend fun mlKitFallback(context: Context, imageUris: List<Uri>, purchaseId: Int) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val texts = mutableListOf<String>()
        for (imageUri in imageUris) {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(image).await()
            texts.add(visionText.text.trim())
        }
        val rawText = texts.joinToString("\n").trim()

        if (rawText.isBlank()) {
            _ticketScanState.value = TicketScanState.Error("No se pudo extraer texto del ticket")
            return
        }

        val parsed = mutableListOf<ScannedProductDto>()
        val priceRegex = Regex("""(\d{1,6}[.,]\d{2})""")
        rawText.lines().forEach { line ->
            val match = priceRegex.find(line)
            if (match != null) {
                val price = match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
                val name  = line.substring(0, match.range.first).trim()
                    .takeIf { it.length >= 2 } ?: "Producto"
                if (price > 0) parsed.add(ScannedProductDto(name = name, price = price))
            }
        }

        _ticketScanState.value = if (parsed.isNotEmpty()) {
            buildConfirmState(parsed, null)
        } else {
            buildConfirmState(
                listOf(ScannedProductDto(name = "Ticket escaneado", price = 0.0, description = rawText.take(200))),
                null
            )
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
