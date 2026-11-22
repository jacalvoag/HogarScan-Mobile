package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EvaluateCompletedScanTest {
    private val user = "2c48661b-b872-4ea4-ad9e-3df603a5c0f9"
    private val session = SessionSnapshot("context", user, "a@example.com", Long.MAX_VALUE, 3)
    private val sessions = object : SessionReader {
        override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
    }
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24),
        Ipv4Address.parse("192.168.1.2"), Ipv4Address.parse("192.168.1.3"), setOf(80))
    private val scan = ScanRecord("6ce5e6b1-9509-48a7-bc9d-f0f872364726", "user:$user", config, 1,
        ScanRecordStatus.COMPLETED, config.targetAddresses.map { ProbeResult(it, 80, ProbeOutcome.ACCEPTED, 1) }, 2, 3)
    private val result = EvaluationResult("1", "2026-11-11T00:00:00Z",
        EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())

    @Test fun `evalua cada objetivo por separado y permite preparar resumen`() = runTest {
        val seen = mutableListOf<Ipv4Address>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { input, _ ->
            seen.add(input.address)
            assertEquals(1, input.probes.size)
            EvaluationOutcome.Success(result)
        }, { 1 })
        val outcome = evaluator.run(scan, session, onProgress = { done, total -> progress.add(done to total) })
        assertEquals(config.targetAddresses, seen)
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), progress)
        val completed = outcome as ScanEvaluationOutcome.Completed
        assertEquals(2, prepareEvaluatedScanSummary(scan, completed.evaluations, session).acceptedServices)
    }

    @Test fun `interrumpe ante fallo sin declarar revision evaluada`() = runTest {
        var calls = 0
        val progress = mutableListOf<Pair<Int, Int>>()
        val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { _, _ ->
            calls++
            if (calls == 2) EvaluationOutcome.Failure(EvaluationFailure.TIMEOUT) else EvaluationOutcome.Success(result)
        }, { 1 })
        val outcome = evaluator.run(scan, session, onProgress = { done, total -> progress.add(done to total) }) as ScanEvaluationOutcome.Failed
        assertEquals(config.targetAddresses[1], outcome.address)
        assertEquals(EvaluationFailure.TIMEOUT, outcome.reason)
        assertEquals(listOf(0 to 2, 1 to 2), progress)
    }

    @Test fun `sesion anterior no publica progreso ni inicia solicitudes`() = runTest {
        sessions.state.value = SessionState.Guest
        val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { _, _ -> error("No request expected") }, { 1 })
        val progress = mutableListOf<Pair<Int, Int>>()
        val outcome = evaluator.run(scan, session, onProgress = { done, total -> progress.add(done to total) })
        assertEquals(ScanEvaluationOutcome.SessionChanged, outcome)
        assertTrue(progress.isEmpty())
    }

    @Test fun `cambio de generacion mientras espera descarta la respuesta`() = runTest {
        val release = CompletableDeferred<Unit>()
        val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { _, _ ->
            release.await()
            EvaluationOutcome.Success(result)
        }, { 1 })
        val pending = async { evaluator.run(scan, session) }
        runCurrent()
        sessions.state.value = SessionState.Authenticated(session.copy(summaryGeneration = 4))
        release.complete(Unit)
        assertEquals(ScanEvaluationOutcome.SessionChanged, pending.await())
    }

    @Test fun `cancelacion detiene la operacion antes del siguiente objetivo`() = runTest {
        var calls = 0
        val evaluator = EvaluateCompletedScan(sessions, EvaluationGateway { _, _ ->
            calls++
            awaitCancellation()
        }, { 1 })
        val pending = async { evaluator.run(scan, session) }
        runCurrent()
        pending.cancelAndJoin()
        assertEquals(1, calls)
        assertTrue(pending.isCancelled)
    }
}
