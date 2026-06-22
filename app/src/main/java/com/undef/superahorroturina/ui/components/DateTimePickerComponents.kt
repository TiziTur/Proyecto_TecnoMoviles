package com.undef.superahorroturina.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import kotlinx.coroutines.flow.filter

private val DRUM_ITEM_H = 52.dp

// Un cilindro scrolleable tipo candado: muestra 3 items, el del centro es el seleccionado.
@Composable
fun DrumPickerColumn(
    values: List<Int>,
    initialIndex: Int,
    onValueChange: (Int) -> Unit,
    format: (Int) -> String = { "%02d".format(it) },
    modifier: Modifier = Modifier
) {
    // Spacer nulo al inicio y al final para poder centrar el primer y último valor
    val padded = remember(values) { listOf<Int?>(null) + values.map { it as Int? } + listOf(null) }
    val state  = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(values.indices))
    val fling  = rememberSnapFlingBehavior(state)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Notifica al padre cuando el scroll se detiene
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                val idx = state.firstVisibleItemIndex
                if (idx in values.indices) onValueChange(values[idx])
            }
    }

    Box(modifier = modifier.height(DRUM_ITEM_H * 3)) {
        // Franja de selección en el centro
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(DRUM_ITEM_H)
                .clip(RoundedCornerShape(10.dp))
                .background(primaryColor.copy(alpha = 0.12f))
        )

        LazyColumn(
            state         = state,
            flingBehavior = fling,
            modifier      = Modifier.fillMaxSize()
        ) {
            // paddedValues: idx=0 es null, idx=k+1 es values[k]
            // Cuando firstVisibleItemIndex = k, el centro es paddedValues[k+1] = values[k]
            // → isCenter para idx cuando: firstVisibleItemIndex == idx - 1
            items(padded.size) { idx ->
                val value    = padded[idx]
                val isCenter = state.firstVisibleItemIndex == idx - 1
                Box(
                    modifier         = Modifier.height(DRUM_ITEM_H).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (value != null) {
                        Text(
                            text       = format(value),
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Light,
                            color      = if (isCenter) primaryColor
                                         else onSurfaceColor.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }

        // Degradado superior (efecto de profundidad)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DRUM_ITEM_H)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(surfaceColor, Color.Transparent)))
        )
        // Degradado inferior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DRUM_ITEM_H)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, surfaceColor)))
        )
    }
}

// Diálogo de hora tipo candado de 4 dígitos: H1 H2 : M1 M2
@Composable
fun DrumTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var h1 by remember { mutableIntStateOf(initialHour / 10) }
    var h2 by remember { mutableIntStateOf(initialHour % 10) }
    var m1 by remember { mutableIntStateOf(initialMinute / 10) }
    var m2 by remember { mutableIntStateOf(initialMinute % 10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.time_picker_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.time_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Decenas de horas: 0-2
                    DrumPickerColumn(
                        values        = (0..2).toList(),
                        initialIndex  = (initialHour / 10).coerceIn(0, 2),
                        onValueChange = { h1 = it },
                        modifier      = Modifier.weight(1f)
                    )
                    // Unidades de horas: 0-9
                    DrumPickerColumn(
                        values        = (0..9).toList(),
                        initialIndex  = initialHour % 10,
                        onValueChange = { h2 = it },
                        modifier      = Modifier.weight(1f)
                    )

                    Text(
                        text       = ":",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 4.dp)
                    )

                    // Decenas de minutos: 0-5
                    DrumPickerColumn(
                        values        = (0..5).toList(),
                        initialIndex  = initialMinute / 10,
                        onValueChange = { m1 = it },
                        modifier      = Modifier.weight(1f)
                    )
                    // Unidades de minutos: 0-9
                    DrumPickerColumn(
                        values        = (0..9).toList(),
                        initialIndex  = initialMinute % 10,
                        onValueChange = { m2 = it },
                        modifier      = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hour   = minOf(23, h1 * 10 + h2)
                val minute = m1 * 10 + m2
                onConfirm(hour, minute)
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
