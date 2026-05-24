// ViewModel para el detalle de una compra.
// Carga la compra con sus productos desde el backend.
// También maneja el flujo de OCR: escanear ticket → mostrar productos detectados → confirmar inserción.
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Context
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

// Estado del flujo de escaneo de ticket
sealed class TicketScanState {
    object Idle : TicketScanState()
    object Scanning : TicketScanState()
    data class Confirm(val products: List<ScannedProductDto>, val supermarket: String?) : TicketScanState()
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
    // Intenta con Gemini Vision; si falla, usa ML Kit como fallback.
    fun scanTicketFromUri(context: Context, imageUri: Uri, purchaseId: Int) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Scanning
            try {
                // 1. Leer bytes e intentar con Gemini Vision
                val bytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
                    ?: run {
                        _ticketScanState.value = TicketScanState.Error("No se pudo leer la imagen")
                        return@launch
                    }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"

                val token = session.bearerToken.first()
                val response = api.scanTicket(token, purchaseId, ScanTicketRequest(base64, mimeType))

                if (response.isSuccessful) {
                    val body = response.body()
                    val products = body?.products ?: emptyList()
                    if (products.isNotEmpty()) {
                        _ticketScanState.value = TicketScanState.Confirm(products, body?.supermarket)
                        return@launch
                    }
                }

                // 2. Fallback: ML Kit OCR (sin parseo de productos — extrae texto crudo)
                mlKitFallback(context, imageUri, purchaseId)

            } catch (e: Exception) {
                // Si Gemini falla por red/timeout, intentar ML Kit
                try { mlKitFallback(context, imageUri, purchaseId) }
                catch (ex: Exception) {
                    _ticketScanState.value = TicketScanState.Error("Error al escanear: ${ex.message}")
                }
            }
        }
    }

    // ML Kit: reconoce el texto del ticket y arma una lista de producto genérico
    // con el texto completo para que el usuario lo vea y ajuste manualmente.
    private suspend fun mlKitFallback(context: Context, imageUri: Uri, purchaseId: Int) {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(image).await()
        val rawText = visionText.text.trim()

        if (rawText.isBlank()) {
            _ticketScanState.value = TicketScanState.Error("No se pudo extraer texto del ticket")
            return
        }

        // Intentar parsear líneas como "Nombre    precio" simples
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
            TicketScanState.Confirm(parsed, null)
        } else {
            // Mostrar el texto como un único producto para revisión manual
            TicketScanState.Confirm(
                listOf(ScannedProductDto(name = "Ticket escaneado", price = 0.0, description = rawText.take(200))),
                null
            )
        }
    }

    // Confirmar e insertar los productos detectados en la compra
    fun confirmScannedProducts(purchaseId: Int, products: List<ScannedProductDto>) {
        viewModelScope.launch {
            _ticketScanState.value = TicketScanState.Inserting
            try {
                products.forEach { p ->
                    productRepository.createProduct(
                        purchaseId  = purchaseId,
                        code        = p.code,
                        name        = p.name,
                        description = p.description,
                        price       = p.price,
                        quantity    = p.quantity
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
