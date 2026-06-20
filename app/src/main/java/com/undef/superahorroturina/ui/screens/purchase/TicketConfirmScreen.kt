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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var pickerIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar productos") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Confirmar y guardar (${products.size})")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!supermarket.isNullOrBlank()) {
                Text(
                    text     = "Supermercado: ${supermarket.replaceFirstChar { it.uppercaseChar() }}",
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
                        onLinkClick = { pickerIndex = index }
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
}

@Composable
private fun ScannedProductRow(
    item: ScannedProductUi,
    moneyFormat: NumberFormat,
    onLinkClick: () -> Unit
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
                    text     = seedMatch?.let { "Vinculado a $it" } ?: "Sin coincidencia — tocar para buscar",
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
            Text("Vincular con el catálogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                label         = { Text("Buscar producto…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            TextButton(onClick = onUnlink) { Text("No vincular este producto") }
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
                            "Sin resultados",
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
