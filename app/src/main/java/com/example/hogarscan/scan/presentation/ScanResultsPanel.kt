package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.model.ProbeOutcome

@Composable
fun ScanResultsPanel(
    state: ScanUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.status == ScanStatus.IDLE) return

    val config = state.config ?: return
    val finished = state.results.size
    val planned = state.plannedProbeCount
    val progress = if (planned > 0) {
        (finished.toFloat() / planned).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Revisión TCP",
            style = MaterialTheme.typography.titleLarge
        )
        Text("Rango: ${config.startAddress} – ${config.endAddress}")
        Text("Puertos: ${config.ports.joinToString()}")

        Text(
            text = when (state.status) {
                ScanStatus.IDLE -> ""
                ScanStatus.STARTING -> "Preparando revisión"
                ScanStatus.RUNNING -> "Revisión en curso"
                ScanStatus.CANCELLING -> "Deteniendo revisión"
                ScanStatus.COMPLETED -> "Revisión local completada"
                ScanStatus.CANCELLED -> "Revisión cancelada"
                ScanStatus.INTERRUPTED -> "Revisión interrumpida"
                ScanStatus.FAILED -> "Revisión con error"
            },
            style = MaterialTheme.typography.titleMedium
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Intentos registrados: $finished de $planned")
        state.message?.let { Text(it) }

        if (state.isActive) {
            OutlinedButton(
                onClick = onCancel,
                enabled = state.status != ScanStatus.CANCELLING
            ) {
                Text("Cancelar revisión")
            }
        }

        Text(
            "Aceptados: ${state.results.count { it.outcome == ProbeOutcome.ACCEPTED }} · " +
                "Rechazados: ${state.results.count { it.outcome == ProbeOutcome.REJECTED }}"
        )
        Text(
            "Sin respuesta dentro del tiempo: " +
                "${state.results.count { it.outcome == ProbeOutcome.TIMED_OUT }} · " +
                "Errores: ${state.results.count { it.outcome == ProbeOutcome.ERROR }}"
        )
        Text(
            "Un tiempo de espera agotado no confirma ausencia de un equipo. " +
                "Una conexión aceptada no demuestra una vulnerabilidad.",
            style = MaterialTheme.typography.bodySmall
        )

        if (state.results.isEmpty()) {
            Text("Sin resultados registrados.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("IPv4", Modifier.weight(1.6f))
                Text("Puerto", Modifier.weight(0.7f))
                Text("Resultado", Modifier.weight(1.4f))
                Text("ms", Modifier.weight(0.6f))
            }
            // Altura definida para anidar la lista en la pantalla desplazable.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.results,
                    key = { "${it.address}:${it.port}" }
                ) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            result.address.toString(),
                            Modifier.weight(1.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            result.port.toString(),
                            Modifier.weight(0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            when (result.outcome) {
                                ProbeOutcome.ACCEPTED -> "Aceptada"
                                ProbeOutcome.REJECTED -> "Rechazada"
                                ProbeOutcome.TIMED_OUT -> "Tiempo agotado"
                                ProbeOutcome.ERROR -> "Error"
                            },
                            Modifier.weight(1.4f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            result.durationMillis.toString(),
                            Modifier.weight(0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
