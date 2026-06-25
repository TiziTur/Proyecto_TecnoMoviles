// Detalle de una compra: carga datos reales desde PurchaseRepository.
// Incluye un Intent.ACTION_SEND para compartir el resumen de la compra
// (requisito de la segunda entrega: "al menos un Intent").
// El AlertDialog de confirmación de eliminación es un ejemplo de diálogos en Compose.
// El botón "Adjuntar ticket" abre cámara/galería y lanza el flujo de OCR con Gemini.
package com.undef.superahorroturina.ui.screens.purchase

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity
import com.undef.superahorroturina.ui.components.*
import java.io.File
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
    val ticketPhotos  by remember(purchaseId) { viewModel.ticketPhotosFlow(purchaseId) }
        .collectAsStateWithLifecycle(initialValue = emptyList<TicketPhotoEntity>())
    val context        = LocalContext.current
    val isDark         = isSystemInDarkTheme()

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val moneyFormat   = remember { NumberFormat.getNumberInstance(Locale("es", "AR")) }

    // Pantalla completa de escaneo (reemplaza el flujo normal mientras se está escaneando o
    // insertando productos). Se chequea antes que Confirm para que el overlay tape la pantalla
    // mientras el escaneo está en curso.
    if (ticketState is TicketScanState.Scanning || ticketState is TicketScanState.Inserting) {
        val overlayText = if (ticketState is TicketScanState.Inserting)
            stringResource(R.string.ticket_inserting_overlay_text)
        else
            stringResource(R.string.ticket_scanning_overlay_text)
        TicketScanningOverlay(text = overlayText, onCancel = { viewModel.resetTicketScan() })
        return
    }

    // Pantalla completa de confirmación (reemplaza el flujo normal mientras hay productos para revisar).
    // Retorna antes de declarar showDeleteDialog/stagedPhotos — es seguro porque el diálogo de
    // borrado es modal y bloquea el botón "Adjuntar ticket" mientras está abierto, por lo que nunca
    // pueden coexistir una confirmación de ticket y un diálogo abierto.
    if (ticketState is TicketScanState.Confirm) {
        val confirmState = ticketState as TicketScanState.Confirm
        TicketConfirmScreen(
            products     = confirmState.items,
            supermarket  = confirmState.supermarket,
            moneyFormat  = moneyFormat,
            onSearchSeed = { query -> viewModel.searchSeedProducts(query) },
            onLinkChange = { index, name -> viewModel.updateSeedLink(index, name) },
            onEditProduct = { index, name, price, quantity -> viewModel.updateScannedProduct(index, name, price, quantity) },
            onConfirm    = { viewModel.confirmScannedProducts(purchaseId, confirmState.items) },
            onCancel     = { viewModel.resetTicketScan() }
        )
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    // Fotos del ticket adjuntadas pero todavía no enviadas a escanear (puede ser más de una,
    // para tickets largos que no entran en una sola foto). Se mantiene como estado local de la
    // pantalla, separado de TicketScanState, porque es una etapa previa al escaneo en sí.
    var stagedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Launcher de galería: permite seleccionar una o varias fotos a la vez.
    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            stagedPhotos = stagedPhotos + uris
        }
    }

    // Launcher de cámara: la app de cámara del sistema escribe la foto en el Uri que le
    // pasamos (vía FileProvider) y nos avisa con un booleano de éxito, no con el Uri en sí.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { stagedPhotos = stagedPhotos + it }
        }
        pendingCameraUri = null
    }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    fun launchCamera() {
        val dir = File(context.cacheDir, "ticket_photos").also { it.mkdirs() }
        val file = File(dir, "ticket_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // Pantalla de preview de fotos staged (reemplaza el flujo normal mientras hay fotos
    // cargadas pero no enviadas). Permite agregar más fotos o quitar alguna antes de escanear.
    if (stagedPhotos.isNotEmpty()) {
        TicketPhotosPreviewScreen(
            photos   = stagedPhotos,
            onRemove = { index -> stagedPhotos = stagedPhotos.toMutableList().also { it.removeAt(index) } },
            onAddMore = { showPhotoSourceDialog = true },
            onSave = {
                val photosToSave = stagedPhotos
                stagedPhotos = emptyList()
                viewModel.savePhotosForPurchase(context, photosToSave, purchaseId)
            },
            onCancel = { stagedPhotos = emptyList() }
        )
        return
    }

    // Diálogo de error de escaneo / auto-reset al confirmar
    when (val state = ticketState) {
        is TicketScanState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetTicketScan() },
                title = { Text(stringResource(R.string.dialog_scan_error_title)) },
                text  = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetTicketScan() }) { Text(stringResource(R.string.action_close)) }
                }
            )
        }
        is TicketScanState.Done -> {
            LaunchedEffect(Unit) { viewModel.resetTicketScan() }
        }
        else -> Unit
    }

    // ── Diálogo para elegir cámara o galería al adjuntar un ticket ─────
    if (showPhotoSourceDialog) {
        PhotoSourceChooserDialog(
            onDismiss = { showPhotoSourceDialog = false },
            onTakePhoto = {
                showPhotoSourceDialog = false
                launchCamera()
            },
            onChooseGallery = {
                showPhotoSourceDialog = false
                multiPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
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
                    IconButton(onClick = {
                        uiState.purchase?.let { p ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Compra en ${p.supermarket}")
                                putExtra(Intent.EXTRA_TEXT, buildShareText(p, dateFormatter, timeFormatter, moneyFormat))
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir compra"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share_purchase))
                    }
                    IconButton(onClick = { uiState.purchase?.let { onNavigateToPurchaseComparison(it.id) } }) {
                        Icon(Icons.Default.CompareArrows, contentDescription = stringResource(R.string.action_compare_prices))
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
                    Text(stringResource(R.string.purchase_load_error), color = MaterialTheme.colorScheme.error)
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

                    item {
                        PurchaseInfoCard(
                            purchase      = purchase,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            moneyFormat   = moneyFormat,
                            isDark        = isDark
                        )
                    }

                    item {
                        TicketAttachCard(
                            photos        = ticketPhotos,
                            isDark        = isDark,
                            onAttachClick = { showPhotoSourceDialog = true },
                            onManualClick = { onNavigateToAddProduct(purchase.id) },
                            onAiClick     = { viewModel.scanTicketFromSavedPhotos(purchase.id) }
                        )
                    }

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

// ── Diálogo para elegir cámara o galería al adjuntar un ticket ───────────────

@Composable
private fun PhotoSourceChooserDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ticket_attach_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_take_photo))
                }
                TextButton(onClick = onChooseGallery, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_choose_from_gallery))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ── Texto para compartir la compra por Intent ─────────────────────────────────

private fun buildShareText(
    p: com.undef.superahorroturina.model.Purchase,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    moneyFormat: NumberFormat
): String = buildString {
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

// ── Tarjeta de info general (supermercado, fecha/hora, total) ────────────────

@Composable
private fun PurchaseInfoCard(
    purchase: com.undef.superahorroturina.model.Purchase,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    moneyFormat: NumberFormat,
    isDark: Boolean
) {
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
                    stringResource(R.string.purchase_date_at_time, purchase.date.format(dateFormatter), purchase.time.format(timeFormatter)),
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

// ── Placeholder de ticket + botón adjuntar ────────────────────────────────────

@Composable
private fun TicketAttachCard(
    photos: List<TicketPhotoEntity>,
    isDark: Boolean,
    onAttachClick: () -> Unit,
    onManualClick: () -> Unit,
    onAiClick: () -> Unit
) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (photos.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    FilledTonalButton(
                        onClick = onAttachClick,
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
            } else {
                Text(stringResource(R.string.ticket_photos_saved_title),
                    style = MaterialTheme.typography.titleSmall)
                TicketPhotoStrip(photos = photos)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onManualClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_choice_manual),
                            maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick  = onAiClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_choice_ai),
                            maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
