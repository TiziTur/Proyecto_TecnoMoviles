package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseScreen(
    purchaseId: Int?,
    onNavigateBack: () -> Unit,
    onNavigateToPurchaseDetail: ((Int) -> Unit)? = null,
    viewModel: NewPurchaseViewModel = hiltViewModel()
) {
    LaunchedEffect(purchaseId) { viewModel.loadPurchase(purchaseId) }

    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing   = purchaseId != null
    val isDark      = isSystemInDarkTheme()
    val moneyFormat = NumberFormat.getNumberInstance(Locale("es", "AR"))
    val title       = if (isEditing) stringResource(R.string.purchase_edit_title)
                      else           stringResource(R.string.purchase_new_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, showBack = true, onBack = onNavigateBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dotPatternBackground(
                    dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                    dotRadius = 1.2f,
                    spacing   = 22f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 20.dp,
                            blurRadius   = 16.dp,
                            offsetY      = 4.dp
                        )
                        .glowBorder(cornerRadius = 20.dp, isDark = isDark),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded        = uiState.dropdownExpanded,
                            onExpandedChange = { viewModel.onDropdownExpandedChange(!uiState.dropdownExpanded) }
                        ) {
                            OutlinedTextField(
                                value          = uiState.supermarket,
                                onValueChange  = {},
                                readOnly       = true,
                                label          = { Text(stringResource(R.string.field_supermarket)) },
                                leadingIcon    = { Icon(Icons.Default.Store, contentDescription = null) },
                                trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.dropdownExpanded) },
                                modifier       = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(
                                expanded        = uiState.dropdownExpanded,
                                onDismissRequest = { viewModel.onDropdownExpandedChange(false) }
                            ) {
                                uiState.supermarketList.forEach { market ->
                                    DropdownMenuItem(
                                        text    = { Text(market) },
                                        onClick = {
                                            viewModel.onSupermarketChange(market)
                                            viewModel.onDropdownExpandedChange(false)
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value          = uiState.date,
                                onValueChange  = { viewModel.onDateChange(it) },
                                label          = { Text(stringResource(R.string.field_date)) },
                                leadingIcon    = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                                placeholder    = { Text("dd/MM/yyyy") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier       = Modifier.weight(1f),
                                singleLine     = true,
                                shape          = MaterialTheme.shapes.medium
                            )
                            OutlinedTextField(
                                value          = uiState.time,
                                onValueChange  = { viewModel.onTimeChange(it) },
                                label          = { Text(stringResource(R.string.field_time)) },
                                leadingIcon    = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                                placeholder    = { Text("HH:mm") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier       = Modifier.weight(1f),
                                singleLine     = true,
                                shape          = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 16.dp,
                            blurRadius   = 14.dp,
                            offsetY      = 3.dp
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.purchase_total),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                stringResource(R.string.purchase_total_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            "$ ${moneyFormat.format(uiState.total)}",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }

                if (uiState.saveError.isNotBlank()) {
                    Text(
                        uiState.saveError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                KlarityButton(
                    text    = stringResource(R.string.action_save),
                    onClick = {
                        viewModel.onSave { newId ->
                            if (newId != null && onNavigateToPurchaseDetail != null)
                                onNavigateToPurchaseDetail(newId)
                            else
                                onNavigateBack()
                        }
                    },
                    loading  = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
