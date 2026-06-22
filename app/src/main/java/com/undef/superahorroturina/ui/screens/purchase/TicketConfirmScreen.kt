// TicketConfirmScreen.kt — pantalla completa para revisar los productos detectados en un ticket
// antes de guardarlos, mostrando si cada uno matchea con el catálogo de precios de referencia (seed)
// y permitiendo vincular manualmente los que no matchearon o corregir un match equivocado.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.network.dto.SeedSearchResultDto
import kotlinx.coroutines.delay
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketConfirmScreen(
    products: List<ScannedProductUi>,
    supermarket: String?,
    moneyFormat: NumberFormat,
    onSearchSeed: suspend (String) -> List<SeedSearchResultDto>,
    onLinkChange: (Int, String?) -> Unit,
    onEditProduct: (Int, String, Double, Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_confirm_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.ticket_confirm_save, products.size))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!supermarket.isNullOrBlank()) {
                Text(
                    text     = stringResource(R.string.purchase_supermarket_label, supermarket.replaceFirstChar { it.uppercaseChar() }),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(products) { index, item ->
                    ScannedProductRow(
                        item        = item,
                        moneyFormat = moneyFormat,
                        onLinkClick = { pickerIndex = index },
                        onEditClick = { editIndex = index }
                    )
                }
            }
        }
    }

    val openIndex = pickerIndex
    if (openIndex != null) {
        SeedLinkPickerSheet(
            initialCandidates = products[openIndex].seedCandidates,
            onSearch          = onSearchSeed,
            onPick            = { name -> onLinkChange(openIndex, name); pickerIndex = null },
            onUnlink          = { onLinkChange(openIndex, null); pickerIndex = null },
            onDismiss         = { pickerIndex = null }
        )
    }

    val editingIndex = editIndex
    if (editingIndex != null) {
        EditProductDialog(
            item      = products[editingIndex],
            onDismiss = { editIndex = null },
            onSave    = { name, price, quantity ->
                onEditProduct(editingIndex, name, price, quantity)
                editIndex = null
            }
        )
    }
}

@Composable
private fun ScannedProductRow(
    item: ScannedProductUi,
    moneyFormat: NumberFormat,
    onLinkClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Text(
                    text       = "${item.product.name} x${item.product.quantity}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    text       = if (item.product.price > 0) "$ ${moneyFormat.format(item.product.price)}" else "–",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_scanned_product_title),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val seedMatch = item.seedMatch
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (seedMatch != null) Color(0xFF059669).copy(alpha = 0.12f)
                        else Color(0xFFF59E0B).copy(alpha = 0.12f)
                    )
                    .clickable(onClick = onLinkClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = if (seedMatch != null) Icons.Default.CheckCircle else Icons.Default.Search,
                    contentDescription = null,
                    tint     = if (seedMatch != null) Color(0xFF059669) else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text     = seedMatch?.let { stringResource(R.string.seed_linked_to, it) } ?: stringResource(R.string.seed_no_match),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (seedMatch != null) Color(0xFF059669) else Color(0xFFF59E0B),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedLinkPickerSheet(
    initialCandidates: List<String>,
    onSearch: suspend (String) -> List<SeedSearchResultDto>,
    onPick: (String) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(initialCandidates) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = initialCandidates
        } else {
            delay(400)
            results = onSearch(query).map { it.productName }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier            = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.seed_link_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                label         = { Text(stringResource(R.string.search_product_placeholder)) },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            TextButton(onClick = onUnlink) { Text(stringResource(R.string.seed_unlink)) }
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(results) { candidate ->
                    Text(
                        text     = candidate,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.seed_no_results),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// Corrección manual de nombre/precio/cantidad cuando la IA no leyó bien el producto.
@Composable
private fun EditProductDialog(
    item: ScannedProductUi,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, quantity: Int) -> Unit
) {
    var name by remember { mutableStateOf(item.product.name) }
    var priceText by remember { mutableStateOf(item.product.price.toString()) }
    var quantityText by remember { mutableStateOf(item.product.quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_scanned_product_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.edit_scanned_product_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text(stringResource(R.string.ticket_edit_name_label)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value         = quantityText,
                        onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                        label         = { Text(stringResource(R.string.field_quantity)) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label         = { Text(stringResource(R.string.ticket_edit_price_label)) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier      = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceText.toDoubleOrNull() ?: item.product.price
                val quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: item.product.quantity
                onSave(name.trim().ifBlank { item.product.name }, price, quantity)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
