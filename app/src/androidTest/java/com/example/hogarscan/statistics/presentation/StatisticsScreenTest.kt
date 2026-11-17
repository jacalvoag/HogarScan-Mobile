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
}
