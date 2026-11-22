package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesAction
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteSummariesViewModelTest {
    private val lifecycle = ViewModelStore()
    private val session = SessionSnapshot("ctx", "account", "a@example.com", Long.MAX_VALUE, 2)
    private val sessions = object : SessionReader {
        override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
    }
    private fun vm(action: DeleteSummariesAction) = ViewModelProvider(lifecycle, viewModelFactory {
        initializer { DeleteSummariesViewModel(sessions, action) { 1 } }
    })[DeleteSummariesViewModel::class.java]
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { lifecycle.clear(); Dispatchers.resetMain() }

    @Test fun `cancelar confirmacion no envia borrado`() = runTest {
        var calls = 0
        val viewModel = vm { calls++; DeleteSummariesOutcome.DELETED }
        advanceUntilIdle()
        viewModel.requestConfirmation()
        assertEquals(session, viewModel.uiState.value.confirmation)
        viewModel.cancelConfirmation()
        viewModel.confirm(); advanceUntilIdle()
        assertEquals(0, calls)
    }

    @Test fun `conserva generacion confirmada e impide doble envio al recrear pantalla`() = runTest {
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<SessionSnapshot>()
        val action = DeleteSummariesAction {
            received += it
            release.await()
            DeleteSummariesOutcome.ALREADY_ADVANCED
        }
        val first = vm(action)
        advanceUntilIdle()
        first.requestConfirmation()
        sessions.state.value = SessionState.Authenticated(session.copy(summaryGeneration = 3))
        runCurrent()
        val recreated = vm(action)
        assertSame(first, recreated)
        assertEquals(2, recreated.uiState.value.confirmation?.summaryGeneration)
        recreated.confirm(); runCurrent()
        assertTrue(recreated.uiState.value.pending)
        recreated.requestConfirmation(); recreated.confirm(); runCurrent()
        assertEquals(listOf(session), received)
        release.complete(Unit); advanceUntilIdle()
        assertFalse(recreated.uiState.value.pending)
        assertTrue(recreated.uiState.value.message!!.contains("generación ya cambió"))
    }

    @Test fun `respuesta tardia del borrado no aparece en otra cuenta`() = runTest {
        val release = CompletableDeferred<Unit>()
        val viewModel = vm {
            withContext(NonCancellable) { release.await() }
            DeleteSummariesOutcome.DELETED
        }
        advanceUntilIdle()
        viewModel.requestConfirmation(); viewModel.confirm(); runCurrent()
        val other = session.copy(contextId = "other", userId = "other")
        sessions.state.value = SessionState.Authenticated(other)
        runCurrent(); release.complete(Unit); advanceUntilIdle()
        assertEquals(DeleteSummariesUiState(session = other), viewModel.uiState.value)
    }
}
