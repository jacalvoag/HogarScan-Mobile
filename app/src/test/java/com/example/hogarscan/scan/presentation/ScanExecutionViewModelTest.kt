package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import com.example.hogarscan.scan.domain.usecase.ScanCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanExecutionViewModelTest {

    private class FakeRepository : WifiNetworkRepository {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var response: WifiNetworkResult = WifiNetworkResult.Available(
            NetworkScope(Ipv4Address.parse("192.168.28.43"), 24),
            "wifi-1:1"
        )

        override fun observeNetworkChanges(): Flow<Unit> = changes

        override suspend fun getCurrentNetwork(): WifiNetworkResult {
            val captured = response
            delay(1)
            return captured
        }
    }

    private class FakeProbe(
        private val failure: ((Ipv4Address, Int) -> Unit)? = null
    ) : TcpProbe {
        var calls = 0
        var active = 0

        override suspend fun probe(
            connectionId: String,
            address: Ipv4Address,
            port: Int
        ): ProbeResult {
            calls++
            active++
            val duration = if (
                address == Ipv4Address.parse("192.168.28.40") && port == 80
            ) 10L else 1_000L

            try {
                delay(duration)
                failure?.invoke(address, port)
                return ProbeResult(address, port, ProbeOutcome.TIMED_OUT, duration)
            } finally {
                active--
            }
        }
    }

    private val store = ViewModelStore()

    private fun viewModel(
        repository: FakeRepository,
        probe: FakeProbe
    ): WifiViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                WifiViewModel(repository, ScanCoordinator(repository, probe))
            }
        }
    )[WifiViewModel::class.java]

    private fun TestScope.authorize(viewModel: WifiViewModel) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.updateStartAddress("192.168.28.40")
        viewModel.updateEndAddress("192.168.28.45")
        viewModel.togglePort(80)
        viewModel.togglePort(443)
        viewModel.reviewConfiguration()
        viewModel.confirmAuthorization()
        advanceUntilIdle()
        assertNotNull(viewModel.setupState.value.authorizedConfig)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `no inicia sin autorizacion`() = runTest {
        val probe = FakeProbe()
        val viewModel = viewModel(FakeRepository(), probe)

        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(ScanStatus.IDLE, viewModel.scanState.value.status)
        assertEquals(0, probe.calls)
    }

    @Test
    fun `doble pulsacion ejecuta un solo rango y consume la autorizacion`() =
        runTest {
            val repository = FakeRepository()
            val probe = FakeProbe()
            val viewModel = viewModel(repository, probe)
            authorize(viewModel)

            viewModel.startScan()
            viewModel.startScan()
            advanceUntilIdle()

            assertEquals(ScanStatus.COMPLETED, viewModel.scanState.value.status)
            assertEquals(10, probe.calls)
            assertEquals(10, viewModel.scanState.value.results.size)
            assertNull(viewModel.setupState.value.authorizedConfig)

            viewModel.startScan()
            advanceUntilIdle()
            assertEquals(10, probe.calls)
        }

    @Test
    fun `cancelar conserva los resultados y detiene los intentos activos`() =
        runTest {
            val repository = FakeRepository()
            val probe = FakeProbe()
            val viewModel = viewModel(repository, probe)
            authorize(viewModel)
            viewModel.startScan()
            advanceTimeBy(30)
            runCurrent()
            assertEquals(1, viewModel.scanState.value.results.size)

            viewModel.cancelScan()
            advanceUntilIdle()

            assertEquals(ScanStatus.CANCELLED, viewModel.scanState.value.status)
            assertEquals(1, viewModel.scanState.value.results.size)
            assertEquals(0, probe.active)
            assertFalse(viewModel.scanState.value.isActive)
        }

    @Test
    fun `cancelar inmediatamente no deja la pantalla deteniendo para siempre`() =
        runTest {
            val repository = FakeRepository()
            val probe = FakeProbe()
            val viewModel = viewModel(repository, probe)
            authorize(viewModel)

            viewModel.startScan()
            viewModel.cancelScan()
            advanceUntilIdle()

            assertEquals(ScanStatus.CANCELLED, viewModel.scanState.value.status)
            assertEquals(0, probe.calls)
            assertFalse(viewModel.scanState.value.isActive)
        }

    @Test
    fun `una conexion cambiada antes de iniciar no abre intentos`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe()
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)
        repository.response =
            (repository.response as WifiNetworkResult.Available).copy(
                connectionId = "wifi-2:1"
            )

        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(ScanStatus.INTERRUPTED, viewModel.scanState.value.status)
        assertEquals(0, probe.calls)
    }

    @Test
    fun `perder la red interrumpe y mantiene los resultados parciales`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe()
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)
        viewModel.startScan()
        advanceTimeBy(30)
        runCurrent()

        repository.response = WifiNetworkResult.Unavailable(
            WifiNetworkResult.Reason.NO_NETWORK
        )
        repository.changes.emit(Unit)
        advanceUntilIdle()

        assertEquals(ScanStatus.INTERRUPTED, viewModel.scanState.value.status)
        assertEquals(1, viewModel.scanState.value.results.size)
        assertEquals(0, probe.active)
    }

    @Test
    fun `un aviso sin cambio de conexion no interrumpe la revision`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe()
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)
        viewModel.startScan()
        advanceTimeBy(30)
        runCurrent()

        repository.changes.emit(Unit)
        advanceUntilIdle()

        assertEquals(ScanStatus.COMPLETED, viewModel.scanState.value.status)
        assertEquals(10, viewModel.scanState.value.results.size)
    }

    @Test
    fun `bloquea la edicion de alcance durante la revision`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe()
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)
        viewModel.startScan()

        viewModel.updateStartAddress("192.168.28.1")
        viewModel.updateEndAddress("192.168.28.60")
        viewModel.togglePort(22)

        assertEquals("192.168.28.40", viewModel.setupState.value.startAddress)
        assertEquals("192.168.28.45", viewModel.setupState.value.endAddress)
        assertEquals(setOf(80, 443), viewModel.setupState.value.selectedPorts)
        assertTrue(viewModel.scanState.value.isActive)

        viewModel.cancelScan()
        advanceUntilIdle()
    }

    @Test
    fun `el cambio detectado por el adaptador marca interrupcion`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe { address, port ->
            if (address != Ipv4Address.parse("192.168.28.40") || port != 80) {
                throw ScanConnectionChangedException()
            }
        }
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)

        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(ScanStatus.INTERRUPTED, viewModel.scanState.value.status)
        assertEquals(1, viewModel.scanState.value.results.size)
        assertEquals(0, probe.active)
    }

    @Test
    fun `un fallo inesperado conserva los resultados y marca error`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe { address, port ->
            if (address != Ipv4Address.parse("192.168.28.40") || port != 80) {
                error("Fallo simulado")
            }
        }
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)

        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(ScanStatus.FAILED, viewModel.scanState.value.status)
        assertEquals(1, viewModel.scanState.value.results.size)
        assertEquals(0, probe.active)
    }

    @Test
    fun `eliminar el ViewModel detiene los intentos activos`() = runTest {
        val repository = FakeRepository()
        val probe = FakeProbe()
        val viewModel = viewModel(repository, probe)
        authorize(viewModel)
        viewModel.startScan()
        advanceTimeBy(30)
        runCurrent()

        store.clear()
        advanceUntilIdle()

        assertEquals(0, probe.active)
        assertFalse(viewModel.scanState.value.isActive)
    }
}
