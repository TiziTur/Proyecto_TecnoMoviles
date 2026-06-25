// TicketPhotoStrip.kt — fila de miniaturas de las fotos del ticket ya guardadas como registro
// de la compra. Tocar una miniatura la abre en grande a pantalla completa.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.undef.superahorroturina.R
import com.undef.superahorroturina.data.local.db.TicketPhotoEntity

@Composable
fun TicketPhotoStrip(photos: List<TicketPhotoEntity>) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(photos) { index, photo ->
            AsyncImage(
                model              = photo.filePath,
                contentDescription = stringResource(R.string.ticket_photo_description, index + 1),
                contentScale       = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewerIndex = index }
            )
        }
    }

    viewerIndex?.let { index ->
        TicketPhotoViewerDialog(
            photos       = photos,
            initialIndex = index,
            onDismiss    = { viewerIndex = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketPhotoViewerDialog(
    photos: List<TicketPhotoEntity>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    // coerceIn por las dudas: photos viene de un Flow de Room que en teoría podría reducirse
    // mientras el diálogo está abierto (hoy no hay forma de borrar una foto individual, pero
    // evita un IndexOutOfBoundsException si eso cambia más adelante).
    val index = initialIndex.coerceIn(0, photos.lastIndex)

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ticket_photo_viewer_title, index + 1, photos.size)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = photos[index].filePath,
                    contentDescription = stringResource(R.string.ticket_photo_description, index + 1),
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
    }
}
