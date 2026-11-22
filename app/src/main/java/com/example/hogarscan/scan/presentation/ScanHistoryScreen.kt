package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.ViewModelProvider
import com.example.hogarscan.evaluations.presentation.ScanEvaluationPanel
import com.example.hogarscan.evaluations.presentation.ScanEvaluationViewModel
import com.example.hogarscan.evaluations.presentation.FindingDetailScreen
import com.example.hogarscan.ui.components.HogarBrandHeader
import com.example.hogarscan.ui.components.HogarButton
import com.example.hogarscan.ui.components.HogarCard
import com.example.hogarscan.evaluations.presentation.ScanEvaluationUiState
import com.example.hogarscan.evaluations.presentation.TargetEvaluationPanel

@Composable
fun ScanHistoryRoute(
    viewModel: ScanHistoryViewModel,
    modifier: Modifier = Modifier,
    evaluationFactory: ((String, String) -> ViewModelProvider.Factory)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val selectedFinding by viewModel.selectedFinding.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    if (deletion.confirmation != null) {
        AlertDialog(onDismissRequest = viewModel::cancelDeletion,
            title = { Text("¿Borrar esta revisión del teléfono?") },
            text = { Text("Se eliminarán sus resultados, evaluaciones y pendientes locales. Un envío ya iniciado puede terminar. Los resúmenes del servidor no se borran.") },
            dismissButton = { TextButton(onClick = viewModel::cancelDeletion) { Text("Cancelar") } },
            confirmButton = { TextButton(onClick = viewModel::confirmDeletion) { Text("Borrar revisión local") } })
    }
    val listState = rememberLazyListState()
    val record = (detailState as? ScanDetailUiState.Loaded)?.record
    val detailKey = record?.let { "${it.ownerId}:${it.id}" }
    val holder = rememberSaveableStateHolder()
    var evaluationKey by rememberSaveable { mutableStateOf<String?>(null) }
    val closeDetail = {
        detailKey?.let(holder::removeState)
        evaluationKey = null
        viewModel.closeDetail()
    }
    val evaluation: ScanEvaluationViewModel? =
        if (record?.status == ScanRecordStatus.COMPLETED && evaluationFactory != null) {
            androidx.lifecycle.viewmodel.compose.viewModel(
                key = "scan-evaluation:$detailKey",
                factory = evaluationFactory(record.id, record.ownerId)
            )
        } else null
    val evaluationState = evaluation?.uiState?.collectAsStateWithLifecycle()?.value

    BackHandler(
        enabled = detailState != ScanDetailUiState.Closed,
        onBack = { if (selectedFinding != null) viewModel.closeFinding() else closeDetail() }
    )

    if (selectedFinding != null) {
        FindingDetailScreen(requireNotNull(selectedFinding), onBack = viewModel::closeFinding, modifier = modifier)
    } else if (detailState == ScanDetailUiState.Closed) {
        ScanHistoryScreen(
            state = state,
            onRetry = viewModel::retry,
            message = deletion.message,
            onOpenRecord = viewModel::openDetail,
            listState = listState,
            modifier = modifier
        )
    } else if (evaluationKey != null && evaluationKey == detailKey && evaluation != null && record != null) {
        holder.SaveableStateProvider("evaluation:$detailKey") {
            Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                ScanEvaluationPanel(evaluation, record, onBack = {
                    holder.removeState("evaluation:$detailKey")
                    evaluationKey = null
                }) { finding -> viewModel.openFinding(record.id, record.ownerId, finding) }
            }
        }
    } else holder.SaveableStateProvider(detailKey ?: "loading") {
        ScanHistoryDetailScreen(
            state = detailState,
            onBack = closeDetail,
            onRetry = viewModel::retryDetail,
            modifier = modifier,
            targetEvaluationContent = { currentRecord, address ->
                TargetEvaluationPanel(
                    scanId = currentRecord.id,
                    ownerId = currentRecord.ownerId,
                    address = address,
                    state = evaluationState,
                    onFindingSelected = { finding ->
                        viewModel.openFinding(
                            currentRecord.id,
                            currentRecord.ownerId,
                            finding
                        )
                    },
                    onOpenEvaluation = if (evaluation != null) {
                        { evaluationKey = detailKey }
                    } else {
                        null
                    }
                )
            },
            evaluationContent = { record ->
                if (record.status != ScanRecordStatus.RUNNING) {
                    OutlinedButton(enabled = !deletion.pending, onClick = viewModel::requestDeletion) { Text("Borrar esta revisión del teléfono") }
                }
                deletion.message?.let { Text(it) }
                if (evaluation != null) {
                    HogarCard {
                        val saved = evaluationState as? ScanEvaluationUiState.Saved
                        Text(if (saved == null) "Evaluación de la revisión" else "Evaluación guardada",
                            style = MaterialTheme.typography.titleLarge)
                        saved?.let { Text(it.message) }
                        HogarButton(if (saved == null) "Evaluar revisión" else "Abrir evaluación",
                            { evaluationKey = detailKey }, Modifier.fillMaxWidth())
                    }
                }
            }
        )
    }
}

@Composable
private fun ScanHistoryScreen(
    state: ScanHistoryUiState,
    onRetry: () -> Unit,
    message: String? = null,
    onOpenRecord: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HogarBrandHeader()
        }
        item {
            Text("Historial", style = MaterialTheme.typography.headlineMedium)
            Text("Revisiones guardadas en este dispositivo.")
            message?.let { Text(it) }
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
                    HogarCard {
                        Text(formatDate(record.startedAtMillis), style = MaterialTheme.typography.titleLarge)
                        Text(record.status.label())
                        record.finishedAtMillis?.let {
                            Text("Cierre: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                        HogarButton("Abrir revisión", { onOpenRecord(record.id) },
                            Modifier.fillMaxWidth(), secondary = true)
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
