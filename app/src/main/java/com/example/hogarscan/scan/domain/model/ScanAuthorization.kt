package com.example.hogarscan.scan.domain.model

class ScanAuthorization private constructor(
    private val scope: NetworkScope,
    private val startAddress: Ipv4Address,
    private val endAddress: Ipv4Address,
    private val ports: List<Int>,
    private val connectionId: String
) {
    private var revoked = false

    fun revoke() {
        revoked = true
    }

    fun validate(
        config: ScanConfig,
        network: WifiNetworkResult
    ): Boolean {
        if (revoked) return false

        val matches =
            network is WifiNetworkResult.Available &&
                    network.connectionId == connectionId &&
                    network.scope == scope &&
                    config.networkScope == scope &&
                    config.startAddress == startAddress &&
                    config.endAddress == endAddress &&
                    config.ports == ports

        if (!matches) {
            revoke()
        }

        return !revoked
    }

    companion object {
        fun confirm(
            config: ScanConfig,
            network: WifiNetworkResult.Available
        ): ScanAuthorization {
            require(config.networkScope == network.scope) {
                "La configuración debe corresponder a la red actual."
            }
            require(network.connectionId.isNotBlank()) {
                "La conexión debe tener un identificador."
            }

            return ScanAuthorization(
                scope = config.networkScope,
                startAddress = config.startAddress,
                endAddress = config.endAddress,
                ports = config.ports.toList(),
                connectionId = network.connectionId
            )
        }
    }
}