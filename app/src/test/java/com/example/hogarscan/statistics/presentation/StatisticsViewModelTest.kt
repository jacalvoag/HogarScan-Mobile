package com.example.hogarscan.statistics.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.statistics.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val store = ViewModelStore()
    private val session = SessionSnapshot("ctx", "account", "a@example.com", Long.MAX_VALUE, 1)
    private val sessions = object : SessionReader {
        override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
    }
    private val empty = AccountStatistics(1, "2026-11-11T05:32:33Z", 0, 0, 0, 0,
        StatisticsFindingCounts(0, 0, 0, 0, 0), emptyList())
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm(gateway: StatisticsGateway) = ViewModelProvider(store, viewModelFactory {
        initializer { StatisticsViewModel(sessions, gateway, { 1 }) }
    })[StatisticsViewModel::class.java]

    @Test fun `muestra cuenta vacia y datos`() = runTest {
        val viewModel = vm(StatisticsGateway { StatisticsOutcome.Success(empty) })
        viewModel.refresh(); advanceUntilIdle()
        assertEquals(empty, (viewModel.uiState.value as StatisticsUiState.Data).statistics)
    }

    @Test fun `cambio de cuenta impide publicar respuesta tardia`() = runTest {
        val release = CompletableDeferred<Unit>()
        val viewModel = vm(StatisticsGateway {
            withContext(NonCancellable) { release.await() }
            StatisticsOutcome.Success(empty)
        })
        viewModel.refresh(); runCurrent()
        sessions.state.value = SessionState.Authenticated(session.copy(contextId = "new", userId = "other"))
        runCurrent(); release.complete(Unit); advanceUntilIdle()
        assertEquals(StatisticsUiState.Idle, viewModel.uiState.value)
    }

    @Test fun `cambio de generacion limpia resultados y no acepta respuesta vieja`() = runTest {
        val viewModel = vm(StatisticsGateway { StatisticsOutcome.Success(empty) })
        viewModel.refresh(); advanceUntilIdle()
        sessions.state.value = SessionState.Authenticated(session.copy(summaryGeneration = 2))
        advanceUntilIdle()
        assertEquals(StatisticsUiState.Idle, viewModel.uiState.value)
        viewModel.refresh(); advanceUntilIdle()
        assertTrue(viewModel.uiState.value is StatisticsUiState.Error)
    }

    @Test fun `sin sesion no se consulta`() = runTest {
        var calls = 0
        val viewModel = vm(StatisticsGateway { calls++; StatisticsOutcome.Success(empty) })
        sessions.state.value = SessionState.Guest
        viewModel.refresh(); advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(StatisticsUiState.SignInRequired, viewModel.uiState.value)
    }
}
