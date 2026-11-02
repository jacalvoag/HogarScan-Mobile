package com.example.hogarscan.scan.domain.model

import com.example.hogarscan.scan.domain.policy.ScanPolicy

class ScanConfig(
    val networkScope: NetworkScope,
    val startAddress: Ipv4Address,
    val endAddress: Ipv4Address,
    ports: Set<Int>
) {
    val ports: List<Int> = ports.sorted()

    init {
        require(startAddress <= endAddress) {
            "La dirección inicial debe ser menor o igual que la final."
        }

        require(
            networkScope.containsHost(startAddress) &&
                    networkScope.containsHost(endAddress)
        ) {
            "El rango debe pertenecer a la subred, sin incluir red ni broadcast."
        }

        require(
            endAddress.value - startAddress.value + 1 <= ScanPolicy.MAX_ADDRESSES
        ) {
            "El rango puede contener como máximo 254 direcciones."
        }

        require(this.ports.isNotEmpty()) {
            "Selecciona al menos un puerto."
        }

        require(this.ports.all { it in ScanPolicy.allowedPorts }) {
            "Los puertos seleccionados deben pertenecer a los permitidos."
        }
    }

    val targetAddresses: List<Ipv4Address> =
        (startAddress.value..endAddress.value)
            .filter { it != networkScope.localAddress.value }
            .map { Ipv4Address.fromValue(it) }

    init {
        require(targetAddresses.isNotEmpty()) {
            "El rango debe incluir otra dirección además de la del teléfono."
        }
    }

    val plannedProbeCount: Int = targetAddresses.size * this.ports.size
}