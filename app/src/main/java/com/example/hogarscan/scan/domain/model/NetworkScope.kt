package com.example.hogarscan.scan.domain.model

data class NetworkScope(
    val localAddress: Ipv4Address,
    val prefixLength: Int
) {
    init {
        require(prefixLength in 1..30) {
            "El alcance de HogarScan admite prefijos de /1 a /30."
        }
    }

    private val mask =
        (0xFFFF_FFFFL shl (32 - prefixLength)) and 0xFFFF_FFFFL

    val networkAddress = Ipv4Address.fromValue(
        localAddress.value and mask
    )

    val broadcastAddress = Ipv4Address.fromValue(
        networkAddress.value or (mask xor 0xFFFF_FFFFL)
    )

    val firstHost = Ipv4Address.fromValue(networkAddress.value + 1)

    val lastHost = Ipv4Address.fromValue(broadcastAddress.value - 1)

    val hostCount: Long = lastHost.value - firstHost.value + 1

    init {
        require(containsHost(localAddress)) {
            "La IPv4 del teléfono debe ser una dirección de host de la subred."
        }
    }

    fun containsHost(address: Ipv4Address): Boolean {
        return address.value in firstHost.value..lastHost.value
    }
}