package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class EvaluationRequestJsonTest {
    private val address = Ipv4Address.parse("192.168.1.2")

    @Test fun `serializa observaciones sin informacion local`() {
        val input = EvaluationInput.fromResults("scan-local", "owner-local", address, listOf(
            ProbeResult(address, 80, ProbeOutcome.REJECTED, 23),
            ProbeResult(address, 443, ProbeOutcome.TIMED_OUT, 1000)
        ))
        val json = EvaluationRequestJson.encode(input)
        assertEquals("""{"probes":[{"port":80,"outcome":"REFUSED"},{"port":443,"outcome":"TIMEOUT"}]}""", json)
        listOf("192.168", "scan-local", "owner-local", "duration", "protocol").forEach {
            assertFalse(json.contains(it))
        }
    }

    @Test fun `serializa identificacion declarada y escapa texto`() {
        val identification = DeclaredIdentification("Marca \"A\"", "Modelo", "1.0", IdentificationSource.USER_ADMIN_PANEL)
        val input = EvaluationInput.fromResults("s", "o", address,
            listOf(ProbeResult(address, 22, ProbeOutcome.ACCEPTED, 0)), identification)
        assertEquals("""{"probes":[{"port":22,"outcome":"ACCEPTED"}],"identification":{"vendor":"Marca \"A\"","product":"Modelo","version":"1.0","source":"USER_ADMIN_PANEL"}}""", EvaluationRequestJson.encode(input))
    }

    @Test fun `rechaza unicode invalido`() {
        val identification = DeclaredIdentification("\uD800", "Producto", "1", IdentificationSource.USER_DEVICE_LABEL)
        val input = EvaluationInput.fromResults("s", "o", address,
            listOf(ProbeResult(address, 22, ProbeOutcome.ERROR, 0)), identification)
        assertThrows(IllegalArgumentException::class.java) { EvaluationRequestJson.encode(input) }
    }
}
