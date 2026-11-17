package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationResultJsonTest {
    @Test fun `conserva datos y estado kev desconocido al guardar y recuperar`() {
        val cve = "CVE-2026-1234"
        val match = PossibleCveMatch(cve, "Marca", "Producto", "1", IdentificationSource.USER_DEVICE_LABEL,
            "condición", "https://nvd.nist.gov/vuln/detail/$cve", "2026-11-11T00:00:00Z", null)
        val finding = Finding(FindingCategory.POSSIBLE_CVE_MATCH, "Título", FindingPriority.MEDIUM,
            FindingConfidence.POSSIBLE, "evidencia", "explicación", "acción", "límite", "regla", cve)
        val original = EvaluationResult("1", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.AVAILABLE, SourceState.UNAVAILABLE, "2026-11-11T00:00:00Z"),
            listOf(finding), listOf(match))
        val recovered = EvaluationResultJson.decode(EvaluationResultJson.encode(original))
        assertEquals(original, recovered)
        assertNull(recovered.possibleCveMatches.single().inKev)
    }

    @Test fun `conserva campos nulos de fuentes no consultadas`() {
        val original = EvaluationResult("1", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())
        assertEquals(original, EvaluationResultJson.decode(EvaluationResultJson.encode(original)))
    }
}
