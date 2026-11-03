package com.example.hogarscan.scan.domain.model

sealed interface WifiNetworkResult {

    data class Available(
        val scope: NetworkScope,
        val connectionId: String
    ) : WifiNetworkResult

    data class Unavailable(
        val reason: Reason
    ) : WifiNetworkResult

    enum class Reason {
        NO_NETWORK,
        NOT_WIFI,
        VPN_ACTIVE,
        NETWORK_INFO_UNAVAILABLE,
        NO_IPV4,
        AMBIGUOUS_IPV4,
        UNSUPPORTED_SUBNET,
        NETWORK_CHANGED,
        PERMISSION_DENIED
    }
}