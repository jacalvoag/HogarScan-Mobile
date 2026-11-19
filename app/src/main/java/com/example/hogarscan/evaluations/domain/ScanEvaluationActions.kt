package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.SummarySyncStatus

/** Capacidades del flujo integrado, sin exponer Room ni resultados HTTP a presentación. */
interface ScanEvaluationActions {
    suspend fun load(scanId: String, expected: SessionSnapshot): List<TargetEvaluation>?
    suspend fun evaluate(
        scanId: String,
        expected: SessionSnapshot,
        identifications: Map<Ipv4Address, DeclaredIdentification>,
        onProgress: (Int, Int) -> Unit
    ): ScanEvaluationRunResult
    suspend fun sync(expected: SessionSnapshot): EvaluationSyncResult
    suspend fun summaryStatus(scanId: String, expected: SessionSnapshot): SummarySyncStatus?
    suspend fun rules(): EvaluationRules?
}

enum class EvaluationSyncResult {
    PROCESSED, BUSY, SESSION_UNAVAILABLE, CONTEXT_CHANGED, QUEUE_CHANGED,
    GENERATION_UPDATED, GENERATION_REFRESH_REQUIRED, FAILED
}

sealed interface ScanEvaluationRunResult {
    data class Saved(val evaluations: List<TargetEvaluation>, val sync: EvaluationSyncResult) : ScanEvaluationRunResult
    data class EvaluationFailed(val address: Ipv4Address) : ScanEvaluationRunResult
    data object NotFoundOrIncomplete : ScanEvaluationRunResult
    data object AlreadyQueued : ScanEvaluationRunResult
    data object SessionChanged : ScanEvaluationRunResult
}
