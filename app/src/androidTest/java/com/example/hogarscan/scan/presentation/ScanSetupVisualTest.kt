package com.example.hogarscan.scan.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.scan.domain.policy.ScanPolicy
import com.example.hogarscan.ui.theme.HogarScanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanSetupVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun limitaSeleccionDePuertosYPermiteCorregirla() {
        var state by mutableStateOf(ScanSetupState(networkAvailable = true))
        compose.setContent {
            HogarScanTheme {
                ScanSetupForm(state, {}, {}, { port ->
                    val ports = if (port in state.selectedPorts) state.selectedPorts - port
                        else state.selectedPorts + port
                    state = state.copy(selectedPorts = ports)
                }, {}, {}, {}, {}, {}, scanInProgress = false)
            }
        }
        compose.onNodeWithText("Seleccionar puertos").performClick()
        val ports = ScanPolicy.allowedPorts.sorted()
        ports.take(6).forEach { compose.onNodeWithText("Puerto $it").performScrollTo().performClick() }
        compose.onNodeWithText("Puerto ${ports.last()}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Puerto ${ports.first()}").performScrollTo().performClick()
        compose.onNodeWithText("Puerto ${ports.last()}").performScrollTo().performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.runOnIdle { assertEquals(ports.drop(1).toSet(), state.selectedPorts) }
    }
}
