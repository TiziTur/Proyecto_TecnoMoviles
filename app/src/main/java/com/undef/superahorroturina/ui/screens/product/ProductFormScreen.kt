// Formulario para agregar o editar un producto dentro de una compra.
// Sirve para los dos casos (new/edit) según si productId es null o no.
// El campo EAN tiene un FilledTonalIconButton al lado para simular el escaneo con cámara
// (el TODO real vendría después con CameraX o un Intent implícito a la cámara del sistema).
// Muestro el subtotal en tiempo real mientras el usuario escribe precio y cantidad —
// eso es lo copado de Compose, no necesito nada extra, solo calcular en la composición.
package com.undef.superahorroturina.ui.screens.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton

@Composable
fun ProductFormScreen(
    purchaseId: Int,
    productId: Int?,
    onNavigateBack: () -> Unit
) {
    val isEditing = productId != null
    val purchase  = MockData.purchases.find { it.id == purchaseId }
    val existing  = if (isEditing) purchase?.products?.find { it.id == productId } else null

    var code        by remember { mutableStateOf(existing?.code ?: "") }
    var name        by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var price       by remember { mutableStateOf(if (existing != null) existing.price.toString() else "") }
    var quantity    by remember { mutableStateOf(if (existing != null) existing.quantity.toString() else "1") }

    var priceError    by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    val title = if (isEditing) stringResource(R.string.product_edit_title)
                else           stringResource(R.string.product_new_title)

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                showBack = true,
                onBack = onNavigateBack
            )
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
            // Código EAN con botón de escaneo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.field_code)) },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    placeholder = { Text("7790895000084") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = { Text("EAN / código de barras", style = MaterialTheme.typography.labelSmall) }
                )
                FilledTonalIconButton(
                    onClick = { /* TODO: abrir cámara para escanear */ },
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
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.field_description)) },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; priceError = false },
                    label = { Text(stringResource(R.string.field_price)) },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = priceError,
                    supportingText = if (priceError) {{ Text(stringResource(R.string.error_price)) }} else null,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it; quantityError = false },
                    label = { Text(stringResource(R.string.field_quantity)) },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = quantityError,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Subtotal preview
            val priceVal = price.toDoubleOrNull() ?: 0.0
            val qtyVal   = quantity.toIntOrNull() ?: 1
            val moneyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.product_subtotal), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$ ${moneyFormat.format(priceVal * qtyVal)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            KlarityButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    if (price.toDoubleOrNull() == null) { priceError = true; return@KlarityButton }
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
