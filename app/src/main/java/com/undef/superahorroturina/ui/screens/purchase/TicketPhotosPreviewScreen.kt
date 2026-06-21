// TicketPhotosPreviewScreen.kt — pantalla completa para revisar las fotos de un ticket largo
// (uno o más segmentos del mismo ticket físico) antes de enviarlas juntas a escanear.
// Permite quitar una foto mal tomada y agregar más fotos antes de confirmar el escaneo.
package com.undef.superahorroturina.ui.screens.purchase

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPhotosPreviewScreen(
    photos: List<Uri>,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fotos del ticket (${photos.size})") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onAddMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar más fotos")
                    }
                    Button(
                        onClick  = onScan,
                        enabled  = photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Escanear ticket (${photos.size})")
                    }
                }
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay fotos cargadas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "Si el ticket es largo, sacá una foto por tramo: se analizan todas juntas como un solo ticket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(photos) { index, uri ->
                    TicketPhotoThumbnail(
                        uri      = uri,
                        index    = index,
                        onRemove = { onRemove(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketPhotoThumbnail(
    uri: Uri,
    index: Int,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            model              = uri,
            contentDescription = "Foto ${index + 1} del ticket",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text     = "${index + 1}",
                color    = Color.White,
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar foto ${index + 1}",
                tint     = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
