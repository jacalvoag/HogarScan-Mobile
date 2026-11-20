package com.example.hogarscan.evaluations.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
        compose.onNodeWithText("Ver fuentes públicas").performScrollTo().performClick()
        compose.onNodeWithText("NVD: No consultada · KEV: No consultada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test fun iconoDeRegresoCoincideConElDisenoRevisado() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val svg = SVG.getFromAsset(assets, "figma/revised/back.svg")
        assertTrue(svg.documentWidth == 24f && svg.documentHeight == 24f)
    }

    @Test fun capturaPantallaParaRevisionVisual() {
        compose.setContent { MaterialTheme { FindingDetailScreen(detail, {}) } }
        val dir = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
        fun capture(name: String) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("finding-detail-top.png")
        compose.onNodeWithText("Qué puedes revisar").performScrollTo()
        capture("finding-detail-middle.png")
        compose.onNodeWithText("Ver fuentes públicas").performScrollTo().performClick()
        compose.onNodeWithText("NVD: No consultada · KEV: No consultada").performScrollTo()
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
