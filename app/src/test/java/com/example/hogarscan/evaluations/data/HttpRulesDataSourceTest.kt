package com.example.hogarscan.evaluations.data

import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HttpRulesDataSourceTest {
    private val json = """{"ruleset_version":"1.0","published_at":"2026-11-11T05:32:33Z","categories":["TCP_SERVICE_ACCESSIBLE","PROTOCOL_IDENTIFIED","PREVENTIVE_REVIEW","POSSIBLE_CVE_MATCH","INSUFFICIENT_EVIDENCE"],"max_probes_per_evaluation":6,"caveat":"No confirma vulnerabilidad","max_addresses_per_scan":254,"allowed_ports":[22,23,80,443,445,3389]}"""

    @Test fun `consulta ruta publica y conserva version de reglas`() = runTest {
        val source = HttpRulesDataSource(ApiTransport { request ->
            assertEquals(ApiMethod.GET, request.method)
            assertEquals("/rules", request.path)
            assertNull(request.bearerToken)
            ApiResult.Success(200, json)
        })
        val rules = (source.getRules() as RulesRemoteResult.Success).rules
        assertEquals("1.0", rules.rulesetVersion)
        assertEquals(setOf(22, 23, 80, 443, 445, 3389), rules.allowedPorts)
    }

    @Test fun `rechaza duplicados tipos o limites invalidos`() = runTest {
        val invalid = listOf(
            json.replace("\"max_addresses_per_scan\":254", "\"max_addresses_per_scan\":\"254\""),
            json.replace("\"max_probes_per_evaluation\":6", "\"max_probes_per_evaluation\":6.1"),
            json.replace("22,23,80,443,445,3389", "22,22,80,443,445,3389"),
            json.replace("\"ruleset_version\":\"1.0\"", "\"ruleset_version\":\"1.0\",\"ruleset_version\":\"2.0\"")
        )
        invalid.forEach { body ->
            val source = HttpRulesDataSource(ApiTransport { ApiResult.Success(200, body) })
            assertSame(ApiResult.InvalidResponse, (source.getRules() as RulesRemoteResult.Failure).error)
        }
    }

    @Test fun `propaga errores del transporte y rechaza estado inesperado`() = runTest {
        val unavailable = HttpRulesDataSource(ApiTransport { ApiResult.NetworkFailure })
        assertSame(ApiResult.NetworkFailure, (unavailable.getRules() as RulesRemoteResult.Failure).error)
        val invalid = HttpRulesDataSource(ApiTransport { ApiResult.Success(201, json) })
        assertSame(ApiResult.InvalidResponse, (invalid.getRules() as RulesRemoteResult.Failure).error)
    }
}
