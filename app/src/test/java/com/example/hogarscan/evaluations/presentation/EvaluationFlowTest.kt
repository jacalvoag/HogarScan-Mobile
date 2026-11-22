package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.data.DefaultEvaluationGateway
import com.example.hogarscan.evaluations.data.HttpEvaluationDataSource
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationFlowTest {
    private val store = ViewModelStore()
    private val session = SessionSnapshot("ctx", "user-a", "a@example.com", Long.MAX_VALUE, 0)
    private val sessions = object : SessionReader {
        override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
    }

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `traduce observaciones y muestra detalle devuelto por la API simulada`() = runTest {
        val ip = Ipv4Address.parse("192.168.1.2")
        val client = SessionApiClient { request, expected ->
            assertEquals(session, expected)
            assertEquals("/evaluations", request.path)
            assertEquals("""{"probes":[{"port":80,"outcome":"REFUSED"}]}""", request.bodyJson)
            assertFalse(request.bodyJson!!.contains(ip.toString()))
            ApiResult.Success(200, """{"ruleset_version":"1.0","evaluated_at":"2026-11-11T05:32:33Z","sources":{"nvd":"NOT_QUERIED","kev":"NOT_QUERIED","checked_at":null},"findings":[{"category":"INSUFFICIENT_EVIDENCE","title":"Conexión rechazada","priority":"UNDETERMINED","confidence":"INCONCLUSIVE","evidence":"Puerto 80 rechazado","explanation":"No se pudo conectar","action":"Revisar si corresponde","limitation":"No prueba seguridad","rule_id":"TCP_REFUSED","cve":null}],"possible_cve_matches":[]} """)
        }
        val viewModel = ViewModelProvider(store, viewModelFactory { initializer {
            EvaluationViewModel("scan", "user:user-a", ip,
                listOf(ProbeResult(ip, 80, com.example.hogarscan.scan.domain.model.ProbeOutcome.REJECTED, 10)),
                sessions, DefaultEvaluationGateway(HttpEvaluationDataSource(client)), { 1 })
        } })[EvaluationViewModel::class.java]
        viewModel.evaluate()
        advanceUntilIdle()
        val result = (viewModel.uiState.value.evaluation as EvaluationUiState.Success).result
        val detail = requireNotNull(result.findingDetail(0))
        assertEquals("Puerto 80 rechazado", detail.evidence)
        assertEquals("Inconclusa", detail.confidence)
        assertEquals("No consultada", detail.nvdStatus)
        assertNull(detail.possibleMatch)
    }
}
