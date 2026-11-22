package com.example.hogarscan.statistics.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.statistics.domain.AccountStatistics
import com.example.hogarscan.statistics.domain.StatisticsFindingCounts
import com.example.hogarscan.statistics.domain.StatisticsGateway
import com.example.hogarscan.statistics.domain.StatisticsOutcome
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun muestraEstadoVacioYPermiteActualizar() {
        var refreshed = false
        val empty = AccountStatistics(0, "2026-11-11T05:32:33Z", 0, 0, 0, 0,
            StatisticsFindingCounts(0, 0, 0, 0, 0), emptyList())
        compose.setContent { MaterialTheme { StatisticsScreen(StatisticsUiState.Data(empty), { refreshed = true }) } }
        compose.onNodeWithText("Aún no hay resúmenes sincronizados.").assertIsDisplayed()
        compose.onNodeWithText("Actualizar").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(refreshed) }
    }

    @Test fun explicaPorQueSeNecesitaSesion() {
        compose.setContent { MaterialTheme { StatisticsScreen(StatisticsUiState.SignInRequired, {}) } }
        compose.onNodeWithText("Inicia sesión para consultar las estadísticas de tu cuenta.").assertIsDisplayed()
    }

    @Test fun consultaAlEntrarYRecargaCuandoCambiaLaCuenta() {
        val session = SessionSnapshot("ctx-a", "user-a", "a@example.com", Long.MAX_VALUE, 1)
        val sessions = object : SessionReader {
            override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
        }
        val gateway = StatisticsGateway { current ->
            val total = if (current.userId == "user-a") 1L else 2L
            StatisticsOutcome.Success(AccountStatistics(1, "2026-11-11T00:00:00Z", total, 0, 0, 0,
                StatisticsFindingCounts(0, 0, 0, 0, 0), emptyList()))
        }
        val viewModel = StatisticsViewModel(sessions, gateway, { 1 })
        compose.setContent { MaterialTheme { StatisticsRoute(viewModel) } }
        compose.onNodeWithText("Revisiones: 1").assertIsDisplayed()
        compose.runOnIdle { sessions.state.value = SessionState.Authenticated(session.copy(contextId = "ctx-b", userId = "user-b")) }
        compose.onNodeWithText("Revisiones: 2").assertIsDisplayed()
    }
}
