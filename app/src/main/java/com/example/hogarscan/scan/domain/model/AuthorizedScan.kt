package com.example.hogarscan.scan.domain.model

import java.util.concurrent.atomic.AtomicBoolean

class AuthorizedScan internal constructor(
    val config: ScanConfig,
    val connectionId: String,
    private val authorization: ScanAuthorization
) {
    private val claimed = AtomicBoolean(false)

    internal fun claim() {
        check(claimed.compareAndSet(false, true)) {
            "Esta solicitud de revisión ya se utilizó."
        }
    }

    fun validate(network: WifiNetworkResult): Boolean =
        authorization.validate(config, network)

    fun revoke() {
        authorization.revoke()
    }
}
