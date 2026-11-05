package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanHistoryDetailScreen(
    state: ScanDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Detalle de revisión",
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedButton(onClick = onBack) {
            Text("Volver al historial")
        }

        when (state) {
            ScanDetailUiState.Closed -> Unit

            ScanDetailUiState.Loading -> {
                CircularProgressIndicator()
                Text("Cargando revisión…")
            }

            ScanDetailUiState.NotFound -> {
                Text("La revisión no está disponible para este perfil.")
            }

            ScanDetailUiState.Error -> {
                Text(
                    "No se pudo cargar la revisión.",
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onRetry) {
                    Text("Reintentar")
                }
            }

            is ScanDetailUiState.Loaded -> {
                val record = state.record
                val scope = record.config.networkScope
                Text("Inicio: ${detailDate(record.startedAtMillis)}")
                record.finishedAtMillis?.let {
                    Text("Cierre: ${detailDate(it)}")
                }
                Text("IPv4 del teléfono: ${scope.localAddress}")
                Text("Subred: ${scope.networkAddress}/${scope.prefixLength}")

                if (record.status == ScanRecordStatus.RUNNING) {
                    Text("Resultados guardados hasta esta consulta.")
                    OutlinedButton(onClick = onRetry) {
                        Text("Actualizar detalle")
                    }
                }

                ScanResultsPanel(state = record.toDetailState())
            }
        }
    }
}

private fun ScanRecord.toDetailState() = ScanUiState(
    status = when (status) {
        ScanRecordStatus.RUNNING -> ScanStatus.RUNNING
        ScanRecordStatus.COMPLETED -> ScanStatus.COMPLETED
        ScanRecordStatus.CANCELLED -> ScanStatus.CANCELLED
        ScanRecordStatus.INTERRUPTED -> ScanStatus.INTERRUPTED
        ScanRecordStatus.FAILED -> ScanStatus.FAILED
    },
    config = config,
    results = results
)

private fun detailDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.MEDIUM
    ).format(Date(timestamp))