package com.example.hogarscan.scan.data.sync

import com.example.hogarscan.auth.data.session.SessionStorageException
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.data.http.ScanSummaryRemoteResult
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.shared.data.http.ApiErrorDecoder
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

sealed interface SummarySyncRunResult {
    data object Completed : SummarySyncRunResult
    data object Busy : SummarySyncRunResult
    data object SessionUnavailable : SummarySyncRunResult
    data object ContextChanged : SummarySyncRunResult
    data object QueueChanged : SummarySyncRunResult
    data object GenerationUpdated : SummarySyncRunResult

    data class GenerationRefreshRequired(
        val currentGeneration: Int
    ) : SummarySyncRunResult

    data class Failure(
        val error: ApiResult
    ) : SummarySyncRunResult
}

class ScanSummarySyncCoordinator(
    private val repository: ScanSummaryRepository,
    private val sessions: SessionReader,
    private val send: suspend (
        FrozenScanSummary,
        SessionSnapshot
    ) -> ScanSummaryRemoteResult,
    private val updateGeneration: suspend (
        SessionSnapshot,
        Int
    ) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val operation = Mutex()

    suspend fun syncPending(expectedSession: SessionSnapshot? = null): SummarySyncRunResult {
        currentCoroutineContext().ensureActive()

        if (!operation.tryLock()) {
            return SummarySyncRunResult.Busy
        }

        return try {
            processPending(expectedSession)
        } catch (_: SessionStorageException) {
            SummarySyncRunResult.Failure(
                ApiResult.SessionStorageFailure
            )
        } finally {
            operation.unlock()
        }
    }

    private suspend fun processPending(expectedSession: SessionSnapshot?): SummarySyncRunResult {
        val expected =
            (sessions.state.value as? SessionState.Authenticated)?.session
                ?: return SummarySyncRunResult.SessionUnavailable

        if (expectedSession != null && (
                expected.contextId != expectedSession.contextId ||
                    expected.userId != expectedSession.userId ||
                    expected.summaryGeneration != expectedSession.summaryGeneration
                )) return SummarySyncRunResult.ContextChanged

        if (!isCurrent(expected)) {
            return SummarySyncRunResult.SessionUnavailable
        }

        repository.markOlderPendingObsolete(
            expected.userId,
            expected.summaryGeneration
        )

        if (!isCurrent(expected)) {
            return SummarySyncRunResult.ContextChanged
        }

        val pending = repository.findPending(
            expected.userId,
            expected.summaryGeneration
        )

        currentCoroutineContext().ensureActive()

        if (!isCurrent(expected)) {
            return SummarySyncRunResult.ContextChanged
        }

        if (pending.any {
                it.userId != expected.userId.lowercase() ||
                        it.summaryGeneration != expected.summaryGeneration
            }
        ) {
            return invalidResponse()
        }

        for (summary in pending) {
            currentCoroutineContext().ensureActive()

            if (!isCurrent(expected)) {
                return SummarySyncRunResult.ContextChanged
            }

            val result = send(summary, expected)

            currentCoroutineContext().ensureActive()

            if (!isCurrent(expected)) {
                return SummarySyncRunResult.ContextChanged
            }

            when (result) {
                is ScanSummaryRemoteResult.Success -> {
                    val receipt = result.receipt

                    if (
                        receipt.scanId != summary.scanId ||
                        receipt.summaryGeneration != summary.summaryGeneration
                    ) {
                        return invalidResponse()
                    }

                    if (!repository.markSynced(
                            summary,
                            receipt.receivedAtMillis
                        )
                    ) {
                        return SummarySyncRunResult.QueueChanged
                    }
                }

                is ScanSummaryRemoteResult.Failure -> {
                    val stop = handleFailure(
                        result.error,
                        summary,
                        expected
                    )

                    if (stop != null) {
                        return stop
                    }
                }
            }
        }

        currentCoroutineContext().ensureActive()

        return if (isCurrent(expected)) {
            SummarySyncRunResult.Completed
        } else {
            SummarySyncRunResult.ContextChanged
        }
    }

    /**
     * null indica que el conflicto se guardó y puede continuar el lote.
     */
    private suspend fun handleFailure(
        failure: ApiResult,
        summary: FrozenScanSummary,
        expected: SessionSnapshot
    ): SummarySyncRunResult? {
        if (failure !is ApiResult.HttpFailure ||
            failure.statusCode != 409
        ) {
            return SummarySyncRunResult.Failure(failure)
        }

        val error = ApiErrorDecoder.decode(failure)

        return when (error.code) {
            "SUMMARY_CONFLICT" -> {
                if (repository.markConflict(summary)) {
                    null
                } else {
                    SummarySyncRunResult.QueueChanged
                }
            }

            "STALE_SUMMARY_GENERATION" -> {
                val generation = error.currentSummaryGeneration

                if (generation == null ||
                    generation <= expected.summaryGeneration
                ) {
                    invalidResponse()
                } else {
                    advanceGeneration(expected, generation)
                }
            }

            "INVALID_SUMMARY_GENERATION" -> {
                val generation = error.currentSummaryGeneration

                if (generation == null ||
                    generation >= expected.summaryGeneration
                ) {
                    invalidResponse()
                } else {
                    SummarySyncRunResult.GenerationRefreshRequired(
                        generation
                    )
                }
            }

            else -> SummarySyncRunResult.Failure(failure)
        }
    }

    private suspend fun advanceGeneration(
        expected: SessionSnapshot,
        generation: Int
    ): SummarySyncRunResult {
        if (!updateGeneration(expected, generation)) {
            return SummarySyncRunResult.ContextChanged
        }

        currentCoroutineContext().ensureActive()

        val current = currentFor(expected)
            ?: return SummarySyncRunResult.ContextChanged

        if (current.summaryGeneration < generation) {
            return invalidResponse()
        }

        repository.markOlderPendingObsolete(
            current.userId,
            current.summaryGeneration
        )

        currentCoroutineContext().ensureActive()

        return if (currentFor(expected) != null) {
            SummarySyncRunResult.GenerationUpdated
        } else {
            SummarySyncRunResult.ContextChanged
        }
    }

    private fun isCurrent(expected: SessionSnapshot): Boolean =
        currentFor(expected)?.summaryGeneration ==
                expected.summaryGeneration

    private fun currentFor(
        expected: SessionSnapshot
    ): SessionSnapshot? {
        val current =
            (sessions.state.value as? SessionState.Authenticated)?.session
                ?: return null

        return current.takeIf {
            it.contextId == expected.contextId &&
                    it.userId == expected.userId &&
                    it.expiresAtMillis > nowMillis()
        }
    }

    private fun invalidResponse() =
        SummarySyncRunResult.Failure(ApiResult.InvalidResponse)
}
