package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAuthorizationTest {

    private fun wifi(
        address: String = "192.168.28.43",
        prefix: Int = 24,
        id: String = "wifi-1"
    ) = WifiNetworkResult.Available(
        scope = NetworkScope(Ipv4Address.parse(address), prefix),
        connectionId = id
    )

    private fun config(
        network: WifiNetworkResult.Available = wifi(),
        start: String = "192.168.28.1",
        end: String = "192.168.28.20",
        ports: Set<Int> = setOf(80, 443)
    ) = ScanConfig(
        networkScope = network.scope,
        startAddress = Ipv4Address.parse(start),
        endAddress = Ipv4Address.parse(end),
        ports = ports
    )

    @Test
    fun `acepta una configuracion equivalente en la misma conexion`() {
        val network = wifi()
        val authorization = ScanAuthorization.confirm(config(), network)

        assertTrue(
            authorization.validate(
                config(ports = setOf(443, 80)),
                network
            )
        )
    }

    @Test
    fun `otra conexion requiere autorizar aunque conserve la subred`() {
        val original = wifi()
        val authorization = ScanAuthorization.confirm(config(), original)

        assertFalse(
            authorization.validate(config(), wifi(id = "wifi-2"))
        )
        assertFalse(authorization.validate(config(), original))
    }

    @Test
    fun `cambiar cualquiera de los extremos invalida la autorizacion`() {
        val network = wifi()
        val changedConfigs = listOf(
            config(start = "192.168.28.2"),
            config(end = "192.168.28.21")
        )

        changedConfigs.forEach { changed ->
            val authorization = ScanAuthorization.confirm(config(), network)

            assertFalse(authorization.validate(changed, network))
            assertFalse(authorization.validate(config(), network))
        }
    }

    @Test
    fun `cambiar los puertos invalida aunque luego se restauren`() {
        val network = wifi()
        val authorization = ScanAuthorization.confirm(config(), network)

        assertFalse(
            authorization.validate(config(ports = setOf(80)), network)
        )
        assertFalse(authorization.validate(config(), network))
    }

    @Test
    fun `cambiar IPv4 o prefijo invalida aunque el identificador coincida`() {
        listOf(
            wifi(address = "192.168.28.44"),
            wifi(prefix = 23)
        ).forEach { changedNetwork ->
            val authorization = ScanAuthorization.confirm(config(), wifi())

            assertFalse(
                authorization.validate(
                    config(network = changedNetwork),
                    changedNetwork
                )
            )
        }
    }

    @Test
    fun `perder la red invalida y recuperarla no reactiva la autorizacion`() {
        val network = wifi()
        val authorization = ScanAuthorization.confirm(config(), network)
        val disconnected = WifiNetworkResult.Unavailable(
            WifiNetworkResult.Reason.NO_NETWORK
        )

        assertFalse(authorization.validate(config(), disconnected))
        assertFalse(authorization.validate(config(), network))

        val newAuthorization = ScanAuthorization.confirm(config(), network)

        assertTrue(newAuthorization.validate(config(), network))
    }

    @Test
    fun `permite revocar explicitamente la autorizacion`() {
        val network = wifi()
        val authorization = ScanAuthorization.confirm(config(), network)

        authorization.revoke()

        assertFalse(authorization.validate(config(), network))
    }

    @Test
    fun `rechaza autorizar una configuracion de otra red`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanAuthorization.confirm(
                config(),
                wifi(address = "192.168.29.43")
            )
        }
    }

    @Test
    fun `rechaza autorizar sin identificador de conexion`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanAuthorization.confirm(config(), wifi(id = " "))
        }
    }
}