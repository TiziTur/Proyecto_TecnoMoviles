// Detalle de una compra: carga datos reales desde PurchaseRepository.
// Incluye un Intent.ACTION_SEND para compartir el resumen de la compra
// (requisito de la segunda entrega: "al menos un Intent").
// El AlertDialog de confirmación de eliminación es un ejemplo de diálogos en Compose.
// El botón "Adjuntar ticket" abre cámara/galería y lanza el flujo de OCR con Gemini + ML Kit.
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.network.dto.ScannedProductDto
import com.undef.superahorroturina.ui.components.*
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    purchaseId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Int) -> Unit,
    onNavigateToAddProduct: (Int) -> Unit,
    onNavigateToEditProduct: (Int, Int) -> Unit,
    onNavigateToPurchaseComparison: (Int) -> Unit = {},
    viewModel: PurchaseDetailViewModel = hiltViewModel()
) {
    // Carga inicial
    LaunchedEffect(purchaseId) {
        viewModel.loadPurchase(purchaseId)
    }

    // Recarga cada vez que la pantalla vuelve al foco (ej: al volver de agregar producto)
    LifecycleResumeEffect(purchaseId) {
        viewModel.loadPurchase(purchaseId)
        onPauseOrDispose { }
    }

    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val ticketState   by viewModel.ticketScanState.collectAsStateWithLifecycle()
    val context        = LocalContext.current
    val isDark         = isSystemInDarkTheme()

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val moneyFormat   = remember { NumberFormat.getNumberInstance(Locale("es", "AR")) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTicketChooser by remember { mutableStateOf(false) }

    // Launcher para galería (pick image)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanTicketFromUri(context, it, purchaseId) }
    }

    // Lanzar chooser de cámara/galería cuando se pide
    if (showTicketChooser) {
        showTicketChooser = false
        galleryLauncher.launch("image/*")
    }

    // Diálogo de confirmación de productos escaneados
    when (val state = ticketState) {
        is TicketScanState.Confirm -> {
            TicketScanConfirmDialog(
                products    = state.products,
                supermarket = state.supermarket,
                moneyFormat = moneyFormat,
                onConfirm   = { viewModel.confirmScannedProducts(purchaseId, state.products) },
                onDismiss   = { viewModel.resetTicketScan() }
            )
        }
        is TicketScanState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetTicketScan() },
                title = { Text("Error al escanear") },
                text  = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetTicketScan() }) { Text("Cerrar") }
                }
            )
        }
        is TicketScanState.Done -> {
            LaunchedEffect(Unit) { viewModel.resetTicketScan() }
        }
        else -> Unit
    }

    // ── Diálogo de confirmación de eliminación ────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text  = { Text(stringResource(R.string.dialog_delete_purchase_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePurchase(onNavigateBack)
                }) {
                    Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(uiState.purchase?.supermarket ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // ── Intent: Compartir compra ──────────────────
                    IconButton(onClick = {
                        uiState.purchase?.let { p ->
                            val text = buildString {
                                appendLine("🛒 Compra en ${p.supermarket}")
                                appendLine("📅 ${p.date.format(dateFormatter)} ${p.time.format(timeFormatter)}")
                                appendLine("─────────────────")
                                p.products.forEach { prod ->
                                    appendLine("• ${prod.name} x${prod.quantity} — $ ${moneyFormat.format(prod.price * prod.quantity)}")
                                }
                                appendLine("─────────────────")
                                appendLine("💰 Total: $ ${moneyFormat.format(p.total)}")
                                appendLine("\nRegistrado con Super Ahorro")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Compra en ${p.supermarket}")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir compra"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir compra")
                    }
                    IconButton(onClick = { uiState.purchase?.let { onNavigateToPurchaseComparison(it.id) } }) {
                        Icon(Icons.Default.CompareArrows, contentDescription = "Comparar precios")
                    }
                    IconButton(onClick = { uiState.purchase?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { uiState.purchase?.let { onNavigateToAddProduct(it.id) } }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_product))
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.purchase == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No se pudo cargar la compra", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val purchase = uiState.purchase!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .dotPatternBackground(
                            dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                            dotRadius = 1.2f,
                            spacing   = 22f
                        )
                ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // ── Tarjeta de info general ───────────────────
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .coloredShadow(
                                    color        = MaterialTheme.colorScheme.primary,
                                    borderRadius = 16.dp,
                                    blurRadius   = 12.dp,
                                    offsetY      = 3.dp
                                )
                                .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Store, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text(purchase.supermarket, style = MaterialTheme.typography.titleMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp))
                                    Text(
                                        "${purchase.date.format(dateFormatter)} a las ${purchase.time.format(timeFormatter)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.purchase_total),
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "$ ${moneyFormat.format(purchase.total)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // ── Placeholder ticket ────────────────────────
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .coloredShadow(
                                    color        = MaterialTheme.colorScheme.secondary,
                                    borderRadius = 16.dp,
                                    blurRadius   = 8.dp,
                                    offsetY      = 2.dp
                                )
                                .glowBorder(cornerRadius = 16.dp, isDark = isDark),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Receipt, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(R.string.purchase_ticket),
                                            style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(R.string.purchase_ticket_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                // Indicador de carga mientras escanea
                                if (ticketState is TicketScanState.Scanning || ticketState is TicketScanState.Inserting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { showTicketChooser = true },
                                    enabled = ticketState !is TicketScanState.Scanning && ticketState !is TicketScanState.Inserting,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_attach),
                                        maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // ── Lista de productos ────────────────────────
                    item {
                        SectionHeader(
                            title = stringResource(R.string.purchase_products, purchase.products.size),
                            actionLabel = stringResource(R.string.action_add_product),
                            onAction = { onNavigateToAddProduct(purchase.id) }
                        )
                    }

                    if (purchase.products.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.Inventory2,
                                message = stringResource(R.string.purchase_no_products),
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(purchase.products) { product ->
                            ProductListItem(
                                code        = product.code,
                                name        = product.name,
                                description = product.description,
                                price       = "$ ${moneyFormat.format(product.price)}",
                                quantity    = product.quantity,
                                onEdit      = { onNavigateToEditProduct(purchase.id, product.id) },
                                onDelete    = { viewModel.deleteProduct(purchase.id, product.id) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
                } // dotPatternBackground Box
            }
        }
    }
}

// ── Diálogo de confirmación de productos escaneados ───────────

@Composable
private fun TicketScanConfirmDialog(
    products: List<ScannedProductDto>,
    supermarket: String?,
    moneyFormat: NumberFormat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ticket escaneado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!supermarket.isNullOrBlank()) {
                    Text(
                        text  = "Supermercado: ${supermarket.replaceFirstChar { it.uppercaseChar() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text  = "Se detectaron ${products.size} producto(s). ¿Querés agregarlos a la compra?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                products.take(8).forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "${p.name} x${p.quantity}",
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text  = if (p.price > 0) "$ ${moneyFormat.format(p.price)}" else "–",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (products.size > 8) {
                    Text(
                        text  = "… y ${products.size - 8} más",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Agregar todos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
