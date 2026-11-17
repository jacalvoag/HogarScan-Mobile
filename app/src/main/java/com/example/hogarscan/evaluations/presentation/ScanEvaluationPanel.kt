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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.evaluations.data.HttpRulesDataSource
import com.example.hogarscan.evaluations.data.RulesRemoteResult
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ScanRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ScanEvaluationPanel(viewModel: ScanEvaluationViewModel, record: ScanRecord, rulesDataSource: HttpRulesDataSource? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val identifications = remember(record.id) { mutableStateMapOf<Ipv4Address, DeclaredIdentification>() }
    var address by remember(record.id) { mutableStateOf("") }
    var vendor by remember(record.id) { mutableStateOf("") }
    var product by remember(record.id) { mutableStateOf("") }
    var version by remember(record.id) { mutableStateOf("") }
    var source by remember(record.id) { mutableStateOf(IdentificationSource.USER_DEVICE_LABEL) }
    var formError by remember(record.id) { mutableStateOf<String?>(null) }
    var rules by remember(record.id) { mutableStateOf<EvaluationRules?>(null) }
    var rulesLoading by remember(record.id) { mutableStateOf(false) }
    var rulesError by remember(record.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evaluación de la revisión")
            if (rulesDataSource != null) {
                OutlinedButton(enabled = !rulesLoading, onClick = {
                    rulesLoading = true
                    rulesError = false
                    scope.launch {
                        try {
                            when (val result = rulesDataSource.getRules()) {
                                is RulesRemoteResult.Success -> rules = result.rules
                                is RulesRemoteResult.Failure -> rulesError = true
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { rulesError = true }
                        finally { rulesLoading = false }
                    }
                }) { Text(if (rulesLoading) "Consultando reglas…" else "Consultar reglas publicadas") }
                if (rulesError) Text("No se pudieron consultar las reglas.")
                rules?.let { Text("Versión: ${it.rulesetVersion}. ${it.caveat}") }
            }
            when (val current = state) {
                ScanEvaluationUiState.Loading -> CircularProgressIndicator()
                ScanEvaluationUiState.Ready -> {
                    Text("Puedes agregar una identificación declarada para cada dirección antes de evaluar. Es opcional y no se verifica automáticamente.")
                    OutlinedTextField(address, { address = it }, label = { Text("IPv4 del objetivo") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(vendor, { vendor = it }, label = { Text("Fabricante") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(product, { product = it }, label = { Text("Producto") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(version, { version = it }, label = { Text("Versión") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        source = if (source == IdentificationSource.USER_DEVICE_LABEL) IdentificationSource.USER_ADMIN_PANEL else IdentificationSource.USER_DEVICE_LABEL
                    }) { Text(if (source == IdentificationSource.USER_DEVICE_LABEL) "Origen: etiqueta del equipo" else "Origen: panel de administración") }
                    OutlinedButton(onClick = {
                        val ip = runCatching { Ipv4Address.parse(address.trim()) }.getOrNull()
                        val declared = runCatching { DeclaredIdentification(vendor, product, version, source) }.getOrNull()
                        if (ip == null || ip !in record.config.targetAddresses || declared == null) {
                            formError = "Revisa la dirección y completa fabricante, producto y versión."
                        } else {
                            identifications[ip] = declared
                            formError = null
                            address = ""; vendor = ""; product = ""; version = ""
                        }
                    }) { Text("Agregar identificación") }
                    formError?.let { Text(it) }
                    identifications.keys.forEach { ip ->
                        OutlinedButton(onClick = { identifications.remove(ip) }) { Text("Quitar identificación de $ip") }
                    }
                    val unfinished = listOf(address, vendor, product, version).any { it.isNotBlank() }
                    if (unfinished) Text("Agrega o limpia los datos escritos antes de comenzar.")
                    Button(onClick = { viewModel.start(identifications.toMap()) }, enabled = !unfinished) { Text("Evaluar revisión") }
                }
                is ScanEvaluationUiState.Running -> {
                    CircularProgressIndicator()
                    Text("Evaluaciones completadas: ${current.completed} de ${current.total}")
                    OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar evaluación") }
                }
                is ScanEvaluationUiState.Error -> {
                    Text(current.message)
                    OutlinedButton(onClick = viewModel::load) { Text("Volver a cargar") }
                }
                is ScanEvaluationUiState.Saved -> {
                    Text(current.message)
                    OutlinedButton(onClick = viewModel::retrySync) { Text("Reintentar sincronización") }
                    current.evaluations.forEach { evaluation ->
                        Text("Objetivo: ${evaluation.input.address}")
                        EvaluationResultPanel(evaluation.result)
                    }
                }
            }
        }
    }
}
