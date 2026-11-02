package com.example.hogarscan.scan.domain.policy

object ScanPolicy {
    const val MAX_ADDRESSES = 254
    const val MAX_CONCURRENT_PROBES = 20
    const val CONNECT_TIMEOUT_MILLIS = 1_000

    val allowedPorts: Set<Int> = setOf(22, 23, 80, 443, 445, 3389)
}