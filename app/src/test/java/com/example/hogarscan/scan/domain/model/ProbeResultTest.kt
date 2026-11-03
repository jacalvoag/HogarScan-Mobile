package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeResultTest {

    private val address = Ipv4Address.parse("192.168.28.40")

    @Test
    fun `conserva los datos de una observacion TCP`() {
        val result = ProbeResult(
            address = address,
            port = 443,
            outcome = ProbeOutcome.REJECTED,
            durationMillis = 27L
        )

        assertEquals(address, result.address)
        assertEquals(443, result.port)
        assertEquals(ProbeOutcome.REJECTED, result.outcome)
        assertEquals(27L, result.durationMillis)
    }

    @Test
    fun `admite los limites TCP y una duracion de cero milisegundos`() {
        listOf(1, 65_535).forEach { port ->
            val result = ProbeResult(
                address = address,
                port = port,
                outcome = ProbeOutcome.ACCEPTED,
                durationMillis = 0L
            )

            assertEquals(port, result.port)
            assertEquals(0L, result.durationMillis)
        }
    }

    @Test
    fun `rechaza puertos fuera del intervalo TCP`() {
        listOf(-1, 0, 65_536).forEach { port ->
            assertThrows(IllegalArgumentException::class.java) {
                ProbeResult(
                    address = address,
                    port = port,
                    outcome = ProbeOutcome.ERROR,
                    durationMillis = 0L
                )
            }
        }
    }

    @Test
    fun `rechaza duraciones negativas`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeResult(
                address = address,
                port = 80,
                outcome = ProbeOutcome.TIMED_OUT,
                durationMillis = -1L
            )
        }
    }
}