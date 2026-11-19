package com.example.hogarscan.evaluations.domain.model

import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.evaluations.data.EvaluationRequestJson
import org.junit.Assert.*
import org.junit.Test

class ObservedIdentificationTest {
    private val ip = Ipv4Address.parse("192.168.1.2")
    private val http = ProbeResult(ip, 80, ProbeOutcome.ACCEPTED, 1, ServiceEvidence(ServiceProtocol.HTTP, "apache", "http_server", "2.4.49"))

    @Test fun `una identidad inequívoca habilita consulta automatica ligada al puerto`() {
        val input = EvaluationInput.fromResults("scan", "user:a", ip, listOf(http))
        assertEquals(IdentificationSource.SERVICE_RESPONSE, input.identification?.source)
        assertEquals(80, input.identification?.servicePort)
        val json = EvaluationRequestJson.encode(input)
        assertTrue(json.contains("\"protocol\":\"HTTP\""))
        assertTrue(json.contains("\"service_port\":80"))
        assertFalse(json.contains("192.168"))
        assertFalse(json.contains("Server:"))
    }

    @Test fun `varios productos no se resuelven escogiendo el primero`() {
        val ssh = ProbeResult(ip, 22, ProbeOutcome.ACCEPTED, 1, ServiceEvidence(ServiceProtocol.SSH, "openbsd", "openssh", "9.6p1"))
        val probes = listOf(http, ssh)
        assertNull(EvaluationInput.fromResults("scan", "user:a", ip, probes).identification)
        val selected = observedIdentifications(probes).last()
        assertEquals(selected, EvaluationInput.fromResults("scan", "user:a", ip, probes, selected).identification)
    }

    @Test fun `no permite atribuir a un puerto una identidad distinta de su respuesta`() {
        assertThrows(IllegalArgumentException::class.java) {
            EvaluationInput.fromResults("scan", "user:a", ip, listOf(http),
                DeclaredIdentification("nginx", "nginx", "1.2.3", IdentificationSource.SERVICE_RESPONSE, 80))
        }
    }
}
