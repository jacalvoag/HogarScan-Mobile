package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanSetupViewModelTest {

    private class FakeRepository : WifiNetworkRepository {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var calls = 0
        var failure: Exception? = null
        var response: WifiNetworkResult = WifiNetworkResult.Available(
            scope = NetworkScope(Ipv4Address.parse("192.168.28.43"), 24),
            connectionId = "wifi-1:1"
        )

        override fun observeNetworkChanges(): Flow<Unit> = changes

        override suspend fun getCurrentNetwork(): WifiNetworkResult {
            calls++
            val capturedResponse = response
            val capturedFailure = failure
            delay(100)
            capturedFailure?.let { throw it }
            return capturedResponse
        }
    }

    private val store = ViewModelStore()

    private fun viewModel(repository: FakeRepository): WifiViewModel =
        ViewModelProvider(
            store,
            viewModelFactory {
                initializer { networkTestViewModel(repository) }
            }
        )[WifiViewModel::class.java]

    private fun review(viewModel: WifiViewModel) {
        viewModel.updateStartAddress("192.168.28.40")
        viewModel.updateEndAddress("192.168.28.45")
        viewModel.togglePort(80)
        viewModel.togglePort(443)
        viewModel.reviewConfiguration()
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
    fun `confirmar vuelve a consultar la red antes de autorizar`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        review(viewModel)

        viewModel.confirmAuthorization()

        assertNull(viewModel.setupState.value.authorizedConfig)
        advanceUntilIdle()

        assertEquals(2, repository.calls)
        assertNotNull(viewModel.setupState.value.authorizedConfig)
    }

    @Test
    fun `rechaza confirmar si la red cambio sin recibir aun el aviso`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        review(viewModel)
        repository.response =
            (repository.response as WifiNetworkResult.Available).copy(
                connectionId = "wifi-2:1"
            )

        viewModel.confirmAuthorization()
        advanceUntilIdle()

        assertNull(viewModel.setupState.value.confirmation)
        assertNull(viewModel.setupState.value.authorizedConfig)
    }

    @Test
    fun `editar durante la consulta de confirmacion no autoriza el borrador anterior`() =
        runTest {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)
            viewModel.refresh()
            advanceUntilIdle()
            review(viewModel)

            viewModel.confirmAuthorization()
            runCurrent()
            viewModel.updateEndAddress("192.168.28.46")
            advanceUntilIdle()

            assertNull(viewModel.setupState.value.authorizedConfig)
        }

    @Test
    fun `un aviso de cambio cancela una confirmacion en curso`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        review(viewModel)

        viewModel.confirmAuthorization()
        runCurrent()
        repository.response =
            (repository.response as WifiNetworkResult.Available).copy(
                connectionId = "wifi-1:3"
            )
        repository.changes.emit(Unit)
        advanceUntilIdle()

        assertNull(viewModel.setupState.value.confirmation)
        assertNull(viewModel.setupState.value.authorizedConfig)
    }

    @Test
    fun `un error al volver a consultar retira la autorizacion`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        review(viewModel)
        viewModel.confirmAuthorization()
        advanceUntilIdle()
        assertNotNull(viewModel.setupState.value.authorizedConfig)

        repository.failure = IllegalStateException("Fallo simulado")
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(WifiUiState.Error, viewModel.uiState.value)
        assertNull(viewModel.setupState.value.authorizedConfig)
    }
}
