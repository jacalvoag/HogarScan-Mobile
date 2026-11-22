package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationRequestTest {
    @Test fun `traduce los cuatro resultados sin incluir contexto local`() {
        val address = Ipv4Address.parse("192.168.1.2")
        val outcomes = ProbeOutcome.entries
        val ports = listOf(22, 23, 80, 443)
        val input = EvaluationInput.fromResults("scan", "owner", address,
            outcomes.mapIndexed { index, outcome -> ProbeResult(address, ports[index], outcome, 25) })
        val request = input.toEvaluationRequest()
        assertEquals(listOf(RemoteOutcome.ACCEPTED, RemoteOutcome.REFUSED, RemoteOutcome.TIMEOUT, RemoteOutcome.ERROR), request.probes.map { it.outcome })
        assertEquals(ports, request.probes.map { it.port })
    }
}
