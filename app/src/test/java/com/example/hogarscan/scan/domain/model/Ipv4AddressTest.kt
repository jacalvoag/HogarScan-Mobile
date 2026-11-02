package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4AddressTest {

    @Test
    fun `conserva la representacion de una direccion valida`() {
        val address = Ipv4Address.parse("192.168.1.10")

        assertEquals("192.168.1.10", address.toString())
    }

    @Test
    fun `representa los extremos del espacio IPv4`() {
        val minimum = Ipv4Address.parse("0.0.0.0")
        val maximum = Ipv4Address.parse("255.255.255.255")

        assertEquals(0L, minimum.value)
        assertEquals(4_294_967_295L, maximum.value)
        assertEquals("255.255.255.255", maximum.toString())
    }

    @Test
    fun `direcciones con el mismo valor son iguales`() {
        val first = Ipv4Address.parse("192.168.1.10")
        val second = Ipv4Address.parse("192.168.1.10")

        assertEquals(first, second)
    }

    @Test
    fun `compara direcciones por su valor numerico`() {
        val first = Ipv4Address.parse("192.168.1.2")
        val second = Ipv4Address.parse("192.168.1.10")

        assertTrue(first < second)
    }

    @Test
    fun `rechaza formatos invalidos`() {
        val invalidAddresses = listOf(
            "",
            "192.168.1",
            "192.168.1.1.5",
            "192..1.1",
            "192.168.a.1",
            "192.168.1.1 ",
            "192.168.1.1\n",
            "::1"
        )

        invalidAddresses.forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                Ipv4Address.parse(input)
            }
        }
    }

    @Test
    fun `rechaza numeros fuera del rango permitido`() {
        listOf(
            "256.168.1.1",
            "192.168.1.999",
            "192.168.-1.1"
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                Ipv4Address.parse(input)
            }
        }
    }

    @Test
    fun `rechaza ceros iniciales para evitar formatos ambiguos`() {
        assertThrows(IllegalArgumentException::class.java) {
            Ipv4Address.parse("192.168.001.10")
        }
    }
}