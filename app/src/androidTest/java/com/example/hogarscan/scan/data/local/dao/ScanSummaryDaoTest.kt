package com.example.hogarscan.scan.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.data.local.relation.ScanRecordWithDetails
import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.SummarySyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanSummaryDaoTest {

    private lateinit var database: HogarScanDatabase
    private lateinit var dao: ScanSummaryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HogarScanDatabase::class.java
        ).build()

        dao = database.scanSummaryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateEnqueuePreservesOriginalContent() = runBlocking {
        val original = summary(1)
        insertSource(original)

        assertTrue(dao.enqueue(original))

        val changed = summary(1, findings = 8)
        assertFalse(dao.enqueue(changed))

        assertEquals(
            original.toLocal(),
            dao.findById(original.scanId, USER_A)
        )
        assertNull(dao.findById(original.scanId, USER_B))
    }

    @Test
    fun pendingQueriesSeparateAccountsAndGenerations() = runBlocking {
        val current = summary(1)
        val older = summary(2, generation = 6)
        val newer = summary(3, generation = 8)
        val otherAccount = summary(4, userId = USER_B)

        listOf(current, older, newer, otherAccount).forEach {
            insertSource(it)
            dao.enqueue(it)
        }

        assertEquals(
            listOf(current.toLocal()),
            dao.findPending(USER_A, 7)
        )
        assertEquals(
            listOf(otherAccount.toLocal()),
            dao.findPending(USER_B, 7)
        )

        assertEquals(1, dao.markOlderPendingObsolete(USER_A, 7))

        assertEquals(
            SummarySyncStatus.OBSOLETE,
            dao.findById(older.scanId, USER_A)?.syncStatus
        )
        assertEquals(
            listOf(newer.toLocal()),
            dao.findPending(USER_A, 8)
        )
        assertEquals(
            listOf(otherAccount.toLocal()),
            dao.findPending(USER_B, 7)
        )
    }

    @Test
    fun receiptRequiresMatchingAccountAndGeneration() = runBlocking {
        val original = summary(1)
        insertSource(original)
        dao.enqueue(original)

        assertEquals(
            0,
            dao.markSynced(original.scanId, USER_B, 7, 3_000L)
        )
        assertEquals(
            0,
            dao.markSynced(original.scanId, USER_A, 8, 3_000L)
        )
        assertEquals(
            original.toLocal(),
            dao.findById(original.scanId, USER_A)
        )

        assertEquals(
            1,
            dao.markSynced(original.scanId, USER_A, 7, 3_000L)
        )

        assertEquals(
            original.toLocal().copy(
                syncStatus = SummarySyncStatus.SYNCED,
                receivedAtMillis = 3_000L
            ),
            dao.findById(original.scanId, USER_A)
        )

        assertFalse(dao.enqueue(original))
        assertEquals(
            0,
            dao.markConflict(original.scanId, USER_A, 7)
        )
        assertEquals(
            0,
            dao.markSynced(original.scanId, USER_A, 7, 4_000L)
        )
        assertEquals(
            3_000L,
            dao.findById(original.scanId, USER_A)?.receivedAtMillis
        )
        assertTrue(dao.findPending(USER_A, 7).isEmpty())
    }

    @Test
    fun conflictPreservesContentAndLeavesPendingQueue() = runBlocking {
        val original = summary(1)
        insertSource(original)
        dao.enqueue(original)

        assertEquals(
            0,
            dao.markConflict(original.scanId, USER_B, 7)
        )
        assertEquals(
            0,
            dao.markConflict(original.scanId, USER_A, 8)
        )
        assertEquals(
            1,
            dao.markConflict(original.scanId, USER_A, 7)
        )

        assertEquals(
            original.toLocal().copy(
                syncStatus = SummarySyncStatus.CONFLICT
            ),
            dao.findById(original.scanId, USER_A)
        )
        assertTrue(dao.findPending(USER_A, 7).isEmpty())
        assertFalse(dao.enqueue(original))
        assertEquals(
            0,
            dao.markSynced(original.scanId, USER_A, 7, 3_000L)
        )
    }

    @Test
    fun rejectsMissingSourceWrongOwnerGenerationAndDates() = runBlocking {
        val missing = summary(1)
        assertRejected { dao.enqueue(missing) }

        val wrongOwner = summary(2)
        insertSource(wrongOwner, ownerId = "user:$USER_B")
        assertRejected { dao.enqueue(wrongOwner) }

        val legacy = summary(3)
        insertSource(legacy, generation = null)
        assertRejected { dao.enqueue(legacy) }

        val differentGeneration = summary(4)
        insertSource(differentGeneration, generation = 6)
        assertRejected { dao.enqueue(differentGeneration) }

        val differentDates = summary(5)
        insertSource(differentDates, finishedAtMillis = 2_500L)
        assertRejected { dao.enqueue(differentDates) }

        assertTrue(dao.findPending(USER_A, 7).isEmpty())
    }

    @Test
    fun rejectsEveryExecutionStatusExceptCompleted() = runBlocking {
        ScanRecordStatus.entries
            .filter { it != ScanRecordStatus.COMPLETED }
            .forEachIndexed { index, status ->
                val candidate = summary(index + 1)
                insertSource(candidate, status = status)

                assertRejected { dao.enqueue(candidate) }

                assertNull(
                    dao.findById(candidate.scanId, candidate.userId)
                )
            }
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("El resumen debe rechazarse.")
        } catch (_: IllegalArgumentException) {
            // La validación debe impedir la inserción.
        }
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
        startedAtMillis = 1_000L,
        finishedAtMillis = 2_000L,
        addressesScanned = 1,
        addressesResponded = 1,
        acceptedServices = 1,
        findingsByCategory = FindingCounts(
            tcpServiceAccessible = findings,
            protocolIdentified = 0,
            preventiveReview = 0,
            possibleCveMatch = 0,
            insufficientEvidence = 0
        )
    )

    private suspend fun insertSource(
        summary: FrozenScanSummary,
        ownerId: String = "user:${summary.userId}",
        generation: Int? = summary.summaryGeneration,
        status: ScanRecordStatus = ScanRecordStatus.COMPLETED,
        finishedAtMillis: Long = summary.finishedAtMillis
    ) {
        val target = Ipv4Address.parse("192.168.1.20").value

        database.scanRecordDao().insertWithDetails(
            ScanRecordWithDetails(
                record = ScanRecordEntity(
                    id = summary.scanId,
                    ownerId = ownerId,
                    localAddress =
                        Ipv4Address.parse("192.168.1.10").value,
                    prefixLength = 24,
                    startAddress = target,
                    endAddress = target,
                    status = status,
                    startedAtMillis = summary.startedAtMillis,
                    finishedAtMillis =
                        if (status == ScanRecordStatus.RUNNING) {
                            null
                        } else {
                            finishedAtMillis
                        },
                    summaryGeneration = generation
                ),
                ports = listOf(
                    ScanPortEntity(
                        scanId = summary.scanId,
                        port = 80
                    )
                ),
                results = if (status == ScanRecordStatus.RUNNING) {
                    emptyList()
                } else {
                    listOf(
                        ProbeResultEntity(
                            scanId = summary.scanId,
                            address = target,
                            port = 80,
                            outcome = ProbeOutcome.ACCEPTED,
                            durationMillis = 26L
                        )
                    )
                }
            )
        )
    }

    private companion object {
        const val USER_A = "0f136c4e-447b-4970-8189-a38c29cb3421"
        const val USER_B = "a7842f53-8a2a-4634-b99e-1dfdb4c468be"
    }
}