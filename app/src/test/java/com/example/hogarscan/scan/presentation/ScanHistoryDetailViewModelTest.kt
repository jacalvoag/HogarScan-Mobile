package com.example.hogarscan.scan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanHistoryDetailViewModelTest {

    private class FakeRepository : ScanHistoryRepository {
        val reads = mutableListOf<Pair<String, String>>()
        var read: suspend (String, String) -> ScanRecord? = { _, _ -> null }

        override suspend fun findById(scanId: String, ownerId: String): ScanRecord? {
            reads.add(scanId to ownerId)
            return read(scanId, ownerId)
        }

        override fun observeByOwner(ownerId: String): Flow<List<ScanRecordSummary>> =
            emptyFlow()

        override suspend fun insert(record: ScanRecord) {
            error("Operación no esperada")
        }

        override suspend fun appendResult(
            scanId: String, ownerId: String, result: ProbeResult
        ) {
            error("Operación no esperada")
        }

        override suspend fun finish(
            scanId: String, ownerId: String,
            status: ScanRecordStatus, finishedAtMillis: Long
        ) {
            error("Operación no esperada")
        }
    }

    private val store = ViewModelStore()

    private fun viewModel(
        repository: FakeRepository,
        handle: SavedStateHandle = SavedStateHandle()
    ): ScanHistoryViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                ScanHistoryViewModel(repository, { "local:test" }, handle)
            }
        }
    )[ScanHistoryViewModel::class.java]

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun opensRecordUsingCurrentOwnerAndPreservesPartialResults() = runTest {
        val expected = record("scan-1")
        val repository = FakeRepository().apply { read = { _, _ -> expected } }
        val handle = SavedStateHandle()
        val viewModel = viewModel(repository, handle)

        viewModel.openDetail(expected.id)
        assertEquals(ScanDetailUiState.Loading, viewModel.detailState.value)
        advanceUntilIdle()

        assertEquals(listOf("scan-1" to "local:test"), repository.reads)
        assertEquals(ScanDetailUiState.Loaded(expected), viewModel.detailState.value)
        assertEquals("scan-1", handle.get<String>("selected_scan_id"))
        assertEquals(1, expected.results.size)
        assertEquals(2, expected.config.plannedProbeCount)
    }

    @Test
    fun missingRecordPublishesNotFound() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.openDetail("missing")
        advanceUntilIdle()

        assertEquals(ScanDetailUiState.NotFound, viewModel.detailState.value)
        assertEquals(listOf("missing" to "local:test"), repository.reads)
    }

    @Test
    fun retryReloadsSelectedRecordAfterFailure() = runTest {
        val expected = record("scan-1")
        val repository = FakeRepository().apply {
            read = { _, _ -> throw IOException("Fallo de lectura") }
        }
        val viewModel = viewModel(repository)
        viewModel.openDetail(expected.id)
        advanceUntilIdle()
        assertEquals(ScanDetailUiState.Error, viewModel.detailState.value)

        repository.read = { _, _ -> expected }
        viewModel.retryDetail()
        advanceUntilIdle()

        assertEquals(ScanDetailUiState.Loaded(expected), viewModel.detailState.value)
        assertEquals(2, repository.reads.size)
    }

    @Test
    fun olderReadCannotOverwriteNewerSelection() = runTest {
        val older = record("older")
        val newer = record("newer")
        val repository = FakeRepository().apply {
            read = { id, _ ->
                withContext(NonCancellable) {
                    delay(if (id == "older") 100L else 10L)
                }
                if (id == "older") older else newer
            }
        }
        val viewModel = viewModel(repository)
        viewModel.openDetail("older")
        runCurrent()

        viewModel.openDetail("newer")
        advanceUntilIdle()

        assertEquals(ScanDetailUiState.Loaded(newer), viewModel.detailState.value)
    }

    @Test
    fun closePreventsPendingReadFromReopeningDetail() = runTest {
        val repository = FakeRepository().apply {
            read = { id, _ ->
                withContext(NonCancellable) { delay(100L) }
                record(id)
            }
        }
        val handle = SavedStateHandle()
        val viewModel = viewModel(repository, handle)
        viewModel.openDetail("scan-1")
        runCurrent()

        viewModel.closeDetail()
        advanceUntilIdle()

        assertEquals(ScanDetailUiState.Closed, viewModel.detailState.value)
        assertNull(handle.get<String>("selected_scan_id"))
    }

    @Test
    fun restoresSelectionAndReloadsRecord() = runTest {
        val expected = record("saved")
        val repository = FakeRepository().apply { read = { _, _ -> expected } }
        val handle = SavedStateHandle(mapOf("selected_scan_id" to expected.id))
        val viewModel = viewModel(repository, handle)
        advanceUntilIdle()

        assertEquals(listOf("saved" to "local:test"), repository.reads)
        assertEquals(ScanDetailUiState.Loaded(expected), viewModel.detailState.value)
    }

    private fun record(id: String): ScanRecord {
        val target = Ipv4Address.parse("192.168.1.20")
        return ScanRecord(
            id = id,
            ownerId = "local:test",
            config = ScanConfig(
                networkScope = NetworkScope(
                    Ipv4Address.parse("192.168.1.10"), 24
                ),
                startAddress = target,
                endAddress = target,
                ports = setOf(80, 443)
            ),
            startedAtMillis = 1_000L,
            status = ScanRecordStatus.CANCELLED,
            results = listOf(
                ProbeResult(target, 80, ProbeOutcome.ACCEPTED, 26L)
            ),
            finishedAtMillis = 2_000L
        )
    }
}
