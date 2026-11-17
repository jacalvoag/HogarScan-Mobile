package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.domain.EvaluationFailure
import com.example.hogarscan.evaluations.domain.EvaluationGateway
import com.example.hogarscan.evaluations.domain.EvaluationOutcome
import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.evaluations.domain.model.EvaluationSources
import com.example.hogarscan.evaluations.domain.model.SourceState
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationViewModelTest {
    private val store = ViewModelStore()
    private val session = SessionSnapshot("context-a", "account-a", "a@example.com", Long.MAX_VALUE, 0)
    private val sessions = object : SessionReader {
        override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
    }
    private val ip = Ipv4Address.parse("192.168.1.2")
    private val result = EvaluationResult("1", "2026-11-11T05:32:33Z",
        EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun viewModel(gateway: EvaluationGateway): EvaluationViewModel = ViewModelProvider(store,
        viewModelFactory { initializer {
            EvaluationViewModel("scan", "user:account-a", ip,
                listOf(ProbeResult(ip, 80, ProbeOutcome.ACCEPTED, 0)), sessions, gateway, { 100L })
        } })[EvaluationViewModel::class.java]

    @Test fun `publica el resultado y usa la sesion y propietario esperados`() = runTest {
        val vm = viewModel(EvaluationGateway { input, expected ->
            assertEquals("user:account-a", input.ownerId)
            assertEquals(session, expected)
            EvaluationOutcome.Success(result)
        })
        vm.evaluate()
        assertTrue(vm.uiState.value.evaluation is EvaluationUiState.Loading)
        advanceUntilIdle()
        assertEquals(result, (vm.uiState.value.evaluation as EvaluationUiState.Success).result)
    }

    @Test fun `cancelar impide publicar una respuesta tardia`() = runTest {
        val release = CompletableDeferred<Unit>()
        val vm = viewModel(EvaluationGateway { _, _ ->
            withContext(NonCancellable) { release.await() }
            EvaluationOutcome.Success(result)
        })
        vm.evaluate(); runCurrent()
        vm.cancel()
        release.complete(Unit); advanceUntilIdle()
        assertEquals(EvaluationUiState.Ready, vm.uiState.value.evaluation)
    }

    @Test fun `cambiar de cuenta limpia el formulario y bloquea respuestas tardias`() = runTest {
        val release = CompletableDeferred<Unit>()
        val vm = viewModel(EvaluationGateway { _, _ ->
            withContext(NonCancellable) { release.await() }
            EvaluationOutcome.Success(result)
        })
        vm.updateForm(IdentificationForm(vendor = "dato anterior"))
        vm.evaluate(); runCurrent()
        sessions.state.value = SessionState.Authenticated(session.copy(contextId = "context-b", userId = "account-b"))
        runCurrent(); release.complete(Unit); advanceUntilIdle()
        assertTrue(vm.uiState.value.evaluation is EvaluationUiState.Error)
        assertEquals(IdentificationForm(), vm.uiState.value.form)
        vm.evaluate(); advanceUntilIdle()
        assertTrue(vm.uiState.value.evaluation is EvaluationUiState.Error)
    }

    @Test fun `fallos y formulario invalido no se presentan como exito`() = runTest {
        var calls = 0
        val vm = viewModel(EvaluationGateway { _, _ -> calls++; EvaluationOutcome.Failure(EvaluationFailure.TIMEOUT) })
        vm.updateForm(IdentificationForm(enabled = true))
        vm.evaluate(); advanceUntilIdle()
        assertEquals(0, calls)
        assertTrue(vm.uiState.value.errors.vendor)
        vm.updateForm(IdentificationForm())
        vm.evaluate(); advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.evaluation is EvaluationUiState.Error)
    }

    @Test fun `cambiar cuenta limpia formulario aun sin solicitud`() = runTest {
        val vm = viewModel(EvaluationGateway { _, _ -> EvaluationOutcome.Success(result) })
        runCurrent()
        vm.updateForm(IdentificationForm(vendor = "dato anterior"))
        sessions.state.value = SessionState.Guest
        advanceUntilIdle()
        assertEquals(IdentificationForm(), vm.uiState.value.form)
        assertTrue(vm.uiState.value.evaluation is EvaluationUiState.Error)
    }
}
