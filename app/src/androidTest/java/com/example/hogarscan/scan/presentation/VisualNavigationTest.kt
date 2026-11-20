package com.example.hogarscan.scan.presentation

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun configuracionConservaDestinoAlGirarYRegresaAlInicio() {
        compose.onNodeWithText("Configurar revisión").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nueva revisión").assertIsDisplayed()
        compose.onNodeWithText("Historial").assertDoesNotExist()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.onNodeWithText("Nueva revisión").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Configurar revisión").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Historial").assertIsDisplayed()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
