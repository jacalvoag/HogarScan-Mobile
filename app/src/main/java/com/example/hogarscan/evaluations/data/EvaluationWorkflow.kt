package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.evaluations.data.local.EvaluationSaveResult
import com.example.hogarscan.evaluations.domain.EvaluateCompletedScan
import com.example.hogarscan.evaluations.domain.ScanEvaluationOutcome
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.scan.data.sync.SummarySyncRunResult
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface EvaluationWorkflowResult {
    data class Saved(val evaluations: List<TargetEvaluation>, val sync: SummarySyncRunResult) : EvaluationWorkflowResult
    data class EvaluationFailed(val result: ScanEvaluationOutcome.Failed) : EvaluationWorkflowResult
    data object NotFoundOrIncomplete : EvaluationWorkflowResult
    data object AlreadyQueued : EvaluationWorkflowResult
    data object SessionChanged : EvaluationWorkflowResult
}

/** Coordina red, guardado atómico y sincronización, sin acoplarse a una pantalla. */
class EvaluationWorkflow(
    private val sessions: SessionReader,
    private val history: ScanHistoryRepository,
    private val evaluator: EvaluateCompletedScan,
    private val save: suspend (String, List<TargetEvaluation>, SessionSnapshot) -> EvaluationSaveResult,
    private val sync: suspend (SessionSnapshot) -> SummarySyncRunResult,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun run(
        scanId: String,
        session: SessionSnapshot,
        identifications: Map<Ipv4Address, DeclaredIdentification> = emptyMap(),
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): EvaluationWorkflowResult {
        if (!isCurrent(session)) return EvaluationWorkflowResult.SessionChanged
        val scan = history.findById(scanId, "user:${session.userId}")
        currentCoroutineContext().ensureActive()
        if (!isCurrent(session)) return EvaluationWorkflowResult.SessionChanged
        if (scan == null || scan.status != ScanRecordStatus.COMPLETED ||
            scan.summaryGeneration != session.summaryGeneration) return EvaluationWorkflowResult.NotFoundOrIncomplete
        val evaluations = when (val result = evaluator.run(scan, session, identifications, onProgress)) {
            is ScanEvaluationOutcome.Failed -> return EvaluationWorkflowResult.EvaluationFailed(result)
            ScanEvaluationOutcome.SessionChanged -> return EvaluationWorkflowResult.SessionChanged
            is ScanEvaluationOutcome.Completed -> result.evaluations
        }
        if (!isCurrent(session)) return EvaluationWorkflowResult.SessionChanged
        when (save(scanId, evaluations, session)) {
            EvaluationSaveResult.SESSION_CHANGED -> return EvaluationWorkflowResult.SessionChanged
            EvaluationSaveResult.ALREADY_QUEUED -> return EvaluationWorkflowResult.AlreadyQueued
            EvaluationSaveResult.SAVED -> Unit
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrent(session)) return EvaluationWorkflowResult.SessionChanged
        val syncResult = sync(session)
        currentCoroutineContext().ensureActive()
        if (!isCurrent(session)) return EvaluationWorkflowResult.SessionChanged
        return EvaluationWorkflowResult.Saved(evaluations, syncResult)
    }

    private fun isCurrent(expected: SessionSnapshot): Boolean {
        if (!sessions.isCurrentSession(expected, nowMillis())) return false
        val current = (sessions.state.value as? SessionState.Authenticated)?.session ?: return false
        return current.contextId == expected.contextId && current.userId == expected.userId &&
            current.summaryGeneration == expected.summaryGeneration
    }
}
