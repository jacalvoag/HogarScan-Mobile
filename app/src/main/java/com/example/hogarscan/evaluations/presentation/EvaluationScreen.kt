package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.evaluations.domain.model.FindingCategory
import com.example.hogarscan.evaluations.domain.model.FindingConfidence
import com.example.hogarscan.evaluations.domain.model.FindingPriority
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.evaluations.domain.model.SourceState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role

@Composable
fun EvaluationRoute(viewModel: EvaluationViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EvaluationScreen(
        state = state.evaluation,
        form = state.form,
        errors = state.errors,
        onFormChange = viewModel::updateForm,
        onEvaluate = { viewModel.evaluate() },
        onInvalidForm = viewModel::setErrors,
        onCancel = viewModel::cancel,
        modifier = modifier
    )
}

/** Pantalla aislada: el integrador conserva el formulario y maneja los eventos. */
@Composable
fun EvaluationScreen(
    state: EvaluationUiState,
    form: IdentificationForm,
    errors: IdentificationErrors,
    onFormChange: (IdentificationForm) -> Unit,
    onEvaluate: (DeclaredIdentification?) -> Unit,
    onInvalidForm: (IdentificationErrors) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Evaluación", style = MaterialTheme.typography.headlineMedium)
        Text("Una conexión aceptada no demuestra una vulnerabilidad.")
        if (state !is EvaluationUiState.Success) {
            val editable = state !is EvaluationUiState.Loading
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.enabled, onCheckedChange = { onFormChange(form.copy(enabled = it)) }, enabled = editable)
                Text("Agregar identificación declarada (opcional)")
            }
            if (form.enabled) {
                Text("Los datos son declarados por ti y no se verifican automáticamente.")
                OutlinedTextField(value = form.vendor, onValueChange = { onFormChange(form.copy(vendor = it)) }, label = { Text("Fabricante (máximo 80)") }, isError = errors.vendor, enabled = editable, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = form.product, onValueChange = { onFormChange(form.copy(product = it)) }, label = { Text("Producto (máximo 120)") }, isError = errors.product, enabled = editable, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = form.version, onValueChange = { onFormChange(form.copy(version = it)) }, label = { Text("Versión (máximo 80)") }, isError = errors.version, enabled = editable, modifier = Modifier.fillMaxWidth())
                Text("¿De dónde obtuviste estos datos?")
                IdentificationSource.entries.filter { it != IdentificationSource.SERVICE_RESPONSE }.forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = form.source == source, onClick = { onFormChange(form.copy(source = source)) }, enabled = editable)
                        Text(if (source == IdentificationSource.USER_DEVICE_LABEL) "Etiqueta del dispositivo" else "Panel de administración")
                    }
                }
                if (errors.vendor || errors.product || errors.version || errors.source) {
                    Text("Revisa los campos y selecciona la procedencia.", color = MaterialTheme.colorScheme.error)
                }
            }
            when (state) {
                EvaluationUiState.Loading -> {
                    CircularProgressIndicator()
                    Text("Evaluando observaciones…")
                    OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                }
                else -> {
                    if (state is EvaluationUiState.Error) Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        when (val validation = form.validate()) {
                            is IdentificationValidation.Valid -> onEvaluate(validation.identification)
                            is IdentificationValidation.Invalid -> onInvalidForm(validation.errors)
                        }
                    }) { Text(if (state is EvaluationUiState.Error) "Reintentar" else "Evaluar") }
                }
            }
        } else {
            EvaluationResultPanel(state.result)
        }
    }
}

