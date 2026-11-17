package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.evaluations.domain.model.FindingCategory
import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus

/** Entrada local y respuesta que corresponden a un solo objetivo. */
data class TargetEvaluation(val input: EvaluationInput, val result: EvaluationResult)

/**
 * Prepara un resumen; no persiste, encola ni verifica la sesión actual de forma atómica.
 * La capa de persistencia debe validar propietario y generación al guardar.
 */
fun prepareEvaluatedScanSummary(
    scan: ScanRecord,
    evaluations: List<TargetEvaluation>,
    session: SessionSnapshot
): FrozenScanSummary {
    require(scan.status == ScanRecordStatus.COMPLETED)
    require(scan.ownerId == "user:${session.userId}")
    require(scan.summaryGeneration != null && scan.summaryGeneration == session.summaryGeneration)
    val targets = scan.config.targetAddresses.toSet()
    require(evaluations.size == targets.size)
    require(evaluations.map { it.input.address }.toSet() == targets)
    val resultsByAddress = scan.results.groupBy { it.address }
    evaluations.forEach { evaluation ->
        val input = evaluation.input
        require(input.scanId == scan.id && input.ownerId == scan.ownerId)
        val probes = requireNotNull(resultsByAddress[input.address])
        require(input.probes.map { it.port to it.outcome }.toSet() ==
            probes.map { it.port to it.outcome }.toSet())
    }

    val counts = evaluations.flatMap { it.result.findings }.groupingBy { it.category }.eachCount()
    fun count(category: FindingCategory): Int = counts[category] ?: 0
    return FrozenScanSummary(
        userId = session.userId,
        scanId = scan.id,
        summaryGeneration = scan.summaryGeneration,
        startedAtMillis = scan.startedAtMillis,
        finishedAtMillis = requireNotNull(scan.finishedAtMillis),
        addressesScanned = targets.size,
        addressesResponded = scan.results.filter {
            it.outcome == ProbeOutcome.ACCEPTED || it.outcome == ProbeOutcome.REJECTED
        }.map { it.address }.toSet().size,
        acceptedServices = scan.results.count { it.outcome == ProbeOutcome.ACCEPTED },
        findingsByCategory = FindingCounts(
            tcpServiceAccessible = count(FindingCategory.TCP_SERVICE_ACCESSIBLE),
            protocolIdentified = count(FindingCategory.PROTOCOL_IDENTIFIED),
            preventiveReview = count(FindingCategory.PREVENTIVE_REVIEW),
            possibleCveMatch = count(FindingCategory.POSSIBLE_CVE_MATCH),
            insufficientEvidence = count(FindingCategory.INSUFFICIENT_EVIDENCE)
        )
    )
}
