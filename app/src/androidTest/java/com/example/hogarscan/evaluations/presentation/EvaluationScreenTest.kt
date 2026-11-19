package com.example.hogarscan.evaluations.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.evaluations.domain.model.EvaluationSources
import com.example.hogarscan.evaluations.domain.model.SourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvaluationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun evaluaSinIdentificacionCuandoEsOpcional() {
        var called = false
        var identification: Any? = "not-called"
        compose.setContent {
            MaterialTheme {
                EvaluationScreen(EvaluationUiState.Ready, IdentificationForm(), IdentificationErrors(),
                    onFormChange = {}, onEvaluate = { called = true; identification = it },
                    onInvalidForm = {}, onCancel = {})
            }
        }
        compose.onNodeWithText("Evaluar").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(called); assertNull(identification) }
    }

    @Test fun permiteCancelarLaEvaluacion() {
        var cancelled = false
        compose.setContent {
            MaterialTheme {
                EvaluationScreen(EvaluationUiState.Loading, IdentificationForm(), IdentificationErrors(),
                    onFormChange = {}, onEvaluate = {}, onInvalidForm = {}, onCancel = { cancelled = true })
            }
        }
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun distingueFuentesNoConsultadasYNoDisponibles() {
        val result = EvaluationResult("1", "2026-01-01T00:00:00Z",
            EvaluationSources(SourceState.UNAVAILABLE, SourceState.NOT_QUERIED, "2026-01-01T00:00:00Z"),
            emptyList(), emptyList())
        compose.setContent { MaterialTheme { EvaluationResultPanel(result) } }
        compose.onNodeWithText("NVD: no disponible · KEV: no consultada").assertIsDisplayed()
        compose.onNodeWithText("La evaluación no devolvió hallazgos.").assertIsDisplayed()
        assertEquals(SourceState.UNAVAILABLE, result.sources.nvd)
    }
    @Test fun prioridadDeRevisionOrdenaLaVistaSinCambiarElIndiceDelHallazgo() {
        val low = com.example.hogarscan.evaluations.domain.model.Finding(
            com.example.hogarscan.evaluations.domain.model.FindingCategory.TCP_SERVICE_ACCESSIBLE, "Observación TCP",
            com.example.hogarscan.evaluations.domain.model.FindingPriority.LOW,
            com.example.hogarscan.evaluations.domain.model.FindingConfidence.OBSERVED,
            "Evidencia", "Explicación", "Acción", "Límite", "TCP_ACCEPTED", null)
        val high = low.copy(title = "Revisión prioritaria", priority = com.example.hogarscan.evaluations.domain.model.FindingPriority.HIGH)
        var selected = -1
        val result = EvaluationResult("0.4.0", "2026-11-19T16:00:00Z",
            EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), listOf(low, high), emptyList())
        compose.setContent { MaterialTheme { EvaluationResultPanel(result, onFindingClick = { selected = it }) } }
        compose.onNodeWithText("Revisión prioritaria").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

}
