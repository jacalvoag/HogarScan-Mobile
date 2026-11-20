package com.example.hogarscan.scan.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.model.WifiNetworkResult.Reason
import com.example.hogarscan.ui.components.HogarBackHeader
import com.example.hogarscan.ui.components.HogarButton
import com.example.hogarscan.ui.components.HogarIcon
import com.example.hogarscan.ui.components.HogarIconImage
import com.example.hogarscan.ui.components.HogarNotice
import java.text.DateFormat
import java.util.Date

enum class WifiPage { Home, Setup, Progress, Help }

@Composable
fun WifiRoute(
    viewModel: WifiViewModel,
    page: WifiPage,
    onPageChange: (WifiPage) -> Unit,
    history: ScanHistoryUiState,
    onOpenReview: (String) -> Unit,
    modifier: Modifier = Modifier,
    guest: Boolean = false,
    onLogin: () -> Unit = {},
    onRegister: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    var cancelConfirmation by rememberSaveable { mutableStateOf(false) }
    var helpOrigin by rememberSaveable { mutableStateOf(WifiPage.Home) }
    val back = {
        when {
            page == WifiPage.Setup && setupState.confirmation != null && !setupState.checkingNetwork -> viewModel.dismissConfirmation()
            page == WifiPage.Setup && setupState.authorizedConfig != null -> viewModel.revokeAuthorization()
            page == WifiPage.Progress && scanState.isActive -> cancelConfirmation = true
            page == WifiPage.Help -> onPageChange(helpOrigin)
            else -> onPageChange(WifiPage.Home)
        }
    }
    BackHandler(enabled = page != WifiPage.Home, onBack = back)
    if (cancelConfirmation && scanState.isActive) {
        AlertDialog(onDismissRequest = { cancelConfirmation = false },
            title = { Text("¿Cancelar revisión?") },
            text = { Text("Se conservarán los resultados parciales registrados hasta el momento.") },
            confirmButton = { TextButton(onClick = {
                cancelConfirmation = false
                viewModel.cancelScan()
            }) { Text("Cancelar revisión") } },
            dismissButton = { TextButton(onClick = { cancelConfirmation = false }) { Text("Continuar") } })
    }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (page) {
            WifiPage.Home -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HogarIconImage(HogarIcon.Logo, tint = false)
                    Text("HogarScan", style = MaterialTheme.typography.titleLarge)
                }
                Text("Inicio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                if (guest) HogarNotice("Modo invitado", "Puedes guardar revisiones TCP en este teléfono.", Modifier.fillMaxWidth())
                Text("Tu red, con claridad.", style = MaterialTheme.typography.titleLarge)
                NetworkSummary(state)
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nueva revisión", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Text("Comprueba conexiones seleccionadas en una red que administras.",
                        color = MaterialTheme.colorScheme.onPrimary)
                    HogarButton("Configurar revisión", { onPageChange(WifiPage.Setup) },
                        Modifier.fillMaxWidth(), secondary = true)
                }
                if (guest) {
                    HogarButton("Iniciar sesión", onLogin, Modifier.fillMaxWidth())
                    HogarButton("Crear cuenta", onRegister, Modifier.fillMaxWidth(), secondary = true)
                }
                Text("Última revisión", style = MaterialTheme.typography.titleLarge)
                when (history) {
                    ScanHistoryUiState.Loading -> Text("Consultando historial…")
                    ScanHistoryUiState.Error -> Text("No se pudo consultar el historial.")
                    is ScanHistoryUiState.Loaded -> {
                        val latest = history.records.firstOrNull()
                        if (latest == null) Text("Aún no hay revisiones guardadas en este perfil.")
                        else Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(latest.startedAtMillis)), style = MaterialTheme.typography.titleLarge)
                            Text(when (latest.status) {
                                ScanRecordStatus.RUNNING -> "Revisión en curso"
                                ScanRecordStatus.COMPLETED -> "Revisión completa"
                                ScanRecordStatus.CANCELLED -> "Revisión cancelada"
                                ScanRecordStatus.INTERRUPTED -> "Revisión interrumpida"
                                ScanRecordStatus.FAILED -> "Revisión con error"
                            })
                            HogarButton("Abrir revisión", { onOpenReview(latest.id) }, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
                TextButton(onClick = { helpOrigin = WifiPage.Home; onPageChange(WifiPage.Help) },
                    modifier = Modifier.fillMaxWidth()) { Text("Qué puede comprobar HogarScan") }
                TextButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) { Text("Actualizar red") }
            }
            WifiPage.Setup -> {
                val authorizing = setupState.confirmation != null || setupState.authorizedConfig != null
                HogarBackHeader(if (authorizing) "Autorizar revisión" else "Nueva revisión", back)
                if (!authorizing) {
                    Text("Red Wi-Fi", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkSummary(state, detailed = true)
                        if ((state as? WifiUiState.Loaded)?.result !is WifiNetworkResult.Available) {
                            TextButton(onClick = viewModel::refresh) { Text("Actualizar red") }
                        }
                    }
                }
                ScanSetupForm(setupState, viewModel::updateStartAddress, viewModel::updateEndAddress,
                    viewModel::togglePort, viewModel::reviewConfiguration, viewModel::dismissConfirmation,
                    viewModel::confirmAuthorization, viewModel::revokeAuthorization,
                    {
                        viewModel.startScan()
                        if (viewModel.scanState.value.isActive) onPageChange(WifiPage.Progress)
                    }, scanState.isActive)
                if (!authorizing) TextButton(onClick = { helpOrigin = WifiPage.Setup; onPageChange(WifiPage.Help) },
                    modifier = Modifier.fillMaxWidth()) { Text("Consultar ayuda") }
            }
            WifiPage.Progress -> {
                HogarBackHeader(if (scanState.isActive) "Revisión en curso" else "Resultados de revisión", back)
                ScanResultsPanel(scanState, onCancel = { cancelConfirmation = true })
                if (!scanState.isActive) {
                    val reviewId = scanState.reviewId
                    if (reviewId != null) {
                        HogarButton("Abrir revisión guardada", {
                            onPageChange(WifiPage.Home)
                            onOpenReview(reviewId)
                        }, Modifier.fillMaxWidth())
                    } else {
                        HogarButton("Ir al historial", { onPageChange(WifiPage.Home); onHistory() },
                            Modifier.fillMaxWidth())
                    }
                    HogarButton("Volver al inicio", { onPageChange(WifiPage.Home) }, Modifier.fillMaxWidth())
                }
            }
            WifiPage.Help -> {
                HogarBackHeader("Acerca de la revisión", back)
                HogarNotice("Qué comprueba HogarScan",
                    "La aplicación intenta conexiones TCP en el rango y puertos autorizados. Una conexión aceptada no confirma una vulnerabilidad y un tiempo agotado no demuestra que el equipo esté apagado.",
                    Modifier.fillMaxWidth())
                Text("Revisa únicamente redes que administras o para las que tienes autorización.")
            }
        }
    }
}

