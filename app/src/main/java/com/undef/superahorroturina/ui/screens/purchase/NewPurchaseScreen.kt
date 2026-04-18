package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseScreen(
    purchaseId: Int?,
    onNavigateBack: () -> Unit,
    onNavigateToAddProduct: (Int) -> Unit
) {
    val isEditing = purchaseId != null
    val existing  = if (isEditing) MockData.purchases.find { it.id == purchaseId } else null

    var supermarket   by remember { mutableStateOf(existing?.supermarket ?: "") }
    var date          by remember { mutableStateOf(existing?.date?.toString() ?: "") }
    var time          by remember { mutableStateOf(existing?.time?.toString() ?: "") }
    var expanded      by remember { mutableStateOf(false) }

    val title = if (isEditing) stringResource(R.string.purchase_edit_title)
                else           stringResource(R.string.purchase_new_title)

    val totalDisplay = if (isEditing && existing != null) {
        val moneyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "AR"))
        "$ ${moneyFormat.format(existing.products.sumOf { it.price * it.quantity })}"
    } else {
        "$ 0,00"
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Supermarket selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = supermarket,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_supermarket)) },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MockData.supermarkets.forEach { market ->
                        DropdownMenuItem(
                            text = { Text(market) },
                            onClick = { supermarket = market; expanded = false }
                        )
                    }
                }
            }

            // Date field
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text(stringResource(R.string.field_date)) },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                placeholder = { Text("dd/MM/yyyy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Time field
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text(stringResource(R.string.field_time)) },
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                placeholder = { Text("HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Total (calculated)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.purchase_total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.purchase_total_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        totalDisplay,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add products button
            OutlinedButton(
                onClick = { onNavigateToAddProduct(purchaseId ?: 0) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_product))
            }

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
