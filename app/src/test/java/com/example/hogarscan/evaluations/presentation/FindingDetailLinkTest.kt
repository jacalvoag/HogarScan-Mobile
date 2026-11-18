package com.example.hogarscan.evaluations.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingDetailLinkTest {
    @Test fun `solo abre referencia https de la misma CVE en NVD`() {
        val cve = "CVE-2026-1234"
        assertTrue(safeNvdUrl("https://nvd.nist.gov/vuln/detail/$cve", cve))
        listOf(
            "http://nvd.nist.gov/vuln/detail/$cve",
            "https://nvd.nist.gov.evil.test/vuln/detail/$cve",
            "https://nvd.nist.gov/vuln/detail/CVE-2026-9999",
            "https://nvd.nist.gov/vuln/detail/$cve?redirect=other"
        ).forEach { assertFalse(safeNvdUrl(it, cve)) }
    }
}
