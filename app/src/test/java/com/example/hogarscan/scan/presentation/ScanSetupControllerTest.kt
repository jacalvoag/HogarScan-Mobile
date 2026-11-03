package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSetupControllerTest {

    private fun wifi(
        address: String = "192.168.28.43",
        prefix: Int = 24,
        id: String = "wifi-1:1"
    ) = WifiNetworkResult.Available(
        scope = NetworkScope(Ipv4Address.parse(address), prefix),
        connectionId = id
    )

    private fun ready() = ScanSetupController().apply {
        onNetworkResult(wifi())
        updateStartAddress("192.168.28.40")
        updateEndAddress("192.168.28.45")
        togglePort(80)
        togglePort(443)
    }

    private fun authorized() = ready().apply {
        reviewConfiguration()
        confirmAuthorization()
        assertNotNull(state.authorizedConfig)
    }

    @Test
    fun `impide revisar sin red disponible`() {
        val controller = ScanSetupController()

        controller.reviewConfiguration()
        controller.confirmAuthorization()

        assertNotNull(controller.state.error)
        assertNull(controller.state.confirmation)
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `un error de rango se presenta sin abrir la confirmacion`() {
        val controller = ready()
        controller.updateStartAddress("192.168.29.1")

        controller.reviewConfiguration()

        assertNotNull(controller.state.error)
        assertNull(controller.state.confirmation)
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `una IPv4 mal escrita produce un error de formulario`() {
        val controller = ready()
        controller.updateStartAddress("192.168..40")

        controller.reviewConfiguration()

        assertNotNull(controller.state.error)
        assertNull(controller.state.confirmation)
    }

    @Test
    fun `exige seleccionar puertos`() {
        val controller = ready()
        controller.togglePort(80)
        controller.togglePort(443)

        controller.reviewConfiguration()

        assertNotNull(controller.state.error)
        assertNull(controller.state.confirmation)
    }

    @Test
    fun `la vista previa excluye al telefono y calcula los intentos`() {
        val controller = ready()

        controller.reviewConfiguration()

        val config = requireNotNull(controller.state.confirmation)
        assertEquals(5, config.targetAddresses.size)
        assertEquals(10, config.plannedProbeCount)
        assertEquals(listOf(80, 443), config.ports)
        assertFalse(config.targetAddresses.contains(wifi().scope.localAddress))
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `cancelar el dialogo no autoriza`() {
        val controller = ready()
        controller.reviewConfiguration()

        controller.dismissConfirmation()
        controller.confirmAuthorization()

        assertNull(controller.state.confirmation)
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `no autoriza sin una configuracion revisada`() {
        val controller = ready()

        controller.confirmAuthorization()

        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `la confirmacion explicita autoriza la configuracion revisada`() {
        val controller = ready()
        controller.reviewConfiguration()
        val reviewed = controller.state.confirmation

        controller.confirmAuthorization()

        assertEquals(reviewed, controller.state.authorizedConfig)
        assertNull(controller.state.confirmation)
        assertNull(controller.state.error)
    }

    @Test
    fun `verificar la misma conexion conserva la autorizacion`() {
        val controller = authorized()
        val config = controller.state.authorizedConfig

        controller.onNetworkChecking()

        assertTrue(controller.state.checkingNetwork)
        assertFalse(controller.state.networkAvailable)

        controller.onNetworkResult(wifi())

        assertFalse(controller.state.checkingNetwork)
        assertTrue(controller.state.networkAvailable)
        assertEquals(config, controller.state.authorizedConfig)
    }

    @Test
    fun `editar extremos o puertos revoca aunque luego se restauren`() {
        val edits: List<(ScanSetupController) -> Unit> = listOf(
            {
                it.updateStartAddress("192.168.28.41")
                it.updateStartAddress("192.168.28.40")
            },
            {
                it.updateEndAddress("192.168.28.46")
                it.updateEndAddress("192.168.28.45")
            },
            {
                it.togglePort(80)
                it.togglePort(80)
            }
        )

        edits.forEach { edit ->
            val controller = authorized()
            edit(controller)

            assertNull(controller.state.authorizedConfig)
            assertNull(controller.state.confirmation)
        }
    }

    @Test
    fun `perder y recuperar la conexion no restaura la autorizacion`() {
        val controller = authorized()

        controller.onNetworkResult(
            WifiNetworkResult.Unavailable(WifiNetworkResult.Reason.NO_NETWORK)
        )
        controller.onNetworkResult(wifi())

        assertTrue(controller.state.networkAvailable)
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `otra conexion o revision invalida aunque conserve la subred`() {
        listOf("wifi-2:1", "wifi-1:2").forEach { id ->
            val controller = authorized()

            controller.onNetworkResult(wifi(id = id))

            assertNull(controller.state.authorizedConfig)
        }
    }

    @Test
    fun `cambiar IPv4 o prefijo invalida la autorizacion`() {
        listOf(
            wifi(address = "192.168.28.44"),
            wifi(prefix = 23)
        ).forEach { network ->
            val controller = authorized()

            controller.onNetworkResult(network)

            assertNull(controller.state.authorizedConfig)
        }
    }

    @Test
    fun `cambiar la conexion mientras se confirma descarta el dialogo`() {
        val controller = ready()
        controller.reviewConfiguration()

        controller.onNetworkChecking()
        controller.onNetworkResult(wifi(id = "wifi-2:1"))
        controller.confirmAuthorization()

        assertNull(controller.state.confirmation)
        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `no permite confirmar mientras se comprueba la conexion`() {
        val controller = ready()
        controller.reviewConfiguration()
        controller.onNetworkChecking()

        controller.confirmAuthorization()

        assertNull(controller.state.authorizedConfig)
    }

    @Test
    fun `se puede revocar y autorizar nuevamente de forma explicita`() {
        val controller = authorized()

        controller.revokeAuthorization()
        assertNull(controller.state.authorizedConfig)

        controller.reviewConfiguration()
        controller.confirmAuthorization()
        assertNotNull(controller.state.authorizedConfig)
    }

    @Test
    fun `transfiere una autorizacion para iniciar una sola revision`() {
        val controller = authorized()

        val request = requireNotNull(controller.takeAuthorizedScan())

        assertTrue(request.validate(wifi()))
        assertNull(controller.state.authorizedConfig)
        assertNull(controller.takeAuthorizedScan())
        request.revoke()
        assertFalse(request.validate(wifi()))
    }

    @Test
    fun `espera a terminar la consulta de red antes de transferir la autorizacion`() {
        val controller = authorized()
        controller.onNetworkChecking()

        assertNull(controller.takeAuthorizedScan())
        assertNotNull(controller.state.authorizedConfig)

        controller.onNetworkResult(wifi())

        assertNotNull(controller.takeAuthorizedScan())
    }

    @Test
    fun `no puede iniciar despues de editar el alcance autorizado`() {
        val controller = authorized()
        controller.updateEndAddress("192.168.28.46")

        assertNull(controller.takeAuthorizedScan())
    }
}