@Composable
fun EvaluationResultPanel(result: EvaluationResult, modifier: Modifier = Modifier, onFindingClick: ((Int) -> Unit)? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hallazgos", style = MaterialTheme.typography.titleLarge)
        Text("Reglas: ${result.rulesetVersion} · Evaluado: ${result.evaluatedAt}")
        Text("NVD: ${result.sources.nvd.description()} · KEV: ${result.sources.kev.description()}")
        result.sources.checkedAt?.let { Text("Fuentes consultadas: $it") }
        if (
            result.findings.any {
                it.category == FindingCategory.POSSIBLE_CVE_MATCH
            } || result.possibleCveMatches.isNotEmpty()
        ) {
            Text(
                text = "Las coincidencias CVE son posibles; " +
                        "no confirman una vulnerabilidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (result.findings.isEmpty()) Text("La evaluación no devolvió hallazgos.")
        result.findings.withIndex().sortedBy { (_, finding) ->
            when (finding.priority) {
                FindingPriority.HIGH -> 0
                FindingPriority.MEDIUM -> 1
                FindingPriority.LOW -> 2
                FindingPriority.UNDETERMINED -> 3
            }
        }.forEach { (index, finding) ->
            val shape = RoundedCornerShape(20.dp)

            val cardModifier = if (onFindingClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Ver hallazgo"
                    ) {
                        onFindingClick(index)
                    }
            } else {
                Modifier.fillMaxWidth()
            }

            Card(
                modifier = cardModifier,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = finding.category.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (
                            finding.category == FindingCategory.POSSIBLE_CVE_MATCH
                        ) {
                            finding.cve ?: finding.title
                        } else {
                            finding.title
                        },
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Prioridad: ${finding.priority.description()} · " +
                                "Certeza: ${finding.confidence.description()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (onFindingClick != null) {
                        Text(
                            text = "Ver hallazgo",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Conserva la información cuando el panel se utiliza
                        // sin navegación hacia una pantalla de detalle.
                        Text("Evidencia: ${finding.evidence}")
                        Text(finding.explanation)
                        Text("Recomendación: ${finding.action}")
                        Text("Limitación: ${finding.limitation}")
                        Text(
                            text = "Regla: ${finding.ruleId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        result.possibleCveMatches
            .filter { match ->
                onFindingClick == null ||
                        result.findings.none { finding ->
                            finding.cve == match.cve
                        }
            }
            .forEach { match ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Coincidencia posible: ${match.cve}", style = MaterialTheme.typography.titleMedium)
                    Text("No confirma una vulnerabilidad en el dispositivo.")
                    Text("Identificación: ${match.vendor} ${match.product} ${match.version}")
                    Text("Procedencia: ${when (match.identificationSource) { IdentificationSource.USER_DEVICE_LABEL -> "etiqueta del dispositivo"; IdentificationSource.USER_ADMIN_PANEL -> "panel de administración"; IdentificationSource.SERVICE_RESPONSE -> "respuesta del servicio en puerto ${match.servicePort}; identidad anunciada, no certificada" }}")
                    Text("Condición: ${match.affectedCondition}")
                    Text("NVD: ${match.nvdUrl} · Consultado: ${match.nvdCheckedAt}")
                    Text(when (match.inKev) {
                        true -> "CVE incluida en el catálogo KEV consultado."
                        false -> "CVE no encontrada en el catálogo KEV consultado; esto no descarta riesgo."
                        null -> "La presencia en KEV es desconocida."
                    })
                }
            }
        }
    }
}

private fun SourceState.description(): String = when (this) {
    SourceState.NOT_QUERIED -> "no consultada"
    SourceState.AVAILABLE -> "disponible"
    SourceState.UNAVAILABLE -> "no disponible"
}

private fun FindingCategory.description(): String = when (this) {
    FindingCategory.TCP_SERVICE_ACCESSIBLE -> "Conexión TCP aceptada"
    FindingCategory.PROTOCOL_IDENTIFIED -> "Protocolo identificado"
    FindingCategory.PREVENTIVE_REVIEW -> "Revisión preventiva"
    FindingCategory.POSSIBLE_CVE_MATCH -> "Posible coincidencia CVE"
    FindingCategory.INSUFFICIENT_EVIDENCE -> "Evidencia insuficiente"
}

private fun FindingPriority.description(): String = when (this) {
    FindingPriority.LOW -> "Baja"
    FindingPriority.MEDIUM -> "Media"
    FindingPriority.HIGH -> "Alta"
    FindingPriority.UNDETERMINED -> "Indeterminada"
}

private fun FindingConfidence.description(): String = when (this) {
    FindingConfidence.OBSERVED -> "Observada"
    FindingConfidence.DECLARED -> "Declarada"
    FindingConfidence.POSSIBLE -> "Posible"
    FindingConfidence.INCONCLUSIVE -> "Inconclusa"
}
