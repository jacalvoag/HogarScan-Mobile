package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@OptIn(ExperimentalCoroutinesApi::class)
class WifiViewModelTest {

    private class FakeRepository : WifiNetworkRepository {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        var calls = 0
        var cancelledCalls = 0

        var response: WifiNetworkResult =
            WifiNetworkResult.Unavailable(
                WifiNetworkResult.Reason.NO_NETWORK
            )

        var failure: Exception? = null

        override fun observeNetworkChanges(): Flow<Unit> = changes

        override suspend fun getCurrentNetwork(): WifiNetworkResult {
            calls++

            val capturedResponse = response
            val capturedFailure = failure

            try {
                delay(100)
            } catch (exception: CancellationException) {
                cancelledCalls++
                throw exception
            }

            capturedFailure?.let { throw it }

            return capturedResponse
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `publica la red encontrada despues de cargar`() = runTest {
        val repository = FakeRepository()
        val available = WifiNetworkResult.Available(
            scope = NetworkScope(
                localAddress = Ipv4Address.parse("192.168.1.10"),
                prefixLength = 24
            ),
            connectionId = "connection-1"
        )
        repository.response = available

        val viewModel = WifiViewModel(repository)

        assertEquals(WifiUiState.Idle, viewModel.uiState.value)

        viewModel.refresh()

        assertEquals(WifiUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(
            WifiUiState.Loaded(available),
            viewModel.uiState.value
        )
    }

    @Test
    fun `reemplaza la red anterior cuando deja de estar disponible`() = runTest {
        val repository = FakeRepository()
        repository.response = WifiNetworkResult.Available(
            scope = NetworkScope(
                localAddress = Ipv4Address.parse("192.168.1.10"),
                prefixLength = 24
            ),
            connectionId = "connection-1"
        )

        val viewModel = WifiViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()

        val unavailable = WifiNetworkResult.Unavailable(
            WifiNetworkResult.Reason.NO_NETWORK
        )
        repository.response = unavailable

        viewModel.refresh()

        assertEquals(WifiUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(
            WifiUiState.Loaded(unavailable),
            viewModel.uiState.value
        )
    }

    @Test
    fun `ignora nuevas consultas mientras hay una en curso`() = runTest {
        val repository = FakeRepository()
        val viewModel = WifiViewModel(repository)

        viewModel.refresh()
        runCurrent()

        viewModel.refresh()
        viewModel.refresh()
        runCurrent()

        assertEquals(1, repository.calls)
        assertEquals(WifiUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(
            WifiUiState.Loaded(repository.response),
            viewModel.uiState.value
        )
    }

    @Test
    fun `permite reintentar despues de un error inesperado`() = runTest {
        val repository = FakeRepository()
        repository.failure = IllegalStateException("Fallo simulado")

        val viewModel = WifiViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(WifiUiState.Error, viewModel.uiState.value)

        repository.failure = null

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, repository.calls)
        assertEquals(
            WifiUiState.Loaded(repository.response),
            viewModel.uiState.value
        )
    }

    @Test
    fun `consulta automaticamente cuando recibe un cambio de red`() = runTest {
        val repository = FakeRepository()
        val viewModel = WifiViewModel(repository)

        // Inicia la suscripcion del ViewModel.
        runCurrent()

        repository.changes.emit(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals(
            WifiUiState.Loaded(repository.response),
            viewModel.uiState.value
        )
    }

    @Test
    fun `un cambio de red cancela la consulta anterior`() = runTest {
        val repository = FakeRepository()
        val previousNetwork = WifiNetworkResult.Available(
            scope = NetworkScope(
                localAddress = Ipv4Address.parse("192.168.1.10"),
                prefixLength = 24
            ),
            connectionId = "connection-1"
        )
        repository.response = previousNetwork

        val viewModel = WifiViewModel(repository)
        viewModel.refresh()

        // La primera consulta queda suspendida dentro de delay.
        runCurrent()

        val disconnected = WifiNetworkResult.Unavailable(
            WifiNetworkResult.Reason.NO_NETWORK
        )
        repository.response = disconnected
        repository.changes.emit(Unit)

        runCurrent()

        assertEquals(1, repository.cancelledCalls)
        assertEquals(WifiUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(2, repository.calls)
        assertEquals(
            WifiUiState.Loaded(disconnected),
            viewModel.uiState.value
        )
    }
}