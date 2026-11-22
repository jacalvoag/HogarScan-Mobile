package com.example.hogarscan.scan.data.local.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.SummarySyncStatus
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomScanSummaryRepositoryTest {

    private lateinit var database: HogarScanDatabase
    private lateinit var repository: ScanSummaryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HogarScanDatabase::class.java
        ).build()

        repository = RoomScanSummaryRepository(database.scanSummaryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoresOriginalPayloadFromAnotherRepositoryInstance() = runBlocking {
        val original = summary(1)
        insertSource(original)

        assertTrue(repository.enqueue(original))
        assertFalse(repository.enqueue(summary(1, findings = 99)))

        val nextRepository =
            RoomScanSummaryRepository(database.scanSummaryDao())

        val restored = nextRepository.findPending(
            USER_A.uppercase(),
            7
        ).single()

        assertEquals(original.toLocal(), restored.toLocal())
    }

    @Test
    fun receiptUpdatesOnlyTheMatchingPendingSummary() = runBlocking {
        val original = summary(1)
        insertSource(original)
        repository.enqueue(original)

        assertFalse(
            repository.markSynced(
                summary(1, userId = USER_B),
                3_000L
            )
        )
        assertFalse(
            repository.markSynced(
                summary(1, generation = 8),
                3_000L
            )
        )
        assertEquals(1, repository.findPending(USER_A, 7).size)

        val pending = repository.findPending(USER_A, 7).single()

        assertTrue(repository.markSynced(pending, 3_000L))
        assertTrue(repository.findPending(USER_A, 7).isEmpty())

        assertFalse(repository.enqueue(original))
        assertFalse(repository.markSynced(pending, 4_000L))

        assertEquals(
            original.toLocal().copy(
                syncStatus = SummarySyncStatus.SYNCED,
                receivedAtMillis = 3_000L
            ),
            database.scanSummaryDao().findById(original.scanId, USER_A)
        )
    }

    @Test
    fun obsoleteAndConflictOperationsPreserveOtherQueueEntries() =
        runBlocking {
            val old = summary(1, generation = 6)
            val current = summary(2)
            val future = summary(3, generation = 8)
            val otherAccount = summary(4, userId = USER_B)

            listOf(old, current, future, otherAccount).forEach {
                insertSource(it)
                repository.enqueue(it)
            }

            assertEquals(
                1,
                repository.markOlderPendingObsolete(USER_A.uppercase(), 7)
            )
            assertTrue(repository.findPending(USER_A, 6).isEmpty())

            assertTrue(repository.markConflict(current))
            assertFalse(repository.markConflict(current))
            assertTrue(repository.findPending(USER_A, 7).isEmpty())

            assertEquals(
                listOf(future.toLocal()),
                repository.findPending(USER_A, 8).map { it.toLocal() }
            )
            assertEquals(
                listOf(otherAccount.toLocal()),
                repository.findPending(USER_B, 7).map { it.toLocal() }
            )

            assertEquals(
                old.toLocal().copy(
                    syncStatus = SummarySyncStatus.OBSOLETE
                ),
                database.scanSummaryDao().findById(old.scanId, USER_A)
            )
            assertEquals(
                current.toLocal().copy(
                    syncStatus = SummarySyncStatus.CONFLICT
                ),
                database.scanSummaryDao().findById(current.scanId, USER_A)
            )
        }

    private fun summary(
        index: Int,
        userId: String = USER_A,
        generation: Int = 7,
        findings: Int = 1
    ) = FrozenScanSummary(
        userId = userId,
        scanId = "00000000-0000-4000-8000-" +
                index.toString().padStart(12, '0'),
        summaryGeneration = generation,
        startedAtMillis = 2_000L,
        finishedAtMillis = 1_000L,
        addressesScanned = 1,
        addressesResponded = 1,
        acceptedServices = 1,
        findingsByCategory = FindingCounts(
            tcpServiceAccessible = findings,
            protocolIdentified = 2,
            preventiveReview = 3,
            possibleCveMatch = 4,
            insufficientEvidence = 5
        )
    )

    private suspend fun insertSource(summary: FrozenScanSummary) {
        val target = Ipv4Address.parse("192.168.1.20")

        val record = ScanRecord(
            id = summary.scanId,
            ownerId = "user:${summary.userId}",
            config = ScanConfig(
                networkScope = NetworkScope(
                    Ipv4Address.parse("192.168.1.10"),
                    24
                ),
                startAddress = target,
                endAddress = target,
                ports = setOf(80)
            ),
            startedAtMillis = summary.startedAtMillis,
            status = ScanRecordStatus.COMPLETED,
            results = listOf(
                ProbeResult(
                    target,
                    80,
                    ProbeOutcome.ACCEPTED,
                    26L
                )
            ),
            finishedAtMillis = summary.finishedAtMillis,
            summaryGeneration = summary.summaryGeneration
        )

        database.scanRecordDao().insertWithDetails(record.toLocal())
    }

    private companion object {
        const val USER_A = "0f136c4e-447b-4970-8189-a38c29cb3421"
        const val USER_B = "a7842f53-8a2a-4634-b99e-1dfdb4c468be"
    }
}