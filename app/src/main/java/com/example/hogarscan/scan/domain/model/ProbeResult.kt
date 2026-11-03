package com.example.hogarscan.scan.domain.model

enum class ProbeOutcome {
    ACCEPTED,
    REJECTED,
    TIMED_OUT,
    ERROR
}

data class ProbeResult(
    val address: Ipv4Address,
    val port: Int,
    val outcome: ProbeOutcome,
    val durationMillis: Long
) {
    init {
        require(port in 1..65_535) {
            "El puerto TCP debe estar entre 1 y 65535."
        }
        require(durationMillis >= 0L) {
            "La duración no puede ser negativa."
        }
    }
}