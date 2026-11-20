package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.policy.ScanPolicy
import com.example.hogarscan.ui.components.HogarButton
import com.example.hogarscan.ui.components.HogarField
import com.example.hogarscan.ui.components.HogarNotice

@Composable
fun ScanSetupForm(
    state: ScanSetupState,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onPortToggle: (Int) -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRevoke: () -> Unit,
    onStart: () -> Unit,
    scanInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    val enabled = state.networkAvailable && !state.checkingNetwork && !scanInProgress
    var portsOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Alcance", style = MaterialTheme.typography.titleLarge)
        BoxWithConstraints {
            val sideBySide = maxWidth >= 320.dp && LocalDensity.current.fontScale < 1.3f
            @Composable fun StartField(fieldModifier: Modifier) {
                HogarField(state.startAddress, onStartChange, "Desde", "IPv4 inicial", fieldModifier,
                    enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            }
            @Composable fun EndField(fieldModifier: Modifier) {
                HogarField(state.endAddress, onEndChange, "Hasta", "IPv4 final", fieldModifier,
                    enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            }
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StartField(Modifier.weight(1f))
                    EndField(Modifier.weight(1f))
                }
            } else {
                Column {
                    StartField(Modifier.fillMaxWidth())
                    EndField(Modifier.fillMaxWidth())
                }
            }
        }
        Text("Puertos TCP", style = MaterialTheme.typography.titleLarge)
        Text("Hasta 6 de los 7 puertos disponibles.", style = MaterialTheme.typography.bodySmall)
        HogarButton(
            if (state.selectedPorts.isEmpty()) "Seleccionar puertos" else
                "${state.selectedPorts.sorted().joinToString(" · ")} — Cambiar selección",
            onClick = { portsOpen = true }, modifier = Modifier.fillMaxWidth(),
            enabled = enabled, secondary = true
        )
        HogarNotice("Alcance de la revisión",
            "El número de puerto orienta sobre un posible servicio; su identificación requiere una respuesta compatible.",
            Modifier.fillMaxWidth())
        state.error?.let { HogarNotice("Revisa la configuración", it, Modifier.fillMaxWidth(), error = true) }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        when {
            scanInProgress -> Text("La configuración está bloqueada durante la revisión.")
            state.checkingNetwork -> Text("Comprobando la conexión…")
            !state.networkAvailable -> Text("Conecta el teléfono a una red Wi-Fi para configurar.")
            state.authorizedConfig != null -> {
                val config = state.authorizedConfig
                HogarNotice("Configuración autorizada",
                    "${config.targetAddresses.size} direcciones objetivo · ${config.plannedProbeCount} intentos TCP",
                    Modifier.fillMaxWidth())
                HogarButton("Iniciar revisión", onStart, Modifier.fillMaxWidth())
                HogarButton("Revocar autorización", onRevoke, Modifier.fillMaxWidth(), secondary = true)
            }
            else -> HogarButton("Confirmar autorización", onReview, Modifier.fillMaxWidth())
        }
    }

    if (portsOpen) {
        AlertDialog(onDismissRequest = { portsOpen = false },
            title = { Text("Seleccionar puertos") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Selecciona hasta seis puertos.")
                    ScanPolicy.allowedPorts.sorted().forEach { port ->
                        val selected = port in state.selectedPorts
                        Row(Modifier.fillMaxWidth().toggleable(value = selected,
                            enabled = enabled && (selected || state.selectedPorts.size < 6),
                            role = Role.Checkbox, onValueChange = { onPortToggle(port) })
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = null,
                                enabled = enabled && (selected || state.selectedPorts.size < 6))
                            Text("Puerto $port", Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { portsOpen = false }) { Text("Listo") } })
    }
    val config = state.confirmation
    if (config != null && enabled) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Autorizar configuración") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Subred: ${config.networkScope.networkAddress}/${config.networkScope.prefixLength}")
                    Text("Rango: ${config.startAddress} – ${config.endAddress}")
                    Text("Puertos: ${config.ports.joinToString()}")
                    Text("Direcciones objetivo: ${config.targetAddresses.size} (excluye el teléfono)")
                    Text("Intentos TCP previstos: ${config.plannedProbeCount}")
                    Text("Confirmo que tengo autorización para revisar esta red con el alcance indicado.")
                }
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Autorizar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    }
}
