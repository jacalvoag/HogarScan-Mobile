package com.example.hogarscan.evaluations.domain.model

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.policy.ScanPolicy

enum class IdentificationSource { USER_DEVICE_LABEL, USER_ADMIN_PANEL, SERVICE_RESPONSE }

data class DeclaredIdentification(
    val vendor: String,
    val product: String,
    val version: String,
    val source: IdentificationSource,
    val servicePort: Int? = null
) {
    init {
        require((source == IdentificationSource.SERVICE_RESPONSE) == (servicePort != null))
        require(servicePort == null || servicePort in ScanPolicy.allowedPorts)
        require(vendor.isNotBlank() && vendor.length <= 80)
        require(product.isNotBlank() && product.length <= 120)
        require(version.isNotBlank() && version.length <= 80)
    }
}

data class EvaluationProbe(val port: Int, val outcome: ProbeOutcome, val service: com.example.hogarscan.scan.domain.model.ServiceEvidence? = null)

/** La dirección y el propietario solo identifican el resultado en el teléfono. */
class EvaluationInput private constructor(
    val scanId: String,
    val ownerId: String,
    val address: Ipv4Address,
    probes: List<EvaluationProbe>,
    val identification: DeclaredIdentification?
) {
    val probes: List<EvaluationProbe> = probes.toList()

    companion object {
        fun fromResults(
            scanId: String,
            ownerId: String,
            address: Ipv4Address,
            results: List<ProbeResult>,
            identification: DeclaredIdentification? = null
        ): EvaluationInput {
            require(scanId.isNotBlank() && ownerId.isNotBlank())
            require(results.size in 1..6)
            require(results.all { it.address == address && it.port in ScanPolicy.allowedPorts })
            require(results.map { it.port }.toSet().size == results.size)
            val candidates = observedIdentifications(results)
            val selected = identification ?: candidates.singleOrNull()
            if (selected?.source == IdentificationSource.SERVICE_RESPONSE) {
                require(selected in candidates) { "La identificación debe proceder de una respuesta de este objetivo." }
            }
            return EvaluationInput(
                scanId, ownerId, address,
                results.map { EvaluationProbe(it.port, it.outcome, it.service) }, selected
            )
        }
    }
}

/** No se elige arbitrariamente entre varias identidades anunciadas por un objetivo. */
fun observedIdentifications(results: List<ProbeResult>): List<DeclaredIdentification> = results.mapNotNull { probe ->
    val service = probe.service ?: return@mapNotNull null
    if (service.vendor == null || service.product == null || service.version == null) return@mapNotNull null
    DeclaredIdentification(service.vendor, service.product, service.version, IdentificationSource.SERVICE_RESPONSE, probe.port)
}
