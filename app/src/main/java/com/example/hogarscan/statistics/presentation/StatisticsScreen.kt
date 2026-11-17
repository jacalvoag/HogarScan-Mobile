package com.example.hogarscan.statistics.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(state, onRefresh = viewModel::refresh, modifier = modifier)
}

@Composable
fun StatisticsScreen(state: StatisticsUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Estadísticas de la cuenta", style = MaterialTheme.typography.headlineMedium)
        Text("Solo incluyen revisiones completas y evaluadas cuyos resúmenes llegaron a la API.")
        when (state) {
            StatisticsUiState.Idle -> Text("Actualiza para consultar tus estadísticas.")
            StatisticsUiState.Loading -> { CircularProgressIndicator(); Text("Consultando estadísticas…") }
            StatisticsUiState.SignInRequired -> Text("Inicia sesión para consultar las estadísticas de tu cuenta.")
            is StatisticsUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is StatisticsUiState.Data -> {
                val data = state.statistics
                if (data.totalScans == 0L) Text("Aún no hay resúmenes sincronizados.")
                Text("Revisiones: ${data.totalScans}")
                Text("Direcciones revisadas: ${data.totalAddressesScanned}")
                Text("Direcciones con respuesta: ${data.totalAddressesResponded}")
                Text("Servicios con conexión aceptada: ${data.totalAcceptedServices}")
                Text("Una misma dirección puede contarse en varias revisiones; no equivale a dispositivos únicos.")
                Text("Hallazgos por categoría", style = MaterialTheme.typography.titleMedium)
                Text("Conexión TCP aceptada: ${data.findings.tcpServiceAccessible}")
                Text("Protocolo identificado: ${data.findings.protocolIdentified}")
                Text("Revisión preventiva: ${data.findings.preventiveReview}")
                Text("Posible coincidencia CVE: ${data.findings.possibleCveMatch}")
                Text("Evidencia insuficiente: ${data.findings.insufficientEvidence}")
                Text("Una coincidencia posible no confirma una vulnerabilidad.")
                Text("Generadas: ${data.generatedAt}")
                if (data.recentScans.isNotEmpty()) Text("Revisiones recientes", style = MaterialTheme.typography.titleMedium)
                data.recentScans.forEach { scan ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Inicio: ${scan.startedAt}")
                            Text("Servicios con conexión aceptada: ${scan.acceptedServices}")
                            Text("Coincidencias CVE posibles: ${scan.findings.possibleCveMatch}")
                        }
                    }
                }
            }
        }
        if (state !is StatisticsUiState.Loading && state !is StatisticsUiState.SignInRequired) {
            Button(onClick = onRefresh) { Text("Actualizar") }
        }
    }
}
