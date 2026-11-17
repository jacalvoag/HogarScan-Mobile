package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.SourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EvaluationResponseJsonTest {
    private val basic = """{"ruleset_version":"1.0","evaluated_at":"2026-11-11T05:32:33Z","sources":{"nvd":"NOT_QUERIED","kev":"NOT_QUERIED","checked_at":null},"findings":[],"possible_cve_matches":[]} """

    @Test fun `lee fuentes no consultadas y listas vacias`() {
        val result = EvaluationResponseJson.decode(basic)
        assertEquals(SourceState.NOT_QUERIED, result.sources.nvd)
        assertNull(result.sources.checkedAt)
        assertEquals(emptyList<Any>(), result.findings)
    }

    @Test fun `rechaza valores duplicados ausentes o de tipo incorrecto`() {
        listOf(
            basic.replace("\"ruleset_version\":\"1.0\"", "\"ruleset_version\":\"1.0\",\"ruleset_version\":\"2\""),
            basic.replace("\"checked_at\":null", "\"checked_at\":null,\"checked_at\":null"),
            basic.replace("\"ruleset_version\":\"1.0\"", "\"ruleset_version\":123"),
            basic.replace(",\"possible_cve_matches\":[]", ""),
            basic.replace("\"nvd\":\"NOT_QUERIED\"", "\"nvd\":\"UNAVAILABLE\"")
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { EvaluationResponseJson.decode(invalid) }
        }
    }

    @Test fun `conserva kev desconocido en coincidencia posible`() {
        val json = """{"ruleset_version":"1","evaluated_at":"2026-11-11T05:32:33Z","sources":{"nvd":"AVAILABLE","kev":"UNAVAILABLE","checked_at":"2026-11-11T05:32:33Z"},"findings":[{"category":"POSSIBLE_CVE_MATCH","title":"Posible coincidencia","priority":"LOW","confidence":"POSSIBLE","evidence":"Declarada","explanation":"Posible","action":"Revisar","limitation":"No confirma","rule_id":"CVE","cve":"CVE-2026-1234"}],"possible_cve_matches":[{"cve":"CVE-2026-1234","vendor":"A","product":"B","version":"1","identification_source":"USER_DEVICE_LABEL","affected_condition":"Versión","nvd_url":"https://nvd.nist.gov/vuln/detail/CVE-2026-1234","nvd_checked_at":"2026-11-11T05:32:33Z","in_kev":null}]}"""
        val result = EvaluationResponseJson.decode(json)
        assertNull(result.possibleCveMatches.single().inKev)
        assertThrows(IllegalArgumentException::class.java) {
            EvaluationResponseJson.decode(json.replace("\"kev\":\"UNAVAILABLE\"", "\"kev\":\"AVAILABLE\""))
        }
    }
}
