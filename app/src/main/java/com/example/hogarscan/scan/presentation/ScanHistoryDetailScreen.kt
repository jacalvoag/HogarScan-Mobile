package com.example.hogarscan.scan.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.ui.components.*
import com.example.hogarscan.ui.theme.SpaceMono
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanHistoryDetailScreen(
    state: ScanDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onEvaluate: (() -> Unit)? = null,
    evaluationContent: @Composable (ScanRecord) -> Unit = {}
) {
    val record = (state as? ScanDetailUiState.Loaded)?.record
    var target by rememberSaveable(record?.ownerId, record?.id) { mutableStateOf<String?>(null) }
    val probes = record?.results?.filter { it.address.toString() == target }.orEmpty()
    val showTarget = probes.isNotEmpty()
    val back = { if (showTarget) target = null else onBack() }
    BackHandler(enabled = showTarget, onBack = back)
    key(target) {
        Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showTarget) {
                HogarBackHeader("Detalle de dirección", back)
                Text(requireNotNull(target), style = MaterialTheme.typography.headlineMedium, fontFamily = SpaceMono)
                val identities = probes.filter { it.service?.product != null }
                if (identities.isNotEmpty()) HogarCard {
                    Text("Identidad anunciada", style = MaterialTheme.typography.titleLarge)
                    identities.forEach { probe ->
                        val evidence = requireNotNull(probe.service)
                        Text("Puerto ${probe.port} · ${evidence.product} · ${evidence.version}")
                    }
                    Text("El anuncio no verifica el software instalado.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Conexiones y protocolos", style = MaterialTheme.typography.titleLarge)
                HogarCard {
                    probes.forEach { probe ->
                        Text("${probe.port} · ${probe.outcome.label()}", style = MaterialTheme.typography.titleMedium)
                        Text(probe.service?.let { "${it.protocol} · respuesta compatible" } ?: "Protocolo no identificado")
                        Text("${probe.durationMillis} ms", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HogarButton("Volver a los resultados", back, Modifier.fillMaxWidth(), secondary = true)
                if (onEvaluate != null) HogarButton("Abrir evaluación de la revisión", onEvaluate, Modifier.fillMaxWidth())
            } else {
                HogarBackHeader("Resultados de revisión", back)
                when (state) {
                    ScanDetailUiState.Closed -> Unit
                    ScanDetailUiState.Loading -> HogarLoading("Cargando revisión…")
                    ScanDetailUiState.NotFound -> HogarNotice("Revisión no disponible", "No se encontró en este perfil.")
                    ScanDetailUiState.Error -> {
                        HogarNotice("No se pudo cargar la revisión", "Vuelve a intentarlo.", error = true)
                        HogarButton("Reintentar", onRetry, Modifier.fillMaxWidth())
                    }
                    is ScanDetailUiState.Loaded -> {
                        val data = state.record
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(data.startedAtMillis)), style = MaterialTheme.typography.bodySmall)
                        val status = ScanStatus.valueOf(data.status.name)
                        ScanResultsPanel(ScanUiState(status, data.config, data.results),
                            onTarget = { target = it.toString() }, details = { evaluationContent(data) })
                        if (data.status == ScanRecordStatus.RUNNING) {
                            HogarButton("Actualizar detalle", onRetry, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
            }
        }
    }
}
