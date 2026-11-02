package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanConfigTest {

    private val defaultScope =
        NetworkScope(Ipv4Address.parse("192.168.1.10"), 24)

    private fun config(
        start: String,
        end: String,
        ports: Set<Int> = setOf(80, 443),
        scope: NetworkScope = defaultScope
    ) = ScanConfig(
        networkScope = scope,
        startAddress = Ipv4Address.parse(start),
        endAddress = Ipv4Address.parse(end),
        ports = ports
    )

    @Test
    fun `excluye al telefono y calcula los intentos por puerto`() {
        val config = config(
            "192.168.1.1",
            "192.168.1.20",
            ports = setOf(22, 23, 80, 443, 445, 3389)
        )

        assertEquals(19, config.targetAddresses.size)
        assertEquals(114, config.plannedProbeCount)
        assertFalse(
            config.targetAddresses.contains(defaultScope.localAddress)
        )
    }

    @Test
    fun `conserva todas las direcciones cuando el telefono queda fuera del rango`() {
        val config = config("192.168.1.1", "192.168.1.5")

        assertEquals(
            listOf(
                "192.168.1.1",
                "192.168.1.2",
                "192.168.1.3",
                "192.168.1.4",
                "192.168.1.5"
            ),
            config.targetAddresses.map { it.toString() }
        )
        assertEquals(10, config.plannedProbeCount)
    }

    @Test
    fun `acepta un rango de exactamente 254 direcciones`() {
        val scope =
            NetworkScope(Ipv4Address.parse("192.168.2.10"), 16)

        val config = config(
            "192.168.1.1",
            "192.168.1.254",
            scope = scope
        )

        assertEquals(254, config.targetAddresses.size)
    }

    @Test
    fun `rechaza 255 direcciones aunque excluir al telefono dejaria 254`() {
        val scope =
            NetworkScope(Ipv4Address.parse("192.168.1.10"), 16)

        assertThrows(IllegalArgumentException::class.java) {
            config(
                "192.168.1.1",
                "192.168.1.255",
                scope = scope
            )
        }
    }

    @Test
    fun `rechaza un rango invertido`() {
        assertThrows(IllegalArgumentException::class.java) {
            config("192.168.1.20", "192.168.1.1")
        }
    }

    @Test
    fun `rechaza extremos fuera de la subred`() {
        listOf(
            "192.168.0.254" to "192.168.1.5",
            "192.168.1.250" to "192.168.2.1"
        ).forEach { (start, end) ->
            assertThrows(IllegalArgumentException::class.java) {
                config(start, end)
            }
        }
    }

    @Test
    fun `rechaza red y broadcast como extremos`() {
        listOf(
            "192.168.1.0" to "192.168.1.5",
            "192.168.1.250" to "192.168.1.255"
        ).forEach { (start, end) ->
            assertThrows(IllegalArgumentException::class.java) {
                config(start, end)
            }
        }
    }

    @Test
    fun `rechaza un rango que solo contiene al telefono`() {
        assertThrows(IllegalArgumentException::class.java) {
            config("192.168.1.10", "192.168.1.10")
        }
    }

    @Test
    fun `rechaza una seleccion de puertos vacia`() {
        assertThrows(IllegalArgumentException::class.java) {
            config(
                "192.168.1.1",
                "192.168.1.5",
                ports = emptySet()
            )
        }
    }

    @Test
    fun `rechaza puertos fuera de los permitidos`() {
        listOf(0, 21, 8080, 65536).forEach { port ->
            assertThrows(IllegalArgumentException::class.java) {
                config(
                    "192.168.1.1",
                    "192.168.1.5",
                    ports = setOf(port)
                )
            }
        }
    }

    @Test
    fun `conserva una copia ordenada de los puertos seleccionados`() {
        val ports = mutableSetOf(443, 80)

        val config = config(
            "192.168.1.1",
            "192.168.1.5",
            ports = ports
        )

        ports.clear()

        assertEquals(listOf(80, 443), config.ports)
        assertEquals(10, config.plannedProbeCount)
    }
}