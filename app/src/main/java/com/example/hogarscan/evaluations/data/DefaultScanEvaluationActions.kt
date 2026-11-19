package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.data.local.EvaluationStore
import com.example.hogarscan.evaluations.domain.EvaluationSyncResult
import com.example.hogarscan.evaluations.domain.ScanEvaluationActions
import com.example.hogarscan.evaluations.domain.ScanEvaluationRunResult
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.scan.data.sync.ScanSummarySyncCoordinator
import com.example.hogarscan.scan.data.sync.SummarySyncRunResult
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.SummarySyncStatus

class DefaultScanEvaluationActions(
    private val store: EvaluationStore,
    private val workflow: EvaluationWorkflow,
    private val coordinator: ScanSummarySyncCoordinator,
    private val readStatus: suspend (String, SessionSnapshot) -> SummarySyncStatus?,
    private val rulesSource: HttpRulesDataSource
) : ScanEvaluationActions {
    override suspend fun load(scanId: String, expected: SessionSnapshot) = store.load(scanId, expected)

    override suspend fun evaluate(
        scanId: String,
        expected: SessionSnapshot,
        identifications: Map<Ipv4Address, DeclaredIdentification>,
        onProgress: (Int, Int) -> Unit
    ): ScanEvaluationRunResult = when (val result = workflow.run(scanId, expected, identifications, onProgress)) {
        is EvaluationWorkflowResult.Saved -> ScanEvaluationRunResult.Saved(result.evaluations, result.sync.toDomain())
        is EvaluationWorkflowResult.EvaluationFailed -> ScanEvaluationRunResult.EvaluationFailed(result.result.address)
        EvaluationWorkflowResult.NotFoundOrIncomplete -> ScanEvaluationRunResult.NotFoundOrIncomplete
        EvaluationWorkflowResult.AlreadyQueued -> ScanEvaluationRunResult.AlreadyQueued
        EvaluationWorkflowResult.SessionChanged -> ScanEvaluationRunResult.SessionChanged
    }

    override suspend fun sync(expected: SessionSnapshot) = coordinator.syncPending(expected).toDomain()
    override suspend fun summaryStatus(scanId: String, expected: SessionSnapshot) = readStatus(scanId, expected)
    override suspend fun rules() = (rulesSource.getRules() as? RulesRemoteResult.Success)?.rules
}

internal fun SummarySyncRunResult.toDomain(): EvaluationSyncResult = when (this) {
    SummarySyncRunResult.Completed -> EvaluationSyncResult.PROCESSED
    SummarySyncRunResult.Busy -> EvaluationSyncResult.BUSY
    SummarySyncRunResult.SessionUnavailable -> EvaluationSyncResult.SESSION_UNAVAILABLE
    SummarySyncRunResult.ContextChanged -> EvaluationSyncResult.CONTEXT_CHANGED
    SummarySyncRunResult.QueueChanged -> EvaluationSyncResult.QUEUE_CHANGED
    SummarySyncRunResult.GenerationUpdated -> EvaluationSyncResult.GENERATION_UPDATED
    is SummarySyncRunResult.GenerationRefreshRequired -> EvaluationSyncResult.GENERATION_REFRESH_REQUIRED
    is SummarySyncRunResult.Failure -> EvaluationSyncResult.FAILED
}
