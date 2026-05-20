// Formulario para agregar o editar un producto, conectado a ProductFormViewModel.
// El subtotal reactivo se calcula en ProductFormUiState.subtotal — no en el composable.
// LaunchedEffect carga los datos del producto si es edición.
package com.undef.superahorroturina.ui.screens.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductFormScreen(
    purchaseId: Int,
    productId: Int?,
    onNavigateBack: () -> Unit,
    viewModel: ProductFormViewModel = hiltViewModel()
) {
    // LaunchedEffect carga los datos del producto existente una sola vez al entrar a la pantalla
    LaunchedEffect(purchaseId, productId) {
        viewModel.loadProduct(purchaseId, productId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = productId != null
    val moneyFormat = NumberFormat.getNumberInstance(Locale("es", "AR"))

    val title = if (isEditing) stringResource(R.string.product_edit_title)
                else           stringResource(R.string.product_new_title)

    Scaffold(
        topBar = {
            AppTopBar(title = title, showBack = true, onBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.code,
                    onValueChange = { viewModel.onCodeChange(it) },
                    label = { Text(stringResource(R.string.field_code)) },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    placeholder = { Text("7790895000084") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = {
                        Text("EAN / código de barras",
                            style = MaterialTheme.typography.labelSmall)
                    }
                )
                FilledTonalIconButton(
                    onClick = { /* TODO: Intent cámara para escanear EAN */ },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Escanear código de barras",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text(stringResource(R.string.field_name)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text(stringResource(R.string.field_description)) },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.price,
                    onValueChange = { viewModel.onPriceChange(it) },
                    label = { Text(stringResource(R.string.field_price)) },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = uiState.priceError,
                    supportingText = if (uiState.priceError) {
                        { Text(stringResource(R.string.error_price)) }
                    } else null,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = { viewModel.onQuantityChange(it) },
                    label = { Text(stringResource(R.string.field_quantity)) },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.quantityError,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Subtotal reactivo — calculado en el UiState, no en el composable
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.product_subtotal),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "$ ${moneyFormat.format(uiState.subtotal)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            KlarityButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.onSave(onNavigateBack) },
                loading = uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
