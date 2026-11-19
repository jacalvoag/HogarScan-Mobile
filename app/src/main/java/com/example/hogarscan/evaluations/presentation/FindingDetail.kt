package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.evaluations.domain.model.Finding
import com.example.hogarscan.evaluations.domain.model.FindingCategory
import com.example.hogarscan.evaluations.domain.model.FindingConfidence
import com.example.hogarscan.evaluations.domain.model.FindingPriority
import com.example.hogarscan.evaluations.domain.model.PossibleCveMatch
import com.example.hogarscan.evaluations.domain.model.SourceState

/** Datos de un hallazgo de una evaluación concreta, sin inferencias del puerto. */
data class FindingDetail(
    val title: String,
    val category: String,
    val priority: String,
    val confidence: String,
    val evidence: String,
    val explanation: String,
    val action: String,
    val limitation: String,
    val ruleId: String,
    val rulesetVersion: String,
    val nvdStatus: String,
    val kevStatus: String,
    val checkedAt: String?,
    val possibleMatch: PossibleCveDetail?
)

data class PossibleCveDetail(
    val cve: String,
    val declaredProduct: String,
    val declaredSource: String,
    val affectedCondition: String,
    val nvdUrl: String,
    val nvdCheckedAt: String,
    val kevMembership: String
)

/** El índice identifica el hallazgo dentro de la respuesta recibida, no un equipo global. */
fun EvaluationResult.findingDetail(index: Int): FindingDetail? {
    val finding = findings.getOrNull(index) ?: return null
    val match = finding.cve?.let { cve -> possibleCveMatches.singleOrNull { it.cve == cve } }
    return finding.toDetail(rulesetVersion, sources.nvd, sources.kev, sources.checkedAt, match)
}

private fun Finding.toDetail(
    rulesetVersion: String,
    nvd: SourceState,
    kev: SourceState,
    checkedAt: String?,
    match: PossibleCveMatch?
) = FindingDetail(
    title = title,
    category = when (category) {
        FindingCategory.TCP_SERVICE_ACCESSIBLE -> "Conexión TCP aceptada"
        FindingCategory.PROTOCOL_IDENTIFIED -> "Protocolo identificado"
        FindingCategory.PREVENTIVE_REVIEW -> "Revisión preventiva"
        FindingCategory.POSSIBLE_CVE_MATCH -> "Posible coincidencia CVE"
        FindingCategory.INSUFFICIENT_EVIDENCE -> "Evidencia insuficiente"
    },
    priority = when (priority) {
        FindingPriority.LOW -> "Baja"
        FindingPriority.MEDIUM -> "Media"
        FindingPriority.HIGH -> "Alta"
        FindingPriority.UNDETERMINED -> "Indeterminada"
    },
    confidence = when (confidence) {
        FindingConfidence.OBSERVED -> "Observada"
        FindingConfidence.DECLARED -> "Declarada"
        FindingConfidence.POSSIBLE -> "Posible"
        FindingConfidence.INCONCLUSIVE -> "Inconclusa"
    },
    evidence = evidence,
    explanation = explanation,
    action = action,
    limitation = limitation,
    ruleId = ruleId,
    rulesetVersion = rulesetVersion,
    nvdStatus = nvd.label(),
    kevStatus = kev.label(),
    checkedAt = checkedAt,
    possibleMatch = match?.toDetail()
)

private fun SourceState.label(): String = when (this) {
    SourceState.NOT_QUERIED -> "No consultada"
    SourceState.AVAILABLE -> "Disponible"
    SourceState.UNAVAILABLE -> "No disponible"
}

private fun PossibleCveMatch.toDetail() = PossibleCveDetail(
    cve = cve,
    declaredProduct = "$vendor · $product · $version" + (servicePort?.let { " · puerto $it" } ?: ""),
    declaredSource = when (identificationSource) {
        com.example.hogarscan.evaluations.domain.model.IdentificationSource.USER_DEVICE_LABEL -> "Etiqueta del dispositivo"
        com.example.hogarscan.evaluations.domain.model.IdentificationSource.USER_ADMIN_PANEL -> "Panel de administración"
        com.example.hogarscan.evaluations.domain.model.IdentificationSource.SERVICE_RESPONSE -> "Identidad anunciada por el servicio; no verificada independientemente"
    },
    affectedCondition = affectedCondition,
    nvdUrl = nvdUrl,
    nvdCheckedAt = nvdCheckedAt,
    kevMembership = when (inKev) {
        true -> "Incluida en el catálogo KEV consultado"
        false -> "No encontrada en el catálogo KEV consultado; esto no descarta riesgo"
        null -> "Presencia en KEV desconocida"
    }
)
