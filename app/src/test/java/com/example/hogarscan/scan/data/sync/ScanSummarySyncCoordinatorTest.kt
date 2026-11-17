package com.example.hogarscan.scan.data.sync

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.data.http.ScanSummaryRemoteResult
import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.ScanSummaryReceipt
import com.example.hogarscan.scan.domain.model.SummarySyncStatus
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSummarySyncCoordinatorTest {

    private val account = SessionSnapshot(
        contextId = "context-a",
        userId = USER_A,
        email = "account-a@example.com",
        expiresAtMillis = 10_000L,
        summaryGeneration = 7
    )

    private val state = MutableStateFlow<SessionState>(
        SessionState.Authenticated(account)
    )

    private val sessions = object : SessionReader {
        override val state: StateFlow<SessionState> =
            this@ScanSummarySyncCoordinatorTest.state
    }

    private val queue = MemoryQueue()
    private val sent = mutableListOf<FrozenScanSummary>()
    private val updates = mutableListOf<Int>()

    private var remote: suspend (
        FrozenScanSummary,
        SessionSnapshot
    ) -> ScanSummaryRemoteResult = { summary, _ ->
        success(summary)
    }

    private var generationUpdater: suspend (
        SessionSnapshot,
        Int
    ) -> Boolean = { expected, generation ->
        val current =
            (state.value as? SessionState.Authenticated)?.session

        if (
            current == null ||
            current.contextId != expected.contextId ||
            current.userId != expected.userId ||
            generation < current.summaryGeneration
        ) {
            false
        } else {
            state.value = SessionState.Authenticated(
                current.copy(summaryGeneration = generation)
            )
            true
        }
    }

    private val coordinator by lazy {
        ScanSummarySyncCoordinator(
            repository = queue,
            sessions = sessions,
            send = { summary, expected ->
                sent += summary
                remote(summary, expected)
            },
            updateGeneration = { expected, generation ->
                updates += generation
                generationUpdater(expected, generation)
            },
            nowMillis = { 1_000L }
        )
    }

    @Test
    fun expectedSessionPreventsSyncingAnotherAccountOrGeneration() = runTest {
        add(1)
        state.value = SessionState.Authenticated(account.copy(contextId = "new-context"))
        assertEquals(SummarySyncRunResult.ContextChanged, coordinator.syncPending(account))
        assertEquals(0, queue.reads)
        assertTrue(sent.isEmpty())
        state.value = SessionState.Authenticated(account.copy(summaryGeneration = 8))
        assertEquals(SummarySyncRunResult.ContextChanged, coordinator.syncPending(account))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun unavailableSessionsDoNotReadOrSendTheQueue() = runTest {
        val original = add(1)

        listOf(
            SessionState.Guest,
            SessionState.Restoring,
            SessionState.Authenticated(
                account.copy(expiresAtMillis = 1_000L)
            )
        ).forEach {
            state.value = it

            assertEquals(
                SummarySyncRunResult.SessionUnavailable,
                coordinator.syncPending()
            )
        }

        assertEquals(0, queue.reads)
        assertTrue(sent.isEmpty())
        assertEquals(SummarySyncStatus.PENDING, queue.status(original))
    }

    @Test
    fun sendsOnlyCurrentAccountAndGeneration() = runTest {
        val current = add(1)
        val old = add(2, generation = 6)
        val other = add(3, userId = USER_B)
        val future = add(4, generation = 8)

        assertEquals(
            SummarySyncRunResult.Completed,
            coordinator.syncPending()
        )

        assertEquals(1, sent.size)
        assertSame(current, sent.single())

        assertEquals(SummarySyncStatus.SYNCED, queue.status(current))
        assertEquals(5_000L, queue.received[current.scanId])
        assertEquals(SummarySyncStatus.OBSOLETE, queue.status(old))
        assertEquals(SummarySyncStatus.PENDING, queue.status(other))
        assertEquals(SummarySyncStatus.PENDING, queue.status(future))
    }

    @Test
    fun temporaryFailuresStopTheBatchAndPreserveTheSamePayload() = runTest {
        val first = add(1)
        val second = add(2)

        listOf(
            ApiResult.NetworkFailure,
            ApiResult.TimedOut,
            ApiResult.HttpFailure(429, "{}", 60L),
            ApiResult.HttpFailure(503, "{}", null)
        ).forEach { failure ->
            sent.clear()
            remote = { _, _ ->
                ScanSummaryRemoteResult.Failure(failure)
            }

            val result = coordinator.syncPending()

            assertTrue(result is SummarySyncRunResult.Failure)
            assertSame(
                failure,
                (result as SummarySyncRunResult.Failure).error
            )
            assertEquals(1, sent.size)
            assertSame(first, sent.single())
            assertEquals(SummarySyncStatus.PENDING, queue.status(first))
            assertEquals(SummarySyncStatus.PENDING, queue.status(second))
        }
    }

    @Test
    fun contentConflictIsRecordedAndTheNextSummaryContinues() = runTest {
        val first = add(1)
        val second = add(2)

        remote = { summary, _ ->
            if (summary.scanId == first.scanId) {
                ScanSummaryRemoteResult.Failure(
                    ApiResult.HttpFailure(
                        409,
                        """{"code":"SUMMARY_CONFLICT","message":"Conflicto."}""",
                        null
                    )
                )
            } else {
                success(summary)
            }
        }

        assertEquals(
            SummarySyncRunResult.Completed,
            coordinator.syncPending()
        )

        assertEquals(2, sent.size)
        assertEquals(SummarySyncStatus.CONFLICT, queue.status(first))
        assertEquals(SummarySyncStatus.SYNCED, queue.status(second))
    }

    @Test
    fun staleGenerationUpdatesSessionAndObsoletesOnlyOlderAccountEntries() =
        runTest {
            val current = add(1)
            val old = add(2, generation = 6)
            val other = add(3, userId = USER_B)
            val next = add(4, generation = 8)

            remote = { _, _ ->
                generationFailure("STALE_SUMMARY_GENERATION", 8)
            }

            assertEquals(
                SummarySyncRunResult.GenerationUpdated,
                coordinator.syncPending()
            )

            assertEquals(listOf(8), updates)
            assertEquals(1, sent.size)
            assertSame(current, sent.single())
            assertEquals(7, current.summaryGeneration)

            val active =
                (state.value as SessionState.Authenticated).session

            assertEquals(8, active.summaryGeneration)
            assertEquals(SummarySyncStatus.OBSOLETE, queue.status(current))
            assertEquals(SummarySyncStatus.OBSOLETE, queue.status(old))
            assertEquals(SummarySyncStatus.PENDING, queue.status(other))
            assertEquals(SummarySyncStatus.PENDING, queue.status(next))
        }

    @Test
    fun futureGenerationRequestsRefreshWithoutRewritingOrResending() =
        runTest {
            val original = add(1)

            remote = { _, _ ->
                generationFailure("INVALID_SUMMARY_GENERATION", 6)
            }

            assertEquals(
                SummarySyncRunResult.GenerationRefreshRequired(6),
                coordinator.syncPending()
            )

            assertEquals(1, sent.size)
            assertTrue(updates.isEmpty())
            assertEquals(7, original.summaryGeneration)
            assertEquals(SummarySyncStatus.PENDING, queue.status(original))
        }

    @Test
    fun contextChangesDuringReadOrResponsePreventAcknowledgement() = runTest {
        val original = add(1)

        queue.onRead = {
            state.value = SessionState.Guest
        }

        assertEquals(
            SummarySyncRunResult.ContextChanged,
            coordinator.syncPending()
        )
        assertTrue(sent.isEmpty())

        queue.onRead = {}

        listOf(
            SessionState.Guest,
            SessionState.Authenticated(
                account.copy(
                    contextId = "context-b",
                    userId = USER_B
                )
            ),
            SessionState.Authenticated(
                account.copy(contextId = "another-login")
            ),
            SessionState.Authenticated(
                account.copy(summaryGeneration = 8)
            ),
            SessionState.Authenticated(
                account.copy(expiresAtMillis = 1_000L)
            )
        ).forEach { replacement ->
            state.value = SessionState.Authenticated(account)

            remote = { summary, _ ->
                state.value = replacement
                success(summary)
            }

            assertEquals(
                SummarySyncRunResult.ContextChanged,
                coordinator.syncPending()
            )
            assertEquals(SummarySyncStatus.PENDING, queue.status(original))
        }

        assertTrue(queue.received.isEmpty())
    }

    @Test
    fun concurrentRunIsRejectedAndCancellationReleasesTheCoordinator() =
        runTest {
            val original = add(1)
            remote = { _, _ -> awaitCancellation() }
            var returned = false

            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.syncPending()
                returned = true
            }

            assertEquals(1, sent.size)
            assertEquals(
                SummarySyncRunResult.Busy,
                coordinator.syncPending()
            )

            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            assertFalse(returned)
            assertEquals(SummarySyncStatus.PENDING, queue.status(original))

            remote = { summary, _ -> success(summary) }

            assertEquals(
                SummarySyncRunResult.Completed,
                coordinator.syncPending()
            )
            assertEquals(SummarySyncStatus.SYNCED, queue.status(original))
        }

    @Test
    fun accountChangeDuringGenerationUpdatePreservesPendingEntries() =
        runTest {
            val original = add(1)
            val other = add(2, userId = USER_B)

            remote = { _, _ ->
                generationFailure("STALE_SUMMARY_GENERATION", 8)
            }

            generationUpdater = { _, _ ->
                state.value = SessionState.Authenticated(
                    account.copy(
                        contextId = "context-b",
                        userId = USER_B
                    )
                )
                false
            }

            assertEquals(
                SummarySyncRunResult.ContextChanged,
                coordinator.syncPending()
            )

            assertEquals(SummarySyncStatus.PENDING, queue.status(original))
            assertEquals(SummarySyncStatus.PENDING, queue.status(other))
        }

    private suspend fun add(
        index: Int,
        userId: String = USER_A,
        generation: Int = 7
    ): FrozenScanSummary {
        val summary = FrozenScanSummary(
            userId = userId,
            scanId = "00000000-0000-4000-8000-" +
                    index.toString().padStart(12, '0'),
            summaryGeneration = generation,
            startedAtMillis = 1_000L,
            finishedAtMillis = 2_000L,
            addressesScanned = 1,
            addressesResponded = 1,
            acceptedServices = 1,
            findingsByCategory = FindingCounts(1, 0, 0, 0, 0)
        )

        queue.enqueue(summary)
        return summary
    }

    private fun success(summary: FrozenScanSummary) =
        ScanSummaryRemoteResult.Success(
            ScanSummaryReceipt(
                scanId = summary.scanId,
                summaryGeneration = summary.summaryGeneration,
                receivedAtMillis = 5_000L,
                created = true
            )
        )

    private fun generationFailure(code: String, generation: Int) =
        ScanSummaryRemoteResult.Failure(
            ApiResult.HttpFailure(
                409,
                """{"code":"$code","message":"Generación distinta.","details":{"current_summary_generation":$generation}}""",
                null
            )
        )

    private class MemoryQueue : ScanSummaryRepository {
        private val records = linkedMapOf<String, FrozenScanSummary>()
        private val statuses = mutableMapOf<String, SummarySyncStatus>()

        val received = mutableMapOf<String, Long>()
        var reads = 0
        var onRead: suspend () -> Unit = {}

        fun status(summary: FrozenScanSummary): SummarySyncStatus? =
            statuses[summary.scanId]

        override suspend fun enqueue(summary: FrozenScanSummary): Boolean {
            if (summary.scanId in records) {
                return false
            }

            records[summary.scanId] = summary
            statuses[summary.scanId] = SummarySyncStatus.PENDING
            return true
        }

        override suspend fun findPending(
            userId: String,
            generation: Int
        ): List<FrozenScanSummary> {
            reads++
            onRead()

            return records.values.filter {
                it.userId == userId &&
                        it.summaryGeneration == generation &&
                        statuses[it.scanId] == SummarySyncStatus.PENDING
            }
        }

        override suspend fun markSynced(
            summary: FrozenScanSummary,
            receivedAtMillis: Long
        ): Boolean {
            if (!change(summary, SummarySyncStatus.SYNCED)) {
                return false
            }

            received[summary.scanId] = receivedAtMillis
            return true
        }

        override suspend fun markConflict(
            summary: FrozenScanSummary
        ): Boolean = change(summary, SummarySyncStatus.CONFLICT)

        override suspend fun markOlderPendingObsolete(
            userId: String,
            currentGeneration: Int
        ): Int {
            val older = records.values.filter {
                it.userId == userId &&
                        it.summaryGeneration < currentGeneration &&
                        statuses[it.scanId] == SummarySyncStatus.PENDING
            }

            older.forEach {
                statuses[it.scanId] = SummarySyncStatus.OBSOLETE
            }

            return older.size
        }

        private fun change(
            summary: FrozenScanSummary,
            status: SummarySyncStatus
        ): Boolean {
            val stored = records[summary.scanId] ?: return false

            if (
                stored.userId != summary.userId ||
                stored.summaryGeneration != summary.summaryGeneration ||
                statuses[summary.scanId] != SummarySyncStatus.PENDING
            ) {
                return false
            }

            statuses[summary.scanId] = status
            return true
        }
    }

    private companion object {
        const val USER_A = "0f136c4e-447b-4970-8189-a38c29cb3421"
        const val USER_B = "a7842f53-8a2a-4634-b99e-1dfdb4c468be"
    }
}
