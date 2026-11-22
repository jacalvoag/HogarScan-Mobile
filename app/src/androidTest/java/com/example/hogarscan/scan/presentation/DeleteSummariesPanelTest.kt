package com.example.hogarscan.scan.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesAction
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesOutcome
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.After
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteSummariesPanelTest {
    @get:Rule val compose = createComposeRule()
    private val session = SessionSnapshot("ctx", "user", "a@example.com", Long.MAX_VALUE, 2)
    private val sessions = object : SessionReader { override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session)) }
    private val lifecycle = ViewModelStore()
    @After fun cleanup() { lifecycle.clear() }

    @Test fun cancelarNoEnviaBorrado() {
        var calls = 0
        val action = DeleteSummariesAction { calls++; DeleteSummariesOutcome.UNCONFIRMED }
        val viewModel = ViewModelProvider(lifecycle, viewModelFactory {
            initializer { DeleteSummariesViewModel(sessions, action) }
        })[DeleteSummariesViewModel::class.java]
        compose.setContent { MaterialTheme { DeleteSummariesPanel(viewModel) } }
        compose.onNodeWithText("Borrar resúmenes del servidor").performClick()
        compose.onNodeWithText("¿Borrar resúmenes del servidor?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
