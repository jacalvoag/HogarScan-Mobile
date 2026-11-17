package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.data.EvaluationWorkflow
import com.example.hogarscan.evaluations.data.local.EvaluationSaveResult
import com.example.hogarscan.evaluations.data.local.EvaluationStore
import com.example.hogarscan.evaluations.domain.*
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.data.sync.ScanSummarySyncCoordinator
import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanEvaluationViewModelTest {
    private val lifecycle = ViewModelStore()
    private val session = SessionSnapshot("ctx", "user", "a@example.com", Long.MAX_VALUE, 1)
    private val sessions = object : SessionReader { override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session)) }
    private val address = Ipv4Address.parse("192.168.1.2")
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80))
    private val scan = ScanRecord("scan", "user:user", config, 1, ScanRecordStatus.COMPLETED,
        listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 1)), 2, 1)
    private val response = EvaluationResult("1", "2026-11-11T00:00:00Z",
        EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())
    private val history = object : ScanHistoryRepository {
        override suspend fun findById(scanId: String, ownerId: String) = scan.takeIf { it.id == scanId && it.ownerId == ownerId }
        override suspend fun insert(record: ScanRecord) = Unit
        override fun observeByOwner(ownerId: String): Flow<List<ScanRecordSummary>> = emptyFlow()
        override suspend fun appendResult(scanId: String, ownerId: String, result: ProbeResult) = Unit
        override suspend fun finish(scanId: String, ownerId: String, status: ScanRecordStatus, finishedAtMillis: Long) = Unit
    }
    private val summaries = object : ScanSummaryRepository {
        override suspend fun enqueue(summary: FrozenScanSummary) = false
        override suspend fun findPending(userId: String, generation: Int) = emptyList<FrozenScanSummary>()
        override suspend fun markSynced(summary: FrozenScanSummary, receivedAtMillis: Long) = false
        override suspend fun markConflict(summary: FrozenScanSummary) = false
        override suspend fun markOlderPendingObsolete(userId: String, currentGeneration: Int) = 0
    }
    private val storage = object : EvaluationStore {
        var saved = emptyList<TargetEvaluation>()
        override suspend fun saveCompleted(scanId: String, evaluations: List<TargetEvaluation>, expected: SessionSnapshot): EvaluationSaveResult {
            saved = evaluations
            return EvaluationSaveResult.SAVED
        }
        override suspend fun load(scanId: String, expected: SessionSnapshot) = saved
    }
    private val sync = ScanSummarySyncCoordinator(summaries, sessions, { _, _ -> error("no pending summaries") }, { _, _ -> true }, { 1 })
    private fun vm(gateway: EvaluationGateway): ScanEvaluationViewModel {
        val evaluator = EvaluateCompletedScan(sessions, gateway, { 1 })
        val workflow = EvaluationWorkflow(sessions, history, evaluator, storage::saveCompleted, { sync.syncPending(it) }, { 1 })
        return ViewModelProvider(lifecycle, viewModelFactory { initializer {
            ScanEvaluationViewModel(scan.id, scan.ownerId, sessions, storage, workflow, sync) { 1 }
        } })[ScanEvaluationViewModel::class.java]
    }
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { lifecycle.clear(); Dispatchers.resetMain() }

    @Test fun `guarda y muestra evaluaciones del propietario`() = runTest {
        val viewModel = vm(EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        assertEquals(ScanEvaluationUiState.Ready, viewModel.uiState.value)
        viewModel.start(); advanceUntilIdle()
        val state = viewModel.uiState.value as ScanEvaluationUiState.Saved
        assertEquals(response, state.evaluations.single().result)
        assertEquals(1, storage.saved.size)
    }

    @Test fun `cambio de cuenta no publica ni guarda respuesta tardia`() = runTest {
        val release = CompletableDeferred<Unit>()
        val viewModel = vm(EvaluationGateway { _, _ -> withContext(NonCancellable) { release.await() }; EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        viewModel.start(); runCurrent()
        sessions.state.value = SessionState.Authenticated(session.copy(contextId = "new", userId = "other"))
        runCurrent(); release.complete(Unit); advanceUntilIdle()
        assertTrue(storage.saved.isEmpty())
        assertTrue(viewModel.uiState.value is ScanEvaluationUiState.Error)
    }
}
