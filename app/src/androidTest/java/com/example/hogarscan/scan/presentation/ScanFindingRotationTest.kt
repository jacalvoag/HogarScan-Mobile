package com.example.hogarscan.scan.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.domain.*
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.evaluations.presentation.ScanEvaluationViewModel
import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.usecase.HistoryOwnerResolver
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanFindingRotationTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun cleanup() { store.clear() }

    @Test fun recreatingRoutePreservesSelectedFindingAndBackReturnsToReview() {
        val sessions = object : SessionReader {
            override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(
                SessionSnapshot("ctx", "account", "a@example.com", Long.MAX_VALUE, 1)
            ))
        }
        val address = Ipv4Address.parse("192.168.1.2")
        val record = ScanRecord("scan", "user:account",
            ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80)),
            1, ScanRecordStatus.COMPLETED,
            listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 1)), 2, 1)
        val title = "Conexión TCP aceptada en el puerto 80"
        val result = EvaluationResult("0.4.0", "2026-11-19T00:00:00Z",
            EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null),
            listOf(Finding(FindingCategory.TCP_SERVICE_ACCESSIBLE, title,
                FindingPriority.UNDETERMINED, FindingConfidence.OBSERVED, "Conexión aceptada",
                "Se aceptó la conexión", "Revisar el servicio", "No confirma vulnerabilidad",
                "TCP_ACCEPTED", null)), emptyList())
        val evaluation = TargetEvaluation(EvaluationInput.fromResults(record.id, record.ownerId,
            address, record.results), result)
        val repository = object : ScanHistoryRepository {
            override fun observeByOwner(ownerId: String) = MutableStateFlow(emptyList<ScanRecordSummary>())
            override suspend fun findById(scanId: String, ownerId: String) =
                record.takeIf { it.id == scanId && it.ownerId == ownerId }
            override suspend fun insert(record: ScanRecord) = error("Unexpected insert")
            override suspend fun appendResult(scanId: String, ownerId: String, result: ProbeResult) = error("Unexpected append")
            override suspend fun finish(scanId: String, ownerId: String, status: ScanRecordStatus,
                finishedAtMillis: Long) = error("Unexpected finish")
        }
        val actions = object : ScanEvaluationActions {
            override suspend fun load(scanId: String, expected: SessionSnapshot) = listOf(evaluation)
            override suspend fun evaluate(scanId: String, expected: SessionSnapshot,
                identifications: Map<Ipv4Address, DeclaredIdentification>,
                onProgress: (Int, Int) -> Unit): ScanEvaluationRunResult = error("Unexpected evaluation")
            override suspend fun sync(expected: SessionSnapshot) = error("Unexpected sync")
            override suspend fun summaryStatus(scanId: String, expected: SessionSnapshot) =
                ScanSummarySyncState(SummarySyncStatus.SYNCED, 1)
            override suspend fun rules(): EvaluationRules? = null
        }
        val history = ViewModelProvider(store, viewModelFactory { initializer {
            ScanHistoryViewModel(repository, HistoryOwnerResolver(sessions, guestOwnerId = { "local:test" }), sessions)
        } })[ScanHistoryViewModel::class.java]
        val evaluationFactory = viewModelFactory { initializer {
            ScanEvaluationViewModel(record.id, record.ownerId, sessions, actions)
        } }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme {
            ScanHistoryRoute(history, evaluationFactory = { _, _ -> evaluationFactory })
        } }
        compose.runOnIdle { history.openDetail(record.id) }
        compose.waitUntil { history.detailState.value is ScanDetailUiState.Loaded }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Abrir evaluación").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Abrir evaluación").performScrollTo().performClick()
        compose.onNodeWithTag("scan-evaluation-results").performScrollTo()
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText("Detalle de hallazgo").assertExists()

        // Simula la recreación de Compose manteniendo los ViewModels, como en una rotación.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Detalle de hallazgo").assertExists()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText("Resultados de revisión").assertDoesNotExist()

        Espresso.pressBack()
        compose.onNodeWithText("Evaluación de la revisión").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Detalle de hallazgo").assertDoesNotExist()
        compose.onNodeWithText("Evaluación de la revisión").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Resultados de revisión").assertExists()
    }
}
