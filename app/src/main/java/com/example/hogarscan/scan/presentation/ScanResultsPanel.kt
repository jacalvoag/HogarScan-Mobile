package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.ui.components.HogarButton
import com.example.hogarscan.ui.components.HogarCard
import com.example.hogarscan.ui.components.HogarMetric
import com.example.hogarscan.ui.components.HogarNotice
import com.example.hogarscan.ui.components.HogarProgress
import com.example.hogarscan.ui.theme.SpaceMono

internal fun ProbeOutcome.label(): String = when (this) {
    ProbeOutcome.ACCEPTED -> "Conexión aceptada"
    ProbeOutcome.REJECTED -> "Conexión rechazada"
    ProbeOutcome.TIMED_OUT -> "Tiempo agotado"
    ProbeOutcome.ERROR -> "Error"
}

@Composable
fun ScanResultsPanel(
    state: ScanUiState,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onTarget: ((Ipv4Address) -> Unit)? = null,
    details: @Composable () -> Unit = {}
) {
    val config = state.config ?: return
    val groups = state.results.groupBy { it.address }
    val responded = groups.filterValues { probes ->
        probes.any { it.outcome == ProbeOutcome.ACCEPTED || it.outcome == ProbeOutcome.REJECTED }
    }
    val accepted = state.results.count { it.outcome == ProbeOutcome.ACCEPTED }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.isActive) {
            Text("Comprobando conexiones TCP", style = MaterialTheme.typography.headlineMedium)
            Text("${config.startAddress} – ${config.endAddress}", fontFamily = SpaceMono)
            HogarProgress(state.results.size, state.plannedProbeCount,
                "${state.results.size} de ${state.plannedProbeCount} intentos registrados")
        } else {
            HogarNotice(when (state.status) {
                ScanStatus.COMPLETED -> "Revisión TCP completada"
                ScanStatus.CANCELLED -> "Revisión cancelada"
                ScanStatus.INTERRUPTED -> "Revisión interrumpida"
                ScanStatus.FAILED -> "Revisión con error"
                else -> "Revisión TCP"
            }, state.message ?: "${config.networkScope.networkAddress}/${config.networkScope.prefixLength}",
                Modifier.fillMaxWidth(), warning = state.status != ScanStatus.COMPLETED)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HogarMetric(responded.size.toString(), "Direcciones con respuesta", Modifier.weight(1f))
            HogarMetric(accepted.toString(), "Conexiones aceptadas", Modifier.weight(1f))
        }
        if (state.isActive) {
            HogarNotice("Resultados provisionales",
                state.message ?: "Los conteos pueden cambiar durante la revisión.", Modifier.fillMaxWidth())
            if (onCancel != null) HogarButton("Cancelar revisión", onCancel, Modifier.fillMaxWidth(),
                enabled = state.status != ScanStatus.CANCELLING, destructive = true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HogarMetric(groups.size.toString(), "Direcciones revisadas", Modifier.weight(1f))
                HogarMetric(state.results.count { it.outcome == ProbeOutcome.TIMED_OUT }.toString(),
                    "Intentos sin respuesta", Modifier.weight(1f))
            }
            details()
            if (onTarget != null) {
                Text("Direcciones observadas", style = MaterialTheme.typography.titleLarge)
                if (groups.isEmpty()) Text("Sin intentos registrados.")
                LazyColumn(Modifier.heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups.entries.sortedByDescending { it.key in responded }, key = { it.key.toString() }) { (address, probes) ->
                        HogarCard {
                            Text(address.toString(), fontFamily = SpaceMono)
                            Text(probes.groupingBy { it.outcome }.eachCount().entries.joinToString(" · ") {
                                "${it.value} ${it.key.label().lowercase()}"
                            })
                            HogarButton("Ver detalle", { onTarget(address) }, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
            }
        }
    }
}
