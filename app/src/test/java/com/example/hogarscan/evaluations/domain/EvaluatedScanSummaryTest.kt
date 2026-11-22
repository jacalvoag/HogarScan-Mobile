package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class EvaluatedScanSummaryTest {
    private val user = "2c48661b-b872-4ea4-ad9e-3df603a5c0f9"
    private val scanId = "6ce5e6b1-9509-48a7-bc9d-f0f872364726"
    private val session = SessionSnapshot("context", user, "a@example.com", Long.MAX_VALUE, 3)
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24),
        Ipv4Address.parse("192.168.1.2"), Ipv4Address.parse("192.168.1.3"), setOf(80, 443))
    private val probes = listOf(
        ProbeResult(config.targetAddresses[0], 80, ProbeOutcome.ACCEPTED, 5),
        ProbeResult(config.targetAddresses[0], 443, ProbeOutcome.TIMED_OUT, 5),
        ProbeResult(config.targetAddresses[1], 80, ProbeOutcome.REJECTED, 5),
        ProbeResult(config.targetAddresses[1], 443, ProbeOutcome.ERROR, 5)
    )
    private fun scan(status: ScanRecordStatus = ScanRecordStatus.COMPLETED, generation: Int? = 3) =
        ScanRecord(scanId, "user:$user", config, 1000, status, probes, 2000, generation)
    private fun finding(category: FindingCategory) = Finding(category, "Título", FindingPriority.LOW,
        FindingConfidence.OBSERVED, "Evidencia", "Explicación", "Acción", "Limitación", "regla", null)
    private fun evaluation(addressIndex: Int, category: FindingCategory): TargetEvaluation {
        val address = config.targetAddresses[addressIndex]
        val input = EvaluationInput.fromResults(scanId, "user:$user", address, probes.filter { it.address == address })
        return TargetEvaluation(input, EvaluationResult("1", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), listOf(finding(category)), emptyList()))
    }

    @Test fun `congela conteos por revision y cuenta respuestas no dispositivos`() {
        val result = prepareEvaluatedScanSummary(scan(), listOf(
            evaluation(0, FindingCategory.TCP_SERVICE_ACCESSIBLE), evaluation(1, FindingCategory.INSUFFICIENT_EVIDENCE)
        ), session)
        assertEquals(2, result.addressesScanned)
        assertEquals(2, result.addressesResponded)
        assertEquals(1, result.acceptedServices)
        assertEquals(1, result.findingsByCategory.tcpServiceAccessible)
        assertEquals(1, result.findingsByCategory.insufficientEvidence)
        assertEquals(3, result.summaryGeneration)
    }

    @Test fun `no admite faltantes duplicados ni revisiones parciales`() {
        val one = evaluation(0, FindingCategory.TCP_SERVICE_ACCESSIBLE)
        val two = evaluation(1, FindingCategory.INSUFFICIENT_EVIDENCE)
        listOf(listOf(one), listOf(one, one)).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { prepareEvaluatedScanSummary(scan(), invalid, session) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            prepareEvaluatedScanSummary(scan(ScanRecordStatus.CANCELLED), listOf(one, two), session)
        }
    }

    @Test fun `rechaza propietarios generaciones y observaciones diferentes`() {
        val one = evaluation(0, FindingCategory.TCP_SERVICE_ACCESSIBLE)
        val two = evaluation(1, FindingCategory.INSUFFICIENT_EVIDENCE)
        assertThrows(IllegalArgumentException::class.java) { prepareEvaluatedScanSummary(scan(), listOf(one, two), session.copy(summaryGeneration = 4)) }
        assertThrows(IllegalArgumentException::class.java) { prepareEvaluatedScanSummary(scan(), listOf(one, two), session.copy(userId = "different")) }
        val altered = EvaluationInput.fromResults(scanId, "user:$user", one.input.address,
            probes.filter { it.address == one.input.address }.map { it.copy(outcome = ProbeOutcome.ERROR) })
        assertThrows(IllegalArgumentException::class.java) {
            prepareEvaluatedScanSummary(scan(), listOf(one.copy(input = altered), two), session)
        }
    }
}
