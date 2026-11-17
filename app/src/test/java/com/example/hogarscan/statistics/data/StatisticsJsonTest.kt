package com.example.hogarscan.statistics.data

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.statistics.domain.StatisticsFailure
import com.example.hogarscan.statistics.domain.StatisticsOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StatisticsJsonTest {
    private val counts = """{"TCP_SERVICE_ACCESSIBLE":200000,"PROTOCOL_IDENTIFIED":0,"PREVENTIVE_REVIEW":0,"POSSIBLE_CVE_MATCH":1,"INSUFFICIENT_EVIDENCE":0}"""
    private val json = """{"summary_generation":2,"generated_at":"2026-11-11T05:32:33Z","total_scans":3000000000,"total_addresses_scanned":10,"total_addresses_responded":3,"total_accepted_services":2,"findings_by_category":$counts,"recent_scans":[]} """
    private val session = SessionSnapshot("ctx", "user", "a@example.com", Long.MAX_VALUE, 2)

    @Test fun `conserva agregados de 64 bits sin limite por revision`() {
        val data = StatisticsJson.decode(json)
        assertEquals(3000000000L, data.totalScans)
        assertEquals(200000L, data.findings.tcpServiceAccessible)
        assertEquals(2, data.summaryGeneration)
    }

    @Test fun `rechaza campos duplicados numeros invalidos y datos incompletos`() {
        listOf(
            json.replace("\"total_scans\":3000000000", "\"total_scans\":-1"),
            json.replace("\"total_scans\":3000000000", "\"total_scans\":1.5"),
            json.replace("\"total_scans\":3000000000", "\"total_scans\":9223372036854775808"),
            json.replace("\"summary_generation\":2", "\"summary_generation\":2,\"summary_generation\":2"),
            json.replace(",\"recent_scans\":[]", "")
        ).forEach { invalid ->
            assertThrows(RuntimeException::class.java) { StatisticsJson.decode(invalid) }
        }
    }

    @Test fun `valida limites de revision reciente sin limitar agregados`() {
        val perScan = counts.replace("200000", "100000")
        val scan = """{"scan_id":"6ce5e6b1-9509-48a7-bc9d-f0f872364726","started_at":"2026-11-11T05:30:00Z","accepted_services":2,"findings_by_category":$perScan}"""
        val response = json.replace("\"recent_scans\":[]", "\"recent_scans\":[$scan]")
        assertEquals(100000L, StatisticsJson.decode(response).recentScans.single().findings.tcpServiceAccessible)
        assertThrows(RuntimeException::class.java) { StatisticsJson.decode(response.replace("100000", "100001")) }
        assertThrows(RuntimeException::class.java) {
            StatisticsJson.decode(json.replace("\"recent_scans\":[]", "\"recent_scans\":[${List(11) { scan }.joinToString()}]"))
        }
    }

    @Test fun `solicita estadisticas de la sesion sin exponer credenciales`() = runTest {
        val gateway = HttpStatisticsGateway(SessionApiClient { request, expected ->
            assertEquals(ApiMethod.GET, request.method)
            assertEquals("/me/stats", request.path)
            assertNull(request.bearerToken)
            assertEquals(session, expected)
            ApiResult.Success(200, json)
        })
        assertTrue(gateway.getStatistics(session) is StatisticsOutcome.Success)
    }

    @Test fun `error de cuenta y respuesta malformada producen errores diferenciados`() = runTest {
        val changed = HttpStatisticsGateway(SessionApiClient { _, _ -> ApiResult.SessionChanged })
        assertEquals(StatisticsFailure.SESSION, (changed.getStatistics(session) as StatisticsOutcome.Failure).reason)
        val malformed = HttpStatisticsGateway(SessionApiClient { _, _ -> ApiResult.Success(200, "{}") })
        assertEquals(StatisticsFailure.INVALID_RESPONSE, (malformed.getStatistics(session) as StatisticsOutcome.Failure).reason)
    }
}
