package com.example.hogarscan.statistics.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import com.example.hogarscan.ui.components.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StatisticsRoute(
    viewModel: StatisticsViewModel, modifier: Modifier = Modifier,
    onLogin: (() -> Unit)? = null, onHistory: (() -> Unit)? = null,
    onSummaryOpenChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Al volver desde historial o cuenta se solicita una instantánea nueva.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(state) {
        if (state == StatisticsUiState.Idle && viewModel.uiState.value == StatisticsUiState.Idle) {
            viewModel.refresh()
        }
    }
    StatisticsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        modifier = modifier,
        onLogin = onLogin,
        onHistory = onHistory,
        onSummaryOpenChange = onSummaryOpenChange,
        selectionKey = viewModel.selectionKey
    )
}

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onSummaryOpenChange: (Boolean) -> Unit = {},
    selectionKey: String? = null
) {
    var selectedId by rememberSaveable(selectionKey) {
        mutableStateOf<String?>(null)
    }

    val data = (state as? StatisticsUiState.Data)?.statistics
    val selected = data?.recentScans?.firstOrNull {
        it.scanId == selectedId
    }

    LaunchedEffect(state) {
        when (state) {
            is StatisticsUiState.Data -> {
                if (selected == null) {
                    selectedId = null
                }
            }

            StatisticsUiState.Idle,
            StatisticsUiState.SignInRequired -> {
                selectedId = null
            }

            StatisticsUiState.Loading,
            is StatisticsUiState.Error -> Unit
        }
    }

    LaunchedEffect(selectedId != null) {
        onSummaryOpenChange(selectedId != null)
    }

    BackHandler(enabled = selectedId != null) {
        selectedId = null
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (selectedId != null) {
            HogarBackHeader(
                title = "Resumen de la cuenta",
                onBack = { selectedId = null }
            )

            when {
                selected != null -> {
                    Text(
                        text = localDate(selected.startedAt),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    HogarCard {
                        HogarValueRow(
                            "Conexiones aceptadas",
                            selected.acceptedServices.toString()
                        )
                        HogarValueRow(
                            "Coincidencias CVE posibles",
                            selected.findings.possibleCveMatch.toString()
                        )
                    }

                    HogarNotice(
                        "Detalle local no disponible",
                        "El servidor conserva fechas y conteos agregados. " +
                                "Las direcciones y evaluaciones permanecen en " +
                                "el teléfono donde se realizó la revisión."
                    )
                }

                state is StatisticsUiState.Error -> {
                    HogarNotice(
                        title = "No se pudo actualizar el resumen",
                        body = state.message,
                        error = true
                    )

                    HogarButton(
                        "Reintentar",
                        onRefresh,
                        Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    HogarLoading("Actualizando resumen…")
                }
            }

            HogarButton(
                text = "Volver a estadísticas",
                onClick = { selectedId = null },
                modifier = Modifier.fillMaxWidth(),
                secondary = true
            )
        } else {
            HogarBrandHeader()
            Text("Estadísticas", style = MaterialTheme.typography.headlineMedium)
            Text("Solo incluyen revisiones completas y evaluadas cuyos resúmenes llegaron a la API.")
            when (state) {
                StatisticsUiState.Idle -> Text("Actualiza para consultar tus estadísticas.")
                StatisticsUiState.Loading -> HogarLoading("Consultando estadísticas…")
                StatisticsUiState.SignInRequired -> {
                    Text("Inicia sesión para consultar las estadísticas de tu cuenta.")
                    onLogin?.let { HogarButton("Ir a cuenta", it, Modifier.fillMaxWidth()) }
                }
                is StatisticsUiState.Error -> HogarNotice("No se pudieron consultar las estadísticas", state.message, error = true)
                is StatisticsUiState.Data -> {
                    val stats = state.statistics
                    if (stats.totalScans == 0L) Text("Aún no hay resúmenes sincronizados.")
                    Text("Actualizadas: ${localDate(stats.generatedAt)}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HogarMetric(stats.totalScans.toString(), "Revisiones: ${stats.totalScans}", Modifier.weight(1f))
                        HogarMetric(stats.totalAddressesScanned.toString(), "Direcciones revisadas", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HogarMetric(stats.totalAddressesResponded.toString(), "Con respuesta TCP", Modifier.weight(1f))
                        HogarMetric(stats.totalAcceptedServices.toString(), "Conexiones aceptadas", Modifier.weight(1f))
                    }
                    if (stats.totalScans == 0L) {
                        onHistory?.let { HogarButton("Ir a mis revisiones", it, Modifier.fillMaxWidth(), secondary = true) }
                    } else {
                        Findings(stats.findings)
                        Text("Una dirección puede contarse en varias revisiones.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (stats.recentScans.isNotEmpty()) Text("Revisiones recientes", style = MaterialTheme.typography.titleLarge)
                    stats.recentScans.forEach { scan ->
                        HogarCard {
                            Text(localDate(scan.startedAt), style = MaterialTheme.typography.titleMedium)
                            HogarButton("Abrir resumen", { selectedId = scan.scanId }, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
            }
            if (state != StatisticsUiState.Loading && state != StatisticsUiState.SignInRequired) {
                HogarButton("Actualizar", onRefresh, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Findings(counts: com.example.hogarscan.statistics.domain.StatisticsFindingCounts) {
    Text("Hallazgos por categoría", style = MaterialTheme.typography.titleLarge)
    HogarCard {
        HogarValueRow("Conexión TCP aceptada", counts.tcpServiceAccessible.toString())
        HogarValueRow("Protocolo identificado", counts.protocolIdentified.toString())
        HogarValueRow("Revisión preventiva", counts.preventiveReview.toString())
        HogarValueRow("Posible coincidencia CVE", counts.possibleCveMatch.toString())
        HogarValueRow("Evidencia insuficiente", counts.insufficientEvidence.toString())
    }
}

private fun localDate(value: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)
