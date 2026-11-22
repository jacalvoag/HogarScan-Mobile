package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetEvaluationPanelTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun muestraSoloLosHallazgosDeLaDireccionSeleccionada() {
        var selected: FindingDetail? = null
        val saved = ScanEvaluationUiState.Saved(
            evaluations = listOf(
                evaluation("192.168.1.2", "Hallazgo A"),
                evaluation("192.168.1.3", "Hallazgo B")
            ),
            message = "Guardada"
        )

        show(saved, onFindingSelected = { selected = it })

        compose.onNodeWithText("Hallazgo A").assertExists()
        compose.onNodeWithText("Hallazgo B").assertDoesNotExist()
        compose.onNodeWithText("Ver hallazgo")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals("Hallazgo A", selected?.title)
        }
    }

    @Test
    fun noMuestraEvaluacionesDeOtraRevisionOPropietario() {
        val saved = ScanEvaluationUiState.Saved(
            evaluations = listOf(
                evaluation(
                    "192.168.1.2",
                    "Otra revisión",
                    scanId = "scan-b"
                ),
                evaluation(
                    "192.168.1.2",
                    "Otro propietario",
                    ownerId = "user:b"
                )
            ),
            message = "Guardada"
        )

        show(saved)

        compose.onNodeWithText("Otra revisión").assertDoesNotExist()
        compose.onNodeWithText("Otro propietario").assertDoesNotExist()
        compose.onNodeWithText("Hallazgos no disponibles").assertExists()
    }

    @Test
    fun laEvaluacionPendienteSoloAbreLaPreparacionAlPulsar() {
        var opened = 0

        show(
            state = ScanEvaluationUiState.Ready,
            onOpenEvaluation = { opened++ }
        )

        compose.onNodeWithText("Evaluación pendiente").assertExists()
        compose.runOnIdle {
            assertEquals(0, opened)
        }

        compose.onNodeWithText("Preparar evaluación de la revisión")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals(1, opened)
        }
    }

    private fun show(
        state: ScanEvaluationUiState,
        onFindingSelected: (FindingDetail) -> Unit = {},
        onOpenEvaluation: (() -> Unit)? = null
    ) {
        compose.setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    TargetEvaluationPanel(
                        scanId = "scan-a",
                        ownerId = "user:a",
                        address = "192.168.1.2",
                        state = state,
                        onFindingSelected = onFindingSelected,
                        onOpenEvaluation = onOpenEvaluation
                    )
                }
            }
        }
    }

    private fun evaluation(
        address: String,
        title: String,
        scanId: String = "scan-a",
        ownerId: String = "user:a"
    ): TargetEvaluation {
        val ip = Ipv4Address.parse(address)
        val input = EvaluationInput.fromResults(
            scanId = scanId,
            ownerId = ownerId,
            address = ip,
            results = listOf(
                ProbeResult(ip, 80, ProbeOutcome.ACCEPTED, 1L)
            )
        )

        val finding = Finding(
            category = FindingCategory.TCP_SERVICE_ACCESSIBLE,
            title = title,
            priority = FindingPriority.UNDETERMINED,
            confidence = FindingConfidence.OBSERVED,
            evidence = "Conexión aceptada",
            explanation = "Se aceptó el intento TCP.",
            action = "Revisar si el servicio es necesario.",
            limitation = "No confirma una vulnerabilidad.",
            ruleId = "TCP_ACCEPTED",
            cve = null
        )

        return TargetEvaluation(
            input = input,
            result = EvaluationResult(
                rulesetVersion = "0.4.0",
                evaluatedAt = "2026-11-19T00:00:00Z",
                sources = EvaluationSources(
                    SourceState.NOT_QUERIED,
                    SourceState.NOT_QUERIED,
                    null
                ),
                findings = listOf(finding),
                possibleCveMatches = emptyList()
            )
        )
    }
}