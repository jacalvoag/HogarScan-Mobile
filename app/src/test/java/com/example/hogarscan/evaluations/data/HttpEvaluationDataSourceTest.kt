package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HttpEvaluationDataSourceTest {
    private val session = SessionSnapshot("ctx", "account", "test@example.com", Long.MAX_VALUE, 0)
    private val address = Ipv4Address.parse("192.168.1.2")
    private val input = EvaluationInput.fromResults("scan", "user:account", address,
        listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 0)))
    private val basic = """{"ruleset_version":"1","evaluated_at":"2026-11-11T05:32:33Z","sources":{"nvd":"NOT_QUERIED","kev":"NOT_QUERIED","checked_at":null},"findings":[],"possible_cve_matches":[]} """

    @Test fun `envia solicitud autenticada sin exponer contexto local`() = runTest {
        val client = SessionApiClient { request, expected ->
            assertEquals(ApiMethod.POST, request.method)
            assertEquals("/evaluations", request.path)
            assertSame(session, expected)
            assertNull(request.bearerToken)
            assertFalse(request.bodyJson!!.contains("192.168"))
            assertFalse(request.bodyJson!!.contains("account"))
            ApiResult.Success(200, basic)
        }
        assertTrue(HttpEvaluationDataSource(client).evaluate(input, session) is EvaluationRemoteResult.Success)
    }

    @Test fun `propaga cambio de sesion y rechaza respuesta invalida`() = runTest {
        val changed = HttpEvaluationDataSource(SessionApiClient { _, _ -> ApiResult.SessionChanged })
        assertSame(ApiResult.SessionChanged, (changed.evaluate(input, session) as EvaluationRemoteResult.Failure).error)
        val invalid = HttpEvaluationDataSource(SessionApiClient { _, _ -> ApiResult.Success(200, "{}") })
        assertSame(ApiResult.InvalidResponse, (invalid.evaluate(input, session) as EvaluationRemoteResult.Failure).error)
    }

    @Test fun `rechaza otro propietario antes de enviar`() = runTest {
        var called = false
        val client = SessionApiClient { _, _ -> called = true; ApiResult.NetworkFailure }
        val other = input.let { EvaluationInput.fromResults(it.scanId, "user:other", address,
            listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 0))) }
        try {
            HttpEvaluationDataSource(client).evaluate(other, session)
            fail("Debió rechazar al propietario")
        } catch (_: IllegalArgumentException) {
            assertFalse(called)
        }
    }
}
