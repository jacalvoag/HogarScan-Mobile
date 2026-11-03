package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.policy.ScanPolicy

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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Configurar revisión",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Elige hasta 254 direcciones de la subred y los puertos a revisar. " +
                "La dirección del teléfono se excluirá de los intentos."
        )

        OutlinedTextField(
            value = state.startAddress,
            onValueChange = onStartChange,
            label = { Text("IPv4 inicial") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.endAddress,
            onValueChange = onEndChange,
            label = { Text("IPv4 final") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Puertos")
        ScanPolicy.allowedPorts.sorted().forEach { port ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = port in state.selectedPorts,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onPortToggle(port) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = port in state.selectedPorts,
                    onCheckedChange = null,
                    enabled = enabled
                )
                Text(
                    text = "Puerto $port",
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.notice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        when {
            scanInProgress -> Text("La configuración está bloqueada durante la revisión.")

            state.checkingNetwork -> Text("Comprobando la conexión…")

            !state.networkAvailable ->
                Text("Conecta el teléfono a una red Wi-Fi para configurar.")

            state.authorizedConfig != null -> {
                val config = state.authorizedConfig
                Text(
                    text = "Configuración autorizada",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${config.targetAddresses.size} direcciones objetivo · " +
                        "${config.plannedProbeCount} intentos TCP"
                )
                Button(onClick = onStart) {
                    Text("Iniciar revisión")
                }
                OutlinedButton(onClick = onRevoke) {
                    Text("Revocar autorización")
                }
            }

            else -> Button(onClick = onReview) {
                Text("Revisar configuración")
            }
        }
    }

    val config = state.confirmation
    if (config != null && enabled) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Autorizar configuración") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Subred: ${config.networkScope.networkAddress}" +
                            "/${config.networkScope.prefixLength}"
                    )
                    Text("Rango: ${config.startAddress} – ${config.endAddress}")
                    Text("Puertos: ${config.ports.joinToString()}")
                    Text(
                        "Direcciones objetivo: ${config.targetAddresses.size} " +
                            "(excluye el teléfono)"
                    )
                    Text("Intentos TCP previstos: ${config.plannedProbeCount}")
                    Text(
                        "Confirmo que tengo autorización para revisar " +
                            "esta red con el alcance indicado."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Autorizar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
}
