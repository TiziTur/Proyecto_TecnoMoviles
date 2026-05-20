// Pantalla de nueva/editar compra conectada a NewPurchaseViewModel.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.model.MockData
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseScreen(
    purchaseId: Int?,
    onNavigateBack: () -> Unit,
    onNavigateToAddProduct: (Int) -> Unit,
    viewModel: NewPurchaseViewModel = hiltViewModel()
) {
    // Carga los datos si es edición — LaunchedEffect garantiza que se ejecuta una sola vez
    LaunchedEffect(purchaseId) {
        viewModel.loadPurchase(purchaseId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = purchaseId != null
    val moneyFormat = NumberFormat.getNumberInstance(Locale("es", "AR"))

    val title = if (isEditing) stringResource(R.string.purchase_edit_title)
                else           stringResource(R.string.purchase_new_title)

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = uiState.dropdownExpanded,
                onExpandedChange = { viewModel.onDropdownExpandedChange(!uiState.dropdownExpanded) }
            ) {
                OutlinedTextField(
                    value = uiState.supermarket,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_supermarket)) },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = uiState.dropdownExpanded,
                    onDismissRequest = { viewModel.onDropdownExpandedChange(false) }
                ) {
                    MockData.supermarkets.forEach { market ->
                        DropdownMenuItem(
                            text = { Text(market) },
                            onClick = {
                                viewModel.onSupermarketChange(market)
                                viewModel.onDropdownExpandedChange(false)
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.date,
                onValueChange = { viewModel.onDateChange(it) },
                label = { Text(stringResource(R.string.field_date)) },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                placeholder = { Text("dd/MM/yyyy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = uiState.time,
                onValueChange = { viewModel.onTimeChange(it) },
                label = { Text(stringResource(R.string.field_time)) },
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                placeholder = { Text("HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
                        "$ ${moneyFormat.format(uiState.total)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onNavigateToAddProduct(purchaseId ?: 0) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_add_product),
                    maxLines = 1, softWrap = false
                )
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
