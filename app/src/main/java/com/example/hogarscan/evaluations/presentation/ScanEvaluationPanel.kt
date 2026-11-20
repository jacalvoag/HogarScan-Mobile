package com.example.hogarscan.evaluations.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.ui.components.*

@Composable
fun ScanEvaluationPanel(
    viewModel: ScanEvaluationViewModel,
    record: ScanRecord,
    onBack: (() -> Unit)? = null,
    onFindingSelected: (FindingDetail) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form by viewModel.identification.collectAsStateWithLifecycle()
    val rulesState by viewModel.rules.collectAsStateWithLifecycle()
    var identifying by rememberSaveable { mutableStateOf(onBack == null) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val busy = state is ScanEvaluationUiState.Running || state == ScanEvaluationUiState.Preparing
    val back: () -> Unit = {
        when {
            busy -> confirmCancel = true
            identifying && state == ScanEvaluationUiState.Ready -> identifying = false
            else -> onBack?.invoke()
        }
    }
    BackHandler(enabled = onBack != null, onBack = back)
    if (confirmCancel && busy) {
        AlertDialog(onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar evaluación?") },
            text = { Text("Los resultados TCP seguirán guardados en el teléfono.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text("Cancelar evaluación") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Continuar evaluación") } })
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) HogarBackHeader(when {
            busy -> "Evaluando revisión"
            state == ScanEvaluationUiState.Ready && identifying -> "Identificación opcional"
            state == ScanEvaluationUiState.Ready -> "Antes de evaluar"
            else -> "Evaluación de la revisión"
        }, back)

        when (val current = state) {
            ScanEvaluationUiState.Loading -> HogarLoading("Cargando evaluación…")
            ScanEvaluationUiState.Ready -> if (identifying) {
                Text("Usa datos de una etiqueta o un panel al que ya tengas acceso.")
                ObservedIdentificationsPanel(record, viewModel::useObservedIdentification)
                HogarField(form.address, { value -> viewModel.editIdentification { it.copy(address = value) } },
                    "IPv4 del objetivo", "Dirección de esta revisión", Modifier.fillMaxWidth())
                HogarField(form.vendor, { value -> viewModel.editIdentification { it.copy(vendor = value) } },
                    "Fabricante", "Revisa el fabricante indicado en el equipo", Modifier.fillMaxWidth())
                HogarField(form.product, { value -> viewModel.editIdentification { it.copy(product = value) } },
                    "Producto", "Producto o componente", Modifier.fillMaxWidth())
                HogarField(form.version, { value -> viewModel.editIdentification { it.copy(version = value) } },
                    "Versión", "Completa los tres datos para usar la declaración", Modifier.fillMaxWidth())
                Text("Procedencia", style = MaterialTheme.typography.titleLarge)
                for ((source, label) in listOf(
                    IdentificationSource.USER_ADMIN_PANEL to "Panel de administración",
                    IdentificationSource.USER_DEVICE_LABEL to "Etiqueta del equipo"
                )) {
                    HogarButton(if (form.source == source) "$label · Seleccionado" else label,
                        { viewModel.editIdentification { it.copy(source = source) } },
                        Modifier.fillMaxWidth(), secondary = true)
                }
                HogarNotice("Datos declarados", "No se verifican automáticamente. La identificación es opcional.")
                form.error?.let { HogarNotice("Revisa la identificación", it, error = true) }
                HogarButton("Agregar identificación", { viewModel.addIdentification(record.config.targetAddresses) },
                    Modifier.fillMaxWidth())
                if (form.unfinished) HogarButton("Descartar borrador", {
                    viewModel.editIdentification { ScanIdentificationState(identifications = it.identifications) }
                }, Modifier.fillMaxWidth(), secondary = true)
                if (onBack != null) HogarButton("Volver a preparar", { identifying = false },
                    Modifier.fillMaxWidth(), secondary = true)
                form.identifications.keys.forEach { ip ->
                    HogarButton("Quitar identificación de $ip", { viewModel.removeIdentification(ip) },
                        Modifier.fillMaxWidth(), secondary = true)
                }
            } else {
                Text("Revisión completa", style = MaterialTheme.typography.headlineMedium)
                HogarCard {
                    HogarValueRow("Objetivos", "${record.config.targetAddresses.size} direcciones")
                    HogarValueRow("Identificaciones elegidas", form.identifications.size.toString())
                }
                HogarNotice("Identificación opcional",
                    "Se utilizará el anuncio inequívoco cuando exista. Puedes aportar una declaración antes de comenzar.")
                HogarButton("Revisar identificación", { identifying = true }, Modifier.fillMaxWidth(), secondary = true)
                HogarButton(if (rulesState.loading) "Consultando reglas…" else "Consultar reglas publicadas",
                    viewModel::loadRules, Modifier.fillMaxWidth(), enabled = !rulesState.loading, secondary = true)
                rulesState.rules?.let { Text("Versión: ${it.rulesetVersion}. ${it.caveat}") }
                if (rulesState.error) Text("No se pudieron consultar las reglas.")
                Text("La evaluación se guardará en este teléfono y se intentará sincronizar el resumen.")
                if (form.unfinished) Text("Agrega o limpia los datos escritos antes de comenzar.")
                HogarButton("Comenzar evaluación", { viewModel.start() }, Modifier.fillMaxWidth(), enabled = !form.unfinished)
            }
            ScanEvaluationUiState.Preparing, is ScanEvaluationUiState.Running -> {
                Text("Interpretando resultados", style = MaterialTheme.typography.headlineMedium)
                if (current is ScanEvaluationUiState.Running) {
                    HogarProgress(current.completed, current.total, "${current.completed} de ${current.total} objetivos evaluados")
                    if (current.completed == current.total) Text("Guardando evaluación y sincronizando resumen…")
                } else HogarLoading("Preparando evaluación…")
                HogarNotice("Fuentes públicas", "NVD y KEV se consultan cuando la identificación y las coincidencias lo requieren.")
                Text("Los resultados TCP siguen guardados en este teléfono.")
                HogarButton("Cancelar evaluación", { confirmCancel = true }, Modifier.fillMaxWidth(), destructive = true)
            }
            is ScanEvaluationUiState.Error -> {
                HogarNotice("Evaluación no disponible", current.message, error = true)
                HogarButton("Volver a cargar", viewModel::load, Modifier.fillMaxWidth(), secondary = true)
            }
            is ScanEvaluationUiState.Saved -> {
                HogarNotice("Evaluación guardada", current.message)
                if (current.canRetrySync) HogarButton("Reintentar sincronización", viewModel::retrySync, Modifier.fillMaxWidth())
                HogarButton("Actualizar estado", viewModel::load, Modifier.fillMaxWidth(), secondary = true)
                ScanEvaluationResultsList(current.evaluations, onFindingSelected)
            }
        }
    }
}
