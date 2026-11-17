package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.data.local.EvaluationSaveResult
import com.example.hogarscan.evaluations.domain.*
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.data.sync.SummarySyncRunResult
import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EvaluationWorkflowTest {
    private val user = "2c48661b-b872-4ea4-ad9e-3df603a5c0f9"
    private val session = SessionSnapshot("ctx", user, "a@example.com", 10000, 1)
    private val sessions = object : SessionReader { override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session)) }
    private val address = Ipv4Address.parse("192.168.1.2")
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80))
    private val scan = ScanRecord("6ce5e6b1-9509-48a7-bc9d-f0f872364726", "user:$user", config, 10,
        ScanRecordStatus.COMPLETED, listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 5)), 20, 1)
    private val history = object : ScanHistoryRepository {
        override suspend fun findById(scanId: String, ownerId: String): ScanRecord? = scan.takeIf { it.id == scanId && it.ownerId == ownerId }
        override suspend fun insert(record: ScanRecord) = Unit
        override fun observeByOwner(ownerId: String): Flow<List<ScanRecordSummary>> = emptyFlow()
        override suspend fun appendResult(scanId: String, ownerId: String, result: ProbeResult) = Unit
        override suspend fun finish(scanId: String, ownerId: String, status: ScanRecordStatus, finishedAtMillis: Long) = Unit
    }
    private val response = EvaluationResult("1", "2026-11-11T00:00:00Z",
        EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())
    private val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { _, _ -> EvaluationOutcome.Success(response) }, { 1 })

    @Test fun `guarda antes de sincronizar y conserva resultado si falla red`() = runTest {
        var saved = false
        val workflow = EvaluationWorkflow(sessions, history, evaluator,
            save = { _, evaluations, _ -> saved = true; assertEquals(1, evaluations.size); EvaluationSaveResult.SAVED },
            sync = { expected -> assertTrue(saved); assertEquals(session, expected); SummarySyncRunResult.Failure(ApiResult.NetworkFailure) },
            nowMillis = { 1 })
        val result = workflow.run(scan.id, session) as EvaluationWorkflowResult.Saved
        assertTrue(result.sync is SummarySyncRunResult.Failure)
        assertEquals(response, result.evaluations.single().result)
    }

    @Test fun `cambio de cuenta antes del guardado impide sincronizar`() = runTest {
        var synced = false
        val workflow = EvaluationWorkflow(sessions, history, evaluator,
            save = { _, _, _ -> sessions.state.value = SessionState.Guest; EvaluationSaveResult.SESSION_CHANGED },
            sync = { synced = true; SummarySyncRunResult.Completed }, nowMillis = { 1 })
        assertEquals(EvaluationWorkflowResult.SessionChanged, workflow.run(scan.id, session))
        assertFalse(synced)
    }

    @Test fun `cambio de cuenta tras guardar no sincroniza otra cola`() = runTest {
        var saved = false
        var synced = false
        val workflow = EvaluationWorkflow(sessions, history, evaluator,
            save = { _, _, _ -> saved = true; sessions.state.value = SessionState.Guest; EvaluationSaveResult.SAVED },
            sync = { synced = true; SummarySyncRunResult.Completed }, nowMillis = { 1 })
        assertEquals(EvaluationWorkflowResult.SessionChanged, workflow.run(scan.id, session))
        assertTrue(saved)
        assertFalse(synced)
    }
}
