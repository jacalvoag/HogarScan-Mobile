package com.example.hogarscan.evaluations.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeScanEvaluationResultsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listaGrandeComponeSoloObjetivosCercanosYPermiteAbrirElUltimo() {
        val evaluations = (1..254).map { host ->
            val address = Ipv4Address.parse("192.168.1.$host")
            val probes = listOf(22, 23, 80, 443, 445, 3389).map {
                ProbeResult(address, it, ProbeOutcome.ACCEPTED, 1)
            }
            val findings = probes.map { probe -> Finding(
                FindingCategory.TCP_SERVICE_ACCESSIBLE, "Hallazgo $host:${probe.port}",
                FindingPriority.LOW, FindingConfidence.OBSERVED, "Conexión TCP aceptada",
                "Se aceptó la conexión", "Revisar configuración", "No confirma vulnerabilidad",
                "tcp-${probe.port}", null)
            }
            TargetEvaluation(EvaluationInput.fromResults("scan", "user:account", address, probes),
                EvaluationResult("1", "2026-11-11T00:00:00Z",
                    EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), findings, emptyList()))
        }
        var selected: FindingDetail? = null
        compose.setContent { MaterialTheme {
            ScanEvaluationResultsList(evaluations, onFindingSelected = { selected = it })
        } }
        assertTrue(compose.onAllNodesWithTag("evaluation-target").fetchSemanticsNodes().size < evaluations.size)
        compose.onNodeWithTag("scan-evaluation-results").performScrollToIndex(253)
        compose.onNodeWithText("Objetivo: 192.168.1.254").assertIsDisplayed()
        compose.onNodeWithText("Hallazgo 254:22").performClick()
        compose.runOnIdle { assertEquals("Hallazgo 254:22", selected?.title) }
    }
}