@Composable
private fun NetworkSummary(state: WifiUiState, detailed: Boolean = false) {
    when (state) {
        WifiUiState.Idle -> Text("Consulta la red para mostrar sus datos.")
        WifiUiState.Loading -> { CircularProgressIndicator(); Text("Consultando la red…") }
        WifiUiState.Error -> Text("No se pudo consultar la red. Inténtalo nuevamente.")
        is WifiUiState.Loaded -> when (val result = state.result) {
            is WifiNetworkResult.Available -> {
                val scope = result.scope
                if (detailed) {
                    Text("IPv4 del teléfono", style = MaterialTheme.typography.bodySmall)
                    Text(scope.localAddress.toString())
                    Text("Subred", style = MaterialTheme.typography.bodySmall)
                    Text("${scope.networkAddress}/${scope.prefixLength}")
                } else Text("Wi-Fi disponible · ${scope.networkAddress}/${scope.prefixLength}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is WifiNetworkResult.Unavailable -> Text(result.reason.message())
        }
    }
}

private fun Reason.message(): String = when (this) {
    Reason.NO_NETWORK -> "No hay una red activa. Conéctate al Wi-Fi y actualiza."
    Reason.NOT_WIFI -> "La conexión activa no es Wi-Fi. Conéctate al Wi-Fi y actualiza."
    Reason.VPN_ACTIVE -> "La conexión activa utiliza una VPN. Desactívala y actualiza."
    Reason.NETWORK_INFO_UNAVAILABLE -> "No se pudo obtener la información de la red. Actualiza nuevamente."
    Reason.NO_IPV4 -> "La red activa no tiene una dirección IPv4 disponible."
    Reason.AMBIGUOUS_IPV4 -> "La red tiene varias direcciones IPv4 y no se pudo determinar cuál usar."
    Reason.UNSUPPORTED_SUBNET -> "La subred detectada no es compatible con el alcance actual de HogarScan."
    Reason.NETWORK_CHANGED -> "La conexión cambió durante la consulta. Actualiza nuevamente."
    Reason.PERMISSION_DENIED -> "La aplicación no tiene permiso para consultar el estado de la red."
}
