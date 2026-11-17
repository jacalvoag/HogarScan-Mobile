package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface ScanEvaluationOutcome {
    data class Completed(val evaluations: List<TargetEvaluation>) : ScanEvaluationOutcome
    data class Failed(val address: Ipv4Address, val reason: EvaluationFailure) : ScanEvaluationOutcome
    data object SessionChanged : ScanEvaluationOutcome
}

/** Evalúa secuencialmente cada objetivo; no guarda resultados ni reintenta. */
class EvaluateCompletedScan(
    private val sessions: SessionReader,
    private val gateway: EvaluationGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun run(
        scan: ScanRecord,
        session: SessionSnapshot,
        identifications: Map<Ipv4Address, DeclaredIdentification> = emptyMap(),
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanEvaluationOutcome {
        require(scan.status == ScanRecordStatus.COMPLETED)
        require(scan.ownerId == "user:${session.userId}")
        require(scan.summaryGeneration == session.summaryGeneration)
        require(identifications.keys.all { it in scan.config.targetAddresses })
        val completed = mutableListOf<TargetEvaluation>()
        val observations = scan.results.groupBy { it.address }
        for (address in scan.config.targetAddresses) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent(session)) return ScanEvaluationOutcome.SessionChanged
            val input = EvaluationInput.fromResults(scan.id, scan.ownerId, address,
                requireNotNull(observations[address]), identifications[address])
            val result = gateway.evaluate(input, session)
            currentCoroutineContext().ensureActive()
            if (!isCurrent(session)) return ScanEvaluationOutcome.SessionChanged
            when (result) {
                is EvaluationOutcome.Success -> completed.add(TargetEvaluation(input, result.result))
                is EvaluationOutcome.Failure -> return ScanEvaluationOutcome.Failed(address, result.reason)
            }
            onProgress(completed.size, scan.config.targetAddresses.size)
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrent(session)) return ScanEvaluationOutcome.SessionChanged
        return ScanEvaluationOutcome.Completed(completed.toList())
    }

    private fun isCurrent(expected: SessionSnapshot): Boolean {
        if (!sessions.isCurrentSession(expected, nowMillis())) return false
        val current = (sessions.state.value as? SessionState.Authenticated)?.session ?: return false
        return current.contextId == expected.contextId && current.userId == expected.userId &&
            current.summaryGeneration == expected.summaryGeneration
    }
}
