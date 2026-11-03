package com.example.hogarscan.scan.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.model.WifiNetworkResult.Reason

@Composable
fun WifiRoute(
    viewModel: WifiViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()


    WifiScreen(
        state = state,
        onRefresh = viewModel::refresh,
        formContent = {
            ScanSetupForm(
                state = setupState,
                onStartChange = viewModel::updateStartAddress,
                onEndChange = viewModel::updateEndAddress,
                onPortToggle = viewModel::togglePort,
                onReview = viewModel::reviewConfiguration,
                onDismiss = viewModel::dismissConfirmation,
                onConfirm = viewModel::confirmAuthorization,
                onRevoke = viewModel::revokeAuthorization,
                onStart = viewModel::startScan,
                scanInProgress = scanState.isActive,
                modifier = Modifier.padding(top = 24.dp)
            )
            ScanResultsPanel(
                state = scanState,
                onCancel = viewModel::cancelScan,
                modifier = Modifier.padding(top = 24.dp)
            )
        },
        modifier = modifier
    )
}

@Composable
private fun WifiScreen(
    state: WifiUiState,
    onRefresh: () -> Unit,
    formContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "HogarScan",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Red Wi-Fi",
            style = MaterialTheme.typography.titleLarge
        )

        Text("Información de la última consulta.")

        when (state) {
            WifiUiState.Idle -> {
                Text("Consulta la red para mostrar sus datos.")
            }

            WifiUiState.Loading -> {
                CircularProgressIndicator()
                Text("Consultando la red…")
            }

            is WifiUiState.Loaded -> {
                when (val result = state.result) {
                    is WifiNetworkResult.Available -> {
                        val scope = result.scope

                        Text("IPv4 del teléfono: ${scope.localAddress}")
                        Text("Prefijo: /${scope.prefixLength}")
                        Text(
                            "Subred: ${scope.networkAddress}" +
                                    "/${scope.prefixLength}"
                        )
                        Text("Broadcast: ${scope.broadcastAddress}")
                        Text(
                            "Rango de hosts: ${scope.firstHost}" +
                                    " – ${scope.lastHost}"
                        )
                        Text(
                            "Direcciones de host en la subred: " +
                                    "${scope.hostCount}"
                        )
                    }

                    is WifiNetworkResult.Unavailable -> {
                        Text(result.reason.message())
                    }
                }
            }

            WifiUiState.Error -> {
                Text(
                    text = "No se pudo consultar la red. Inténtalo nuevamente.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Button(
            onClick = onRefresh,
            enabled = state != WifiUiState.Loading
        ) {
            Text("Actualizar")
        }

        formContent()
    }
}

private fun Reason.message(): String = when (this) {
    Reason.NO_NETWORK ->
        "No hay una red activa. Conéctate al Wi-Fi y actualiza."

    Reason.NOT_WIFI ->
        "La conexión activa no es Wi-Fi. Conéctate al Wi-Fi y actualiza."

    Reason.VPN_ACTIVE ->
        "La conexión activa utiliza una VPN. Desactívala y actualiza."

    Reason.NETWORK_INFO_UNAVAILABLE ->
        "No se pudo obtener la información de la red. Actualiza nuevamente."

    Reason.NO_IPV4 ->
        "La red activa no tiene una dirección IPv4 disponible."

    Reason.AMBIGUOUS_IPV4 ->
        "La red tiene varias direcciones IPv4 y no se pudo determinar cuál usar."

    Reason.UNSUPPORTED_SUBNET ->
        "La subred detectada no es compatible con el alcance actual de HogarScan."

    Reason.NETWORK_CHANGED ->
        "La conexión cambió durante la consulta. Actualiza nuevamente."

    Reason.PERMISSION_DENIED ->
        "La aplicación no tiene permiso para consultar el estado de la red."
}
