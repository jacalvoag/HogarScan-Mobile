package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.domain.model.Ipv4Address

data class ScanIdentificationState(
    val address: String = "",
    val vendor: String = "",
    val product: String = "",
    val version: String = "",
    val source: IdentificationSource = IdentificationSource.USER_DEVICE_LABEL,
    val identifications: Map<Ipv4Address, DeclaredIdentification> = emptyMap(),
    val error: String? = null
) {
    val unfinished: Boolean
        get() = listOf(address, vendor, product, version).any { it.isNotBlank() }
}
