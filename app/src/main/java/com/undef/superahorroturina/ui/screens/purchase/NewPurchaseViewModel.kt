// ViewModel de nueva/editar compra conectado al PurchaseRepository real.
// Al crear una compra nueva, se guarda primero el encabezado (supermercado, fecha, hora)
// y los productos se agregan después en ProductFormScreen.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.data.repository.SupermarketRepository
import com.undef.superahorroturina.ui.state.NewPurchaseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NewPurchaseViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val supermarketRepository: SupermarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewPurchaseUiState())
    val uiState: StateFlow<NewPurchaseUiState> = _uiState.asStateFlow()

    // ID de la compra que se está editando (null = nueva)
    private var editingPurchaseId: Int? = null

    init {
        loadSupermarkets()
        // Pre-cargar fecha y hora actuales para nueva compra
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val now   = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        _uiState.value = _uiState.value.copy(date = today, time = now)
    }

    private fun loadSupermarkets() {
        viewModelScope.launch {
            when (val result = supermarketRepository.getSupermarkets()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(supermarketList = result.data)
                is ApiResult.Error   -> { /* usa fallback ya cargado en el repositorio */ }
            }
        }
    }

    fun loadPurchase(purchaseId: Int?) {
        if (purchaseId == null) return
        editingPurchaseId = purchaseId
        viewModelScope.launch {
            when (val result = purchaseRepository.getPurchase(purchaseId)) {
                is ApiResult.Success -> {
                    val p = result.data
                    _uiState.value = _uiState.value.copy(
                        supermarket = p.supermarket,
                        date        = p.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        time        = p.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                        products    = p.products
                    )
                }
                is ApiResult.Error -> { /* mantiene estado actual */ }
            }
        }
    }

    fun onSupermarketChange(value: String)          { _uiState.value = _uiState.value.copy(supermarket = value) }
    fun onDateChange(value: String)                 { _uiState.value = _uiState.value.copy(date = value) }
    fun onTimeChange(value: String)                 { _uiState.value = _uiState.value.copy(time = value) }
    fun onDropdownExpandedChange(expanded: Boolean) { _uiState.value = _uiState.value.copy(dropdownExpanded = expanded) }

    fun onSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.supermarket.isBlank() || state.date.isBlank()) return

        // Convertir fecha dd/MM/yyyy → yyyy-MM-dd para el backend
        val dateFmt = runCatching {
            val parsed = java.time.LocalDate.parse(state.date, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrElse { state.date }

        val timeFmt = if (state.time.length == 5) "${state.time}:00" else state.time

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val result = if (editingPurchaseId != null) {
                purchaseRepository.updatePurchase(editingPurchaseId!!, state.supermarket, dateFmt, timeFmt)
            } else {
                purchaseRepository.createPurchase(state.supermarket, dateFmt, timeFmt)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveError = result.message)
                }
            }
        }
    }
}
