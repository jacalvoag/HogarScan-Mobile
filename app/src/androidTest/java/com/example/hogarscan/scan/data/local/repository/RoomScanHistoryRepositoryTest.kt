package com.example.hogarscan.scan.data.local.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomScanHistoryRepositoryTest {

    private lateinit var database: HogarScanDatabase
    private lateinit var dao: ScanRecordDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HogarScanDatabase::class.java
        ).build()
        dao = database.scanRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recoversPreviousRunsAndPreservesTheirDetails() = runBlocking {
        val previous = listOf(
            record("partial"),
            record("all-attempts", fullResults = true),
            record("other-owner", ownerId = "local:other")
        )
        previous.forEach { dao.insertWithDetails(it.toLocal()) }
        val repository = RoomScanHistoryRepository(dao) { 9_000L }

        previous.forEach { original ->
            val recovered = checkNotNull(
                repository.findById(original.id, original.ownerId)
            )
            assertEquals(
                original.toLocal().record.copy(
                    status = ScanRecordStatus.INTERRUPTED,
                    finishedAtMillis = 9_000L
                ),
                recovered.toLocal().record
            )
            assertEquals(original.config.ports, recovered.config.ports)
            assertEquals(original.results, recovered.results)
        }

        assertNull(repository.findById("other-owner", "local:test"))
    }

    @Test
    fun preservesAlreadyClosedRevisions() = runBlocking {
        val originals = ScanRecordStatus.entries
            .filter { it != ScanRecordStatus.RUNNING }
            .map { record(it.name, status = it) }
        originals.forEach { dao.insertWithDetails(it.toLocal()) }
        val repository = RoomScanHistoryRepository(dao) { 9_000L }

        val summaries = withTimeout(10_000L) {
            repository.observeByOwner("local:test").first()
        }
        assertEquals(originals.size, summaries.size)

        originals.forEach { original ->
            val restored = checkNotNull(
                repository.findById(original.id, original.ownerId)
            )
            assertEquals(original.toLocal(), restored.toLocal())
        }
    }

    @Test
    fun initializesOnceAndDoesNotInterruptCurrentRun() = runBlocking {
        val calls = AtomicInteger()
        val repository = RoomScanHistoryRepository(dao) {
            calls.incrementAndGet()
            9_000L
        }
        val active = record("active")

        val insertion = async { repository.insert(active) }
        val readers = List(8) {
            async {
                withTimeout(10_000L) {
                    repository.observeByOwner(active.ownerId).first()
                }
            }
        }
        insertion.await()
        readers.forEach { it.await() }

        repeat(3) {
            val current = checkNotNull(
                repository.findById(active.id, active.ownerId)
            )
            assertEquals(ScanRecordStatus.RUNNING, current.status)
            assertNull(current.finishedAtMillis)
        }

        val remaining = record("active", fullResults = true).results.last()
        repository.appendResult(active.id, active.ownerId, remaining)
        repository.finish(
            active.id,
            active.ownerId,
            ScanRecordStatus.COMPLETED,
            12_000L
        )

        val completed = checkNotNull(
            repository.findById(active.id, active.ownerId)
        )
        assertEquals(ScanRecordStatus.COMPLETED, completed.status)
        assertEquals(2, completed.results.size)
        assertEquals(1, calls.get())
    }

    @Test
    fun newRepositoryRecoversRunLeftByPreviousProcess() = runBlocking {
        val active = record("previous-process")
        val previousProcess = RoomScanHistoryRepository(dao) { 5_000L }
        previousProcess.insert(active)

        // Esta instancia representa el repositorio del siguiente proceso.
        val nextProcess = RoomScanHistoryRepository(dao) { 9_000L }
        val recovered = checkNotNull(
            nextProcess.findById(active.id, active.ownerId)
        )

        assertEquals(ScanRecordStatus.INTERRUPTED, recovered.status)
        assertEquals(9_000L, recovered.finishedAtMillis)
        assertEquals(active.results, recovered.results)
    }

    @Test
    fun failedInitializationCanBeRetried() = runBlocking {
        val previous = record("previous")
        dao.insertWithDetails(previous.toLocal())
        var attempts = 0
        val repository = RoomScanHistoryRepository(dao) {
            attempts++
            check(attempts > 1) { "Fallo de inicialización simulado" }
            9_000L
        }

        try {
            repository.findById(previous.id, previous.ownerId)
            fail("La primera inicialización debe fallar.")
        } catch (_: IllegalStateException) {
            // Debe permitir otro intento de inicialización.
        }

        assertEquals(
            ScanRecordStatus.RUNNING,
            dao.findById(previous.id, previous.ownerId)?.record?.status
        )

        val recovered = checkNotNull(
            repository.findById(previous.id, previous.ownerId)
        )
        assertEquals(ScanRecordStatus.INTERRUPTED, recovered.status)
        assertEquals(2, attempts)
    }

    private fun record(
        id: String,
        status: ScanRecordStatus = ScanRecordStatus.RUNNING,
        fullResults: Boolean = false,
        ownerId: String = "local:test"
    ): ScanRecord {
        val target = Ipv4Address.parse("192.168.1.20")
        val results = listOf(
            ProbeResult(target, 80, ProbeOutcome.ACCEPTED, 26L),
            ProbeResult(target, 443, ProbeOutcome.TIMED_OUT, 1_000L)
        )
        return ScanRecord(
            id = id,
            ownerId = ownerId,
            config = ScanConfig(
                NetworkScope(Ipv4Address.parse("192.168.1.10"), 24),
                target,
                target,
                setOf(80, 443)
            ),
            startedAtMillis = 1_000L,
            status = status,
            results = if (fullResults || status == ScanRecordStatus.COMPLETED) {
                results
            } else {
                results.take(1)
            },
            finishedAtMillis = if (status == ScanRecordStatus.RUNNING) {
                null
            } else {
                3_000L
            }
        )
    }
}
