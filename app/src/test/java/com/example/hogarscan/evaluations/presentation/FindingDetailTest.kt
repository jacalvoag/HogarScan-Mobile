package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class FindingDetailTest {
    private val finding = Finding(FindingCategory.TCP_SERVICE_ACCESSIBLE, "Conexión aceptada",
        FindingPriority.LOW, FindingConfidence.OBSERVED, "Puerto accesible", "Explicación",
        "Revisar", "No confirma una vulnerabilidad", "TCP_ACCEPTED", null)

    @Test fun `muestra evidencia y estados sin inventar certeza ni coincidencias`() {
        val evaluation = EvaluationResult("1.0", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.UNAVAILABLE, SourceState.NOT_QUERIED, "2026-11-11T00:00:00Z"),
            listOf(finding), emptyList())
        val detail = requireNotNull(evaluation.findingDetail(0))
        assertEquals("Observada", detail.confidence)
        assertEquals("Puerto accesible", detail.evidence)
        assertEquals("No disponible", detail.nvdStatus)
        assertEquals("No consultada", detail.kevStatus)
        assertNull(detail.possibleMatch)
        assertNull(evaluation.findingDetail(1))
        assertNull(evaluation.findingDetail(-1))
    }

    @Test fun `asocia solo la coincidencia del hallazgo y conserva kev desconocido`() {
        val cve = "CVE-2026-1234"
        val possible = finding.copy(category = FindingCategory.POSSIBLE_CVE_MATCH,
            confidence = FindingConfidence.POSSIBLE, cve = cve)
        val match = PossibleCveMatch(cve, "Marca", "Producto", "1.0", IdentificationSource.USER_DEVICE_LABEL,
            "Condición", "https://nvd.nist.gov/vuln/detail/$cve", "2026-11-11T00:00:00Z", null)
        val evaluation = EvaluationResult("2.0", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.AVAILABLE, SourceState.UNAVAILABLE, "2026-11-11T00:00:00Z"),
            listOf(possible), listOf(match))
        val detail = requireNotNull(evaluation.findingDetail(0))
        assertEquals("Posible", detail.confidence)
        assertEquals("Presencia en KEV desconocida", detail.possibleMatch?.kevMembership)
        assertEquals(cve, detail.possibleMatch?.cve)
        assertEquals("Etiqueta del dispositivo", detail.possibleMatch?.declaredSource)
    }
}
