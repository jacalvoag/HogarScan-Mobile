package com.example.hogarscan.auth.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.R
import com.example.hogarscan.auth.domain.model.LogoutOutcome
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginEnviaEntradaExactaYVaciaPassword() {
        var received: Pair<String, String>? = null

        screen(
            onLogin = { email, password ->
                received = email to password
            }
        )

        compose.onNodeWithTag("auth_email").performTextInput(EMAIL)
        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.onNodeWithTag("auth_submit")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals(EMAIL to PASSWORD, received)
        }

        assertEmptyPassword()
    }

    @Test
    fun registroLimpiaPasswordAlCambiarModoYEnviaConfirmacion() {
        var received: List<String>? = null

        screen(
            onRegister = { email, password, confirmation ->
                received = listOf(email, password, confirmation)
            }
        )

        compose.onNodeWithTag("auth_email").performTextInput(EMAIL)
        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput("anterior")

        compose.onNodeWithTag("auth_switch_mode")
            .performScrollTo()
            .performClick()

        assertEmptyPassword()

        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.onNodeWithTag("auth_confirmation")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.onNodeWithTag("auth_submit")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(EMAIL, PASSWORD, PASSWORD),
                received
            )
        }

        assertEmptyPassword()
    }

    @Test
    fun operacionEnCursoDeshabilitaFormulario() {
        screen(
            state = AuthUiState(
                SessionState.Guest,
                AuthOperation.LOGIN
            )
        )

        compose.onNodeWithTag("auth_email").assertIsNotEnabled()
        compose.onNodeWithTag("auth_password").assertIsNotEnabled()
        compose.onNodeWithTag("auth_submit").assertIsNotEnabled()
        compose.onNodeWithTag("auth_switch_mode").assertIsNotEnabled()
    }

    @Test
    fun restauracionNoPresentaFormulario() {
        screen(
            state = AuthUiState(
                SessionState.Restoring,
                AuthOperation.RESTORE
            )
        )

        compose.onNodeWithTag("auth_email").assertDoesNotExist()
        compose.onNodeWithText(
            text(R.string.auth_restoring)
        ).assertIsDisplayed()
    }

    @Test
    fun logoutUsaLaSesionQueSeEstaMostrando() {
        var received: SessionSnapshot? = null

        screen(
            state = AuthUiState(
                SessionState.Authenticated(SESSION)
            ),
            onLogout = { received = it }
        )

        compose.onNodeWithText(EMAIL).assertIsDisplayed()
        compose.onNodeWithTag("auth_password").assertDoesNotExist()

        compose.onNodeWithTag("auth_logout")
            .performScrollTo()
            .performClick()

        compose.runOnIdle {
            assertSame(SESSION, received)
        }
    }

    @Test
    fun cierreLocalNoSePresentaComoRevocacionConfirmada() {
        screen(
            state = AuthUiState(
                session = SessionState.Guest,
                notice = AuthNotice.LoggedOut(
                    LogoutOutcome.REMOTE_UNCONFIRMED
                )
            )
        )

        compose.onNodeWithText(
            text(R.string.auth_logout_unconfirmed)
        ).assertIsDisplayed()

        compose.onNodeWithText(
            text(R.string.auth_logout_confirmed)
        ).assertDoesNotExist()
    }

    @Test
    fun passwordNoRegresaDespuesDeCambiarSesion() {
        val state = mutableStateOf(
            AuthUiState(SessionState.Guest)
        )

        compose.setContent {
            MaterialTheme {
                AuthScreen(
                    state.value,
                    { _, _ -> },
                    { _, _, _ -> },
                    {},
                    {}
                )
            }
        }

        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.runOnIdle {
            state.value = AuthUiState(
                SessionState.Authenticated(SESSION)
            )
        }

        compose.waitForIdle()

        compose.runOnIdle {
            state.value = AuthUiState(SessionState.Guest)
        }

        assertEmptyPassword()
    }

    @Test
    fun correoInvalidoMuestraErrorSinEnviarLogin() {
        var calls = 0

        screen(
            onLogin = { _, _ -> calls++ }
        )

        compose.onNodeWithTag("auth_email")
            .performTextInput("correo-invalido")

        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.onNodeWithTag("auth_submit")
            .performScrollTo()
            .performClick()

        compose.onNodeWithText(
            text(R.string.auth_error_email)
        ).assertExists()

        compose.runOnIdle {
            assertEquals(0, calls)
        }
    }

    @Test
    fun confirmacionDistintaMuestraErrorSinEnviarRegistro() {
        var calls = 0

        screen(
            onRegister = { _, _, _ -> calls++ }
        )

        compose.onNodeWithTag("auth_switch_mode")
            .performScrollTo()
            .performClick()

        compose.onNodeWithTag("auth_email")
            .performScrollTo()
            .performTextInput(EMAIL)

        compose.onNodeWithTag("auth_password")
            .performScrollTo()
            .performTextInput(PASSWORD)

        compose.onNodeWithTag("auth_confirmation")
            .performScrollTo()
            .performTextInput("Otra contraseña")

        compose.onNodeWithTag("auth_submit")
            .performScrollTo()
            .performClick()

        compose.onNodeWithText(
            text(R.string.auth_error_confirmation)
        ).assertExists()

        compose.runOnIdle {
            assertEquals(0, calls)
        }
    }

    private fun screen(
        state: AuthUiState = AuthUiState(SessionState.Guest),
        onLogin: (String, String) -> Unit = { _, _ -> },
        onRegister: (String, String, String) -> Unit = { _, _, _ -> },
        onLogout: (SessionSnapshot) -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                AuthScreen(
                    state,
                    onLogin,
                    onRegister,
                    onLogout,
                    {}
                )
            }
        }
    }

    private fun assertEmptyPassword() {
        compose.onNodeWithTag("auth_password").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("")
            )
        )
    }

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(id)

    companion object {
        private const val EMAIL = "fixture@example.com"
        private const val PASSWORD =
            "  Clave de prueba suficientemente larga  "

        private val SESSION = SessionSnapshot(
            "fixture-context",
            "fixture-user",
            EMAIL,
            10_000L,
            0
        )
    }
}