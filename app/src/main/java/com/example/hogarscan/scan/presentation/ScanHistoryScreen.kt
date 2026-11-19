package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.ViewModelProvider
import com.example.hogarscan.evaluations.presentation.ScanEvaluationPanel
import com.example.hogarscan.evaluations.presentation.ScanEvaluationViewModel
import com.example.hogarscan.evaluations.presentation.FindingDetail
import com.example.hogarscan.evaluations.presentation.FindingDetailScreen

@Composable
fun ScanHistoryRoute(
    viewModel: ScanHistoryViewModel,
    modifier: Modifier = Modifier,
    evaluationFactory: ((String, String) -> ViewModelProvider.Factory)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var selectedFinding by remember { mutableStateOf<SelectedFinding?>(null) }
    val loaded = detailState as? ScanDetailUiState.Loaded
    val visibleFinding = selectedFinding?.takeIf { selected ->
        loaded != null && loaded.record.id == selected.scanId && loaded.record.ownerId == selected.ownerId
    }
    LaunchedEffect(detailState) {
        if (visibleFinding == null) selectedFinding = null
    }

    BackHandler(
        enabled = detailState != ScanDetailUiState.Closed,
        onBack = { if (visibleFinding != null) selectedFinding = null else viewModel.closeDetail() }
    )

    if (visibleFinding != null) {
        FindingDetailScreen(visibleFinding.detail, onBack = { selectedFinding = null }, modifier = modifier)
    } else if (detailState == ScanDetailUiState.Closed) {
        ScanHistoryScreen(
            state = state,
            onRetry = viewModel::retry,
            onOpenRecord = viewModel::openDetail,
            listState = listState,
            modifier = modifier
        )
    } else {
        ScanHistoryDetailScreen(
            state = detailState,
            onBack = viewModel::closeDetail,
            onRetry = viewModel::retryDetail,
            modifier = modifier,
            evaluationContent = { record ->
                if (evaluationFactory != null) {
                    val evaluationViewModel: ScanEvaluationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = "scan-evaluation:${record.id}",
                        factory = evaluationFactory(record.id, record.ownerId)
                    )
                    ScanEvaluationPanel(evaluationViewModel, record) { finding ->
                        selectedFinding = SelectedFinding(record.id, record.ownerId, finding)
                    }
                }
            }
        )
    }
}

private data class SelectedFinding(val scanId: String, val ownerId: String, val detail: FindingDetail)

@Composable
private fun ScanHistoryScreen(
    state: ScanHistoryUiState,
    onRetry: () -> Unit,
    onOpenRecord: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Historial", style = MaterialTheme.typography.headlineMedium)
            Text("Revisiones guardadas en este dispositivo.")
        }

        when (state) {
            ScanHistoryUiState.Loading -> item {
                CircularProgressIndicator()
                Text("Cargando historial…")
            }

            ScanHistoryUiState.Error -> item {
                Text(
                    "No se pudo cargar el historial.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }

            is ScanHistoryUiState.Loaded -> {
                if (state.records.isEmpty()) {
                    item { Text("Todavía no hay revisiones guardadas.") }
                }

                items(state.records, key = { it.id }) { record ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Ver detalle") {
                                onOpenRecord(record.id)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                record.status.label(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Inicio: ${formatDate(record.startedAtMillis)}")
                            record.finishedAtMillis?.let {
                                Text("Cierre: ${formatDate(it)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ScanRecordStatus.label(): String = when (this) {
    ScanRecordStatus.RUNNING -> "En curso"
    ScanRecordStatus.COMPLETED -> "Completada"
    ScanRecordStatus.CANCELLED -> "Cancelada"
    ScanRecordStatus.INTERRUPTED -> "Interrumpida"
    ScanRecordStatus.FAILED -> "Con error"
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.MEDIUM
    ).format(Date(timestamp))
