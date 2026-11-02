package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScopeTest {

    private fun scope(address: String, prefix: Int) =
        NetworkScope(Ipv4Address.parse(address), prefix)

    @Test
    fun `calcula una subred 24`() {
        val network = scope("192.168.1.10", 24)

        assertEquals("192.168.1.0", network.networkAddress.toString())
        assertEquals("192.168.1.255", network.broadcastAddress.toString())
        assertEquals("192.168.1.1", network.firstHost.toString())
        assertEquals("192.168.1.254", network.lastHost.toString())
        assertEquals(254L, network.hostCount)
    }

    @Test
    fun `respeta un prefijo 16`() {
        val network = scope("172.20.7.5", 16)

        assertEquals("172.20.0.0", network.networkAddress.toString())
        assertEquals("172.20.255.255", network.broadcastAddress.toString())
        assertEquals(65_534L, network.hostCount)
    }

    @Test
    fun `admite hosts terminados en cero o 255 dentro de una subred 23`() {
        val network = scope("192.168.1.10", 23)

        assertEquals("192.168.0.0", network.networkAddress.toString())
        assertEquals("192.168.1.255", network.broadcastAddress.toString())
        assertEquals(510L, network.hostCount)

        assertTrue(
            network.containsHost(Ipv4Address.parse("192.168.0.255"))
        )
        assertTrue(
            network.containsHost(Ipv4Address.parse("192.168.1.0"))
        )
    }

    @Test
    fun `calcula una subred 30 con dos hosts`() {
        val network = scope("192.168.1.1", 30)

        assertEquals("192.168.1.1", network.firstHost.toString())
        assertEquals("192.168.1.2", network.lastHost.toString())
        assertEquals(2L, network.hostCount)
    }

    @Test
    fun `rechaza prefijos fuera del alcance admitido`() {
        listOf(-1, 0, 31, 32, 33).forEach { prefix ->
            assertThrows(IllegalArgumentException::class.java) {
                scope("192.168.1.10", prefix)
            }
        }
    }

    @Test
    fun `rechaza una IP local igual a red o broadcast`() {
        listOf("192.168.1.0", "192.168.1.255").forEach { address ->
            assertThrows(IllegalArgumentException::class.java) {
                scope(address, 24)
            }
        }
    }

    @Test
    fun `comprueba pertenencia sin incluir red broadcast ni otra subred`() {
        val network = scope("192.168.1.10", 24)

        assertTrue(
            network.containsHost(Ipv4Address.parse("192.168.1.10"))
        )
        assertFalse(
            network.containsHost(Ipv4Address.parse("192.168.1.0"))
        )
        assertFalse(
            network.containsHost(Ipv4Address.parse("192.168.1.255"))
        )
        assertFalse(
            network.containsHost(Ipv4Address.parse("192.168.2.1"))
        )
    }

    @Test
    fun `calcula valores altos sin perder el signo`() {
        val network = scope("192.168.1.10", 1)

        assertEquals("128.0.0.0", network.networkAddress.toString())
        assertEquals("255.255.255.255", network.broadcastAddress.toString())
        assertEquals(2_147_483_646L, network.hostCount)
    }
}