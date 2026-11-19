package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.domain.model.ScanRecord

@Composable
fun ScanEvaluationPanel(viewModel: ScanEvaluationViewModel, record: ScanRecord, onFindingSelected: (FindingDetail) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form by viewModel.identification.collectAsStateWithLifecycle()
    val rulesState by viewModel.rules.collectAsStateWithLifecycle()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evaluación de la revisión")
            OutlinedButton(enabled = !rulesState.loading, onClick = viewModel::loadRules) {
                Text(if (rulesState.loading) "Consultando reglas…" else "Consultar reglas publicadas")
            }
            if (rulesState.error) Text("No se pudieron consultar las reglas.")
            rulesState.rules?.let { Text("Versión: ${it.rulesetVersion}. ${it.caveat}") }
            when (val current = state) {
                ScanEvaluationUiState.Loading -> CircularProgressIndicator()
                ScanEvaluationUiState.Ready -> {
                    Text("Se usarán las identidades anunciadas por los servicios cuando sean inequívocas; no certifican el producto instalado.")
                    ObservedIdentificationsPanel(record, viewModel::useObservedIdentification)
                    Text("Puedes agregar una identificación declarada para cada dirección antes de evaluar. Es opcional y no se verifica automáticamente.")
                    OutlinedTextField(form.address, { value -> viewModel.editIdentification { it.copy(address = value) } }, label = { Text("IPv4 del objetivo") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.vendor, { value -> viewModel.editIdentification { it.copy(vendor = value) } }, label = { Text("Fabricante") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.product, { value -> viewModel.editIdentification { it.copy(product = value) } }, label = { Text("Producto") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.version, { value -> viewModel.editIdentification { it.copy(version = value) } }, label = { Text("Versión") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = viewModel::toggleIdentificationSource) {
                        Text(if (form.source == IdentificationSource.USER_DEVICE_LABEL) "Origen: etiqueta del equipo" else "Origen: panel de administración")
                    }
                    OutlinedButton(onClick = { viewModel.addIdentification(record.config.targetAddresses) }) { Text("Agregar identificación") }
                    form.error?.let { Text(it) }
                    form.identifications.keys.forEach { ip ->
                        OutlinedButton(onClick = { viewModel.removeIdentification(ip) }) { Text("Quitar identificación de $ip") }
                    }
                    if (form.unfinished) Text("Agrega o limpia los datos escritos antes de comenzar.")
                    Button(onClick = { viewModel.start() }, enabled = !form.unfinished) { Text("Evaluar revisión") }
                }
                ScanEvaluationUiState.Preparing -> {
                    CircularProgressIndicator()
                    Text("Preparando evaluación…")
                    OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar evaluación") }
                }
                is ScanEvaluationUiState.Running -> {
                    CircularProgressIndicator()
                    Text("Evaluaciones completadas: ${current.completed} de ${current.total}")
                    if (current.completed == current.total) {
                        Text("Guardando evaluación y sincronizando resumen…")
                    }
                    OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar evaluación") }
                }
                is ScanEvaluationUiState.Error -> {
                    Text(current.message)
                    OutlinedButton(onClick = viewModel::load) { Text("Volver a cargar") }
                }
                is ScanEvaluationUiState.Saved -> {
                    Text(current.message)
                    if (current.canRetrySync) {
                        OutlinedButton(onClick = viewModel::retrySync) { Text("Reintentar sincronización") }
                    }
                    OutlinedButton(onClick = viewModel::load) { Text("Actualizar estado") }
                    ScanEvaluationResultsList(current.evaluations, onFindingSelected)
                }
            }
        }
    }
}
