package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.data.DefaultScanEvaluationActions
import com.example.hogarscan.evaluations.data.HttpRulesDataSource
import com.example.hogarscan.shared.data.http.ApiTransport
import com.example.hogarscan.shared.data.http.ApiResult
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
    private var summaryStatus = SummarySyncStatus.PENDING
    private var retryResult = EvaluationSyncResult.PROCESSED
    private var retryCalls = 0
    private var rulesCalls = 0
    private var rulesResponse: EvaluationRules? = null
    private var rulesWait: CompletableDeferred<Unit>? = null
    private fun vm(gateway: EvaluationGateway): ScanEvaluationViewModel {
        val evaluator = EvaluateCompletedScan(sessions, gateway, { 1 })
        val workflow = EvaluationWorkflow(sessions, history, evaluator, storage::saveCompleted, { sync.syncPending(it) }, { 1 })
        val adapter = DefaultScanEvaluationActions(storage, workflow, sync, { _, _ -> summaryStatus },
            HttpRulesDataSource(ApiTransport { ApiResult.NetworkFailure }))
        val actions = object : ScanEvaluationActions by adapter {
            override suspend fun rules(): EvaluationRules? {
                rulesCalls++
                rulesWait?.await()
                return rulesResponse
            }
            override suspend fun sync(expected: SessionSnapshot): EvaluationSyncResult {
                retryCalls++
                return retryResult
            }
        }
        return ViewModelProvider(lifecycle, viewModelFactory { initializer {
            ScanEvaluationViewModel(scan.id, scan.ownerId, sessions, actions) { 1 }
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

    @Test fun `formulario e identificaciones sobreviven al recuperar el mismo ViewModel`() = runTest {
        var sent: DeclaredIdentification? = null
        val gateway = EvaluationGateway { input, _ ->
            sent = input.identification
            EvaluationOutcome.Success(response)
        }
        val first = vm(gateway)
        advanceUntilIdle()
        first.editIdentification { it.copy(address = address.toString(), vendor = "Acme", product = "Router", version = "1") }
        val recreated = vm(gateway)
        assertSame(first, recreated)
        assertEquals("Router", recreated.identification.value.product)
        recreated.addIdentification(config.targetAddresses)
        assertEquals("Acme", recreated.identification.value.identifications[address]?.vendor)
        assertFalse(recreated.identification.value.unfinished)
        recreated.start(); advanceUntilIdle()
        assertEquals("Router", sent?.product)
    }

    @Test fun `cambiar de cuenta limpia el formulario y las identificaciones`() = runTest {
        val viewModel = vm(EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        viewModel.editIdentification { it.copy(address = address.toString(), vendor = "Acme", product = "Router", version = "1") }
        viewModel.addIdentification(config.targetAddresses)
        viewModel.editIdentification { it.copy(vendor = "Otro") }
        sessions.state.value = SessionState.Guest
        advanceUntilIdle()
        assertEquals(ScanIdentificationState(), viewModel.identification.value)
    }

    @Test fun `lote procesado no oculta un conflicto del resumen`() = runTest {
        val viewModel = vm(EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        viewModel.start(); advanceUntilIdle()
        summaryStatus = SummarySyncStatus.CONFLICT
        viewModel.retrySync(); advanceUntilIdle()
        val saved = viewModel.uiState.value as ScanEvaluationUiState.Saved
        assertTrue(saved.message.contains("conflicto"))
        assertFalse(saved.canRetrySync)
        viewModel.retrySync(); advanceUntilIdle()
        assertEquals(1, retryCalls)
    }

    @Test fun `generacion invalida exige renovar sesion sin reenviar el resumen`() = runTest {
        val viewModel = vm(EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        viewModel.start(); advanceUntilIdle()
        retryResult = EvaluationSyncResult.GENERATION_REFRESH_REQUIRED
        viewModel.retrySync(); advanceUntilIdle()
        val saved = viewModel.uiState.value as ScanEvaluationUiState.Saved
        assertTrue(saved.message.contains("Cierra sesión"))
        assertFalse(saved.canRetrySync)
        assertEquals(response, saved.evaluations.single().result)
        viewModel.retrySync(); advanceUntilIdle()
        assertEquals(1, retryCalls)
    }

    @Test fun `al cargar muestra sincronizado u obsoleto sin ofrecer reenvio`() = runTest {
        val viewModel = vm(EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) })
        advanceUntilIdle()
        viewModel.start(); advanceUntilIdle()
        summaryStatus = SummarySyncStatus.SYNCED
        viewModel.load(); advanceUntilIdle()
        assertTrue((viewModel.uiState.value as ScanEvaluationUiState.Saved).message.contains("sincronizado"))
        assertFalse((viewModel.uiState.value as ScanEvaluationUiState.Saved).canRetrySync)
        summaryStatus = SummarySyncStatus.OBSOLETE
        viewModel.load(); advanceUntilIdle()
        assertTrue((viewModel.uiState.value as ScanEvaluationUiState.Saved).message.contains("generación anterior"))
        assertFalse((viewModel.uiState.value as ScanEvaluationUiState.Saved).canRetrySync)
    }

    @Test fun `consulta de reglas conserva estado y no duplica la solicitud`() = runTest {
        val gateway = EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) }
        val viewModel = vm(gateway)
        advanceUntilIdle()
        rulesWait = CompletableDeferred()
        rulesResponse = EvaluationRules("1", "2026-11-11T00:00:00Z", emptySet(), 6, "Alcance TCP", 254, setOf(80))
        viewModel.loadRules(); runCurrent()
        assertTrue(viewModel.rules.value.loading)
        vm(gateway).loadRules(); runCurrent()
        assertEquals(1, rulesCalls)
        rulesWait!!.complete(Unit); advanceUntilIdle()
        assertEquals(rulesResponse, viewModel.rules.value.rules)
        assertFalse(viewModel.rules.value.loading)
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
