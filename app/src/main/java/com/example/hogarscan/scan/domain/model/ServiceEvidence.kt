package com.example.hogarscan.scan.domain.model

enum class ServiceProtocol(val evidenceKind: String) {
    HTTP("HTTP_RESPONSE"), HTTPS("HTTPS_RESPONSE"), SSH("SSH_IDENTIFICATION"), FTP("FTP_GREETING")
}

/** Solo datos normalizados. Una identidad anunciada puede ser incompleta o falsificada. */
data class ServiceEvidence(
    val protocol: ServiceProtocol,
    val vendor: String? = null,
    val product: String? = null,
    val version: String? = null
) {
    init {
        val values = listOf(vendor, product, version)
        require(values.all { it == null } || values.all { it != null && Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}").matches(it) })
    }
}
