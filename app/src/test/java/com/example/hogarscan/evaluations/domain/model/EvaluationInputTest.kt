package com.example.hogarscan.evaluations.domain.model

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EvaluationInputTest {
    private val address = Ipv4Address.parse("192.168.1.2")
    private fun probe(port: Int, ip: Ipv4Address = address) =
        ProbeResult(ip, port, ProbeOutcome.ACCEPTED, 10)

    @Test fun `conserva contexto local y observaciones del objetivo`() {
        val input = EvaluationInput.fromResults("scan", "owner", address, listOf(probe(80), probe(443)))
        assertEquals(address, input.address)
        assertEquals("owner", input.ownerId)
        assertEquals(listOf(80, 443), input.probes.map { it.port })
    }

    @Test fun `rechaza mezcla de objetivos y puertos duplicados o no permitidos`() {
        val invalid = listOf(
            emptyList(),
            listOf(probe(80), probe(443, Ipv4Address.parse("192.168.1.3"))),
            listOf(probe(80), probe(80)),
            listOf(probe(8080))
        )
        invalid.forEach { results ->
            assertThrows(IllegalArgumentException::class.java) {
                EvaluationInput.fromResults("scan", "owner", address, results)
            }
        }
    }

    @Test fun `valida identificacion sin alterar los datos declarados`() {
        val value = DeclaredIdentification(" Fabricante ", "Producto", "1.0", IdentificationSource.USER_DEVICE_LABEL)
        assertEquals(" Fabricante ", value.vendor)
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredIdentification(" ", "Producto", "1.0", IdentificationSource.USER_ADMIN_PANEL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredIdentification("a".repeat(81), "Producto", "1.0", IdentificationSource.USER_DEVICE_LABEL)
        }
    }
}
