package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanHistoryViewModelTest {

    private class FakeRepository : ScanHistoryRepository {
        val records = MutableStateFlow<List<ScanRecordSummary>>(emptyList())
        var observedOwner: String? = null
        var observations = 0
        var failure: Exception? = null

        override fun observeByOwner(ownerId: String): Flow<List<ScanRecordSummary>> {
            observations++
            observedOwner = ownerId
            failure?.let { throw it }
            return records
        }

        override suspend fun insert(record: ScanRecord) {
            error("Operación no esperada en el listado")
        }

        override suspend fun findById(scanId: String, ownerId: String): ScanRecord? =
            error("Operación no esperada en el listado")

        override suspend fun appendResult(
            scanId: String, ownerId: String, result: ProbeResult
        ) {
            error("Operación no esperada en el listado")
        }

        override suspend fun finish(
            scanId: String, ownerId: String,
            status: ScanRecordStatus, finishedAtMillis: Long
        ) {
            error("Operación no esperada en el listado")
        }
    }

    private val store = ViewModelStore()

    private fun viewModel(
        repository: FakeRepository,
        ownerId: suspend () -> String = { "local:test" }
    ): ScanHistoryViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer { ScanHistoryViewModel(repository, ownerId) }
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
    fun observesOwnerAndPublishesEmptyHistoryAndUpdates() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        assertEquals(ScanHistoryUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals("local:test", repository.observedOwner)
        assertEquals(ScanHistoryUiState.Loaded(emptyList()), viewModel.uiState.value)

        val running = ScanRecordSummary(
            id = "scan-1",
            ownerId = "local:test",
            status = ScanRecordStatus.RUNNING,
            startedAtMillis = 1_000L,
            finishedAtMillis = null
        )
        repository.records.value = listOf(running)
        advanceUntilIdle()
        assertEquals(ScanHistoryUiState.Loaded(listOf(running)), viewModel.uiState.value)

        val completed = running.copy(
            status = ScanRecordStatus.COMPLETED,
            finishedAtMillis = 2_000L
        )
        repository.records.value = listOf(completed)
        advanceUntilIdle()
        assertEquals(ScanHistoryUiState.Loaded(listOf(completed)), viewModel.uiState.value)
    }

    @Test
    fun retriesAfterRepositoryFailure() = runTest {
        val repository = FakeRepository().apply {
            failure = IOException("Fallo de lectura")
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(ScanHistoryUiState.Error, viewModel.uiState.value)

        repository.failure = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.observations)
        assertEquals(ScanHistoryUiState.Loaded(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun retriesOwnerResolutionWithoutUsingAnotherProfile() = runTest {
        val repository = FakeRepository()
        var failOwner = true
        val viewModel = viewModel(repository) {
            if (failOwner) throw IOException("Perfil no disponible")
            "local:actual"
        }
        advanceUntilIdle()

        assertEquals(ScanHistoryUiState.Error, viewModel.uiState.value)
        assertEquals(0, repository.observations)

        failOwner = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals("local:actual", repository.observedOwner)
        assertEquals(ScanHistoryUiState.Loaded(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun clearingViewModelCancelsLoadingWithoutPublishingError() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository) { awaitCancellation() }
        runCurrent()

        store.clear()
        advanceUntilIdle()

        assertEquals(0, repository.observations)
        assertEquals(ScanHistoryUiState.Loading, viewModel.uiState.value)
    }
}
