// TicketScanningOverlay.kt — pantalla completa bloqueante mientras la IA procesa el ticket.
// Es indeterminada (no hay % real: es un solo llamado a Gemini sin pasos intermedios para medir),
// pero a pantalla completa con texto explícito para que sea imposible no notar que está trabajando.
package com.undef.superahorroturina.ui.screens.purchase

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R

@Composable
fun TicketScanningOverlay() {
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
                text  = stringResource(R.string.ticket_scanning_overlay_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
