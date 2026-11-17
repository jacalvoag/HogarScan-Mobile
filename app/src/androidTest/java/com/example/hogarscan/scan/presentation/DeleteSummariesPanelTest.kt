package com.example.hogarscan.scan.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.data.sync.DeleteScanSummaries
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.shared.data.http.ApiResult
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
    private val repository = object : ScanSummaryRepository {
        override suspend fun enqueue(summary: FrozenScanSummary) = false
        override suspend fun findPending(userId: String, generation: Int) = emptyList<FrozenScanSummary>()
        override suspend fun markSynced(summary: FrozenScanSummary, receivedAtMillis: Long) = false
        override suspend fun markConflict(summary: FrozenScanSummary) = false
        override suspend fun markOlderPendingObsolete(userId: String, currentGeneration: Int) = 0
    }

    @Test fun cancelarNoEnviaBorrado() {
        var calls = 0
        val action = DeleteScanSummaries(SessionApiClient { _, _ -> calls++; ApiResult.NetworkFailure }, repository) { _, _ -> true }
        compose.setContent { MaterialTheme { DeleteSummariesPanel(sessions, action) } }
        compose.onNodeWithText("Borrar resúmenes del servidor").performClick()
        compose.onNodeWithText("¿Borrar resúmenes del servidor?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
