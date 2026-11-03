package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.AuthorizedScan
import com.example.hogarscan.scan.domain.model.ScanAuthorization
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.policy.ScanPolicy

class ScanSetupController {

    var state = ScanSetupState()
        private set

    private var network: WifiNetworkResult.Available? = null
    private var confirmationConnectionId: String? = null
    private var authorization: ScanAuthorization? = null

    fun updateStartAddress(value: String) {
        if (value == state.startAddress) return
        state = state.copy(startAddress = value)
        edited()
    }

    fun updateEndAddress(value: String) {
        if (value == state.endAddress) return
        state = state.copy(endAddress = value)
        edited()
    }

    fun togglePort(port: Int) {
        require(port in ScanPolicy.allowedPorts) {
            "El puerto no pertenece a los permitidos."
        }
        val ports = if (port in state.selectedPorts) {
            state.selectedPorts - port
        } else {
            state.selectedPorts + port
        }
        state = state.copy(selectedPorts = ports)
        edited()
    }

    fun onNetworkChecking() {
        state = state.copy(
            checkingNetwork = true,
            networkAvailable = false
        )
    }

    fun onNetworkResult(result: WifiNetworkResult) {
        val previous = network
        if (result !is WifiNetworkResult.Available) {
            network = null
            invalidate(
                if (authorization != null || state.confirmation != null) {
                    "La conexión dejó de estar disponible. Vuelve a autorizar."
                } else {
                    state.notice
                }
            )
            state = state.copy(
                networkAvailable = false,
                checkingNetwork = false
            )
            return
        }

        val changed = previous != null &&
            (previous.connectionId != result.connectionId ||
                previous.scope != result.scope)

        val config = state.authorizedConfig
        val authorizationInvalid = config != null &&
            authorization?.validate(config, result) != true

        if (changed || authorizationInvalid) {
            invalidate("La conexión cambió. Revisa y autoriza de nuevo.")
        }

        network = result
        state = state.copy(
            networkAvailable = true,
            checkingNetwork = false
        )
    }

    fun reviewConfiguration() {
        val currentNetwork = network
        if (!state.networkAvailable || state.checkingNetwork ||
            currentNetwork == null
        ) {
            state = state.copy(error = "Espera a tener una red Wi-Fi disponible.")
            return
        }

        invalidate(null)
        try {
            val config = ScanConfig(
                networkScope = currentNetwork.scope,
                startAddress = Ipv4Address.parse(state.startAddress.trim()),
                endAddress = Ipv4Address.parse(state.endAddress.trim()),
                ports = state.selectedPorts
            )
            confirmationConnectionId = currentNetwork.connectionId
            state = state.copy(
                startAddress = config.startAddress.toString(),
                endAddress = config.endAddress.toString(),
                confirmation = config
            )
        } catch (exception: IllegalArgumentException) {
            state = state.copy(
                error = exception.message ?: "Revisa la configuración."
            )
        }
    }

    fun dismissConfirmation() {
        confirmationConnectionId = null
        state = state.copy(confirmation = null)
    }

    fun confirmAuthorization() {
        val config = state.confirmation ?: return
        val currentNetwork = network

        if (!state.networkAvailable || state.checkingNetwork ||
            currentNetwork == null ||
            currentNetwork.connectionId != confirmationConnectionId ||
            currentNetwork.scope != config.networkScope
        ) {
            invalidate("La conexión cambió. Revisa y autoriza de nuevo.")
            return
        }

        try {
            authorization = ScanAuthorization.confirm(config, currentNetwork)
            confirmationConnectionId = null
            state = state.copy(
                confirmation = null,
                authorizedConfig = config,
                error = null,
                notice = null
            )
        } catch (exception: IllegalArgumentException) {
            invalidate(null)
            state = state.copy(error = exception.message)
        }
    }

    fun takeAuthorizedScan(): AuthorizedScan? {
        if (state.checkingNetwork || !state.networkAvailable) return null

        val config = state.authorizedConfig ?: return null
        val currentNetwork = network ?: return null
        val confirmed = authorization ?: return null

        if (!confirmed.validate(config, currentNetwork)) {
            invalidate("La autorización dejó de ser válida. Vuelve a confirmar.")
            return null
        }

        val request = AuthorizedScan(
            config = config,
            connectionId = currentNetwork.connectionId,
            authorization = confirmed
        )

        // Transfiere la autorización al coordinador sin revocarla todavía.
        authorization = null
        state = state.copy(authorizedConfig = null, notice = null)
        return request
    }

    fun revokeAuthorization() {
        invalidate("Autorización revocada.")
    }

    private fun edited() {
        invalidate(
            if (authorization != null) {
                "La configuración cambió. Vuelve a autorizar."
            } else {
                null
            }
        )
    }

    private fun invalidate(notice: String?) {
        authorization?.revoke()
        authorization = null
        confirmationConnectionId = null
        state = state.copy(
            confirmation = null,
            authorizedConfig = null,
            error = null,
            notice = notice
        )
    }
}
