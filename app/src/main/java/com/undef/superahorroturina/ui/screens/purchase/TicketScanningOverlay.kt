// TicketScanningOverlay.kt — pantalla completa bloqueante mientras la IA procesa el ticket.
// Es indeterminada (no hay % real: es un solo llamado a Gemini sin pasos intermedios para medir),
// pero a pantalla completa con texto explícito para que sea imposible no notar que está trabajando.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R

// onCancel no cancela el llamado de red en curso (sigue corriendo en el viewModelScope),
// solo le devuelve el control al usuario si la espera se siente demasiado larga — los timeouts
// de OkHttp (30s conexión + 30s lectura, x3 reintentos) ya acotan el peor caso a un par de
// minutos, pero ninguna otra pantalla a pantalla completa de este flujo deja al usuario sin
// una salida explícita, y esta tampoco debería.
// text es explícito por llamada (no hardcodeado) porque este mismo overlay se usa tanto para
// "escaneando con IA" como para "guardando productos en la compra" — son pasos distintos y
// mostrar el mismo texto en los dos hacía parecer que el escaneo se reiniciaba en vez de avanzar.
@Composable
fun TicketScanningOverlay(text: String, onCancel: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text(
                text  = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
