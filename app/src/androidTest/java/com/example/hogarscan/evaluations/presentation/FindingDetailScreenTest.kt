package com.example.hogarscan.evaluations.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caverock.androidsvg.SVG
import com.example.hogarscan.evaluations.domain.model.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Bitmap
import java.io.File

@RunWith(AndroidJUnit4::class)
class FindingDetailScreenTest {
    @get:Rule val compose = createComposeRule()
    private val detail = FindingDetail("Conexión aceptada", "Conexión TCP aceptada", "Baja", "Observada",
        "Puerto 80 aceptado", "El objetivo aceptó la conexión", "Verifica si necesitas el servicio",
        "Esto no confirma una vulnerabilidad", "TCP_ACCEPTED", "0.3.0", "No consultada", "No consultada", null, null)

    @Test fun muestraDatosRealesYPermiteVolver() {
        var back = false
        compose.setContent { MaterialTheme { FindingDetailScreen(detail, { back = true }) } }
        compose.onNodeWithText("Conexión aceptada").assertIsDisplayed()
        compose.onNodeWithText("Esto no confirma una vulnerabilidad").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NVD: No consultada · KEV: No consultada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VOLVER AL EQUIPO").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test fun recursosSvgExistenYUsanLasDimensionesDelDiseno() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val names = listOf("back.svg", "info.svg", "priority.svg", "certainty.svg", "guidance.svg", "sources.svg", "source_state.svg", "return.svg")
        names.forEach { name ->
            val svg = SVG.getFromAsset(assets, "figma/finding_detail/$name")
            assertTrue(svg.documentWidth > 0f && svg.documentHeight > 0f)
        }
        compose.setContent { MaterialTheme { FindingDetailScreen(detail, {}) } }
        compose.onNodeWithTag("finding-icon-back.svg").assertWidthIsEqualTo(13.3333.dp).assertHeightIsEqualTo(13.3333.dp)
        compose.onNodeWithTag("finding-icon-info.svg").assertWidthIsEqualTo(15.8333.dp).assertHeightIsEqualTo(15.8333.dp)
        compose.onNodeWithTag("finding-icon-priority.svg").assertWidthIsEqualTo(12.dp).assertHeightIsEqualTo(15.dp)
        compose.onNodeWithTag("finding-icon-certainty.svg").assertWidthIsEqualTo(13.3333.dp).assertHeightIsEqualTo(16.dp)
        compose.onNodeWithTag("finding-icon-guidance.svg").assertWidthIsEqualTo(16.6667.dp).assertHeightIsEqualTo(15.dp)
        compose.onNodeWithTag("finding-icon-sources.svg").assertWidthIsEqualTo(13.3333.dp).assertHeightIsEqualTo(16.6667.dp)
        compose.onNodeWithTag("finding-icon-source_state.svg").assertWidthIsEqualTo(15.dp).assertHeightIsEqualTo(12.0562.dp)
        compose.onNodeWithTag("finding-icon-return.svg").assertWidthIsEqualTo(16.6667.dp).assertHeightIsEqualTo(13.3333.dp)
    }

    @Test fun capturaPantallaParaRevisionVisual() {
        compose.setContent { MaterialTheme { FindingDetailScreen(detail, {}) } }
        val dir = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
        fun capture(name: String) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("finding-detail-top.png")
        compose.onNodeWithText("3. QUÉ PUEDES REVISAR").performScrollTo()
        capture("finding-detail-middle.png")
        compose.onNodeWithText("VOLVER AL EQUIPO").performScrollTo()
        capture("finding-detail-bottom.png")
    }

    @Test fun hallazgoPermiteAbrirSuDetalle() {
        var selected = -1
        val finding = Finding(FindingCategory.TCP_SERVICE_ACCESSIBLE, "Conexión aceptada", FindingPriority.LOW,
            FindingConfidence.OBSERVED, "Evidencia", "Explicación", "Acción", "Limitación", "TCP_ACCEPTED", null)
        val result = EvaluationResult("1", "2026-11-11T00:00:00Z",
            EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), listOf(finding), emptyList())
        compose.setContent { MaterialTheme { EvaluationResultPanel(result, onFindingClick = { selected = it }) } }
        compose.onNodeWithText("Ver detalle").performClick()
        compose.runOnIdle { assertTrue(selected == 0) }
    }
}
