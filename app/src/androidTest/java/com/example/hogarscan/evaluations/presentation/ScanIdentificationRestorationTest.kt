package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.evaluations.domain.*
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.scan.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanIdentificationRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = ViewModelStore()
    @After fun cleanup() { lifecycle.clear() }

    @Test fun recrearComposicionConservaCamposEIdentificacionesDelViewModel() {
        val session = SessionSnapshot("ctx", "account", "a@example.com", Long.MAX_VALUE, 1)
        val sessions = object : SessionReader {
            override val state = MutableStateFlow<SessionState>(SessionState.Authenticated(session))
        }
        val actions = object : ScanEvaluationActions {
            override suspend fun load(scanId: String, expected: SessionSnapshot) = emptyList<TargetEvaluation>()
            override suspend fun evaluate(scanId: String, expected: SessionSnapshot,
                identifications: Map<Ipv4Address, DeclaredIdentification>, onProgress: (Int, Int) -> Unit): ScanEvaluationRunResult = error("No evaluation expected")
            override suspend fun sync(expected: SessionSnapshot) = error("No sync expected")
            override suspend fun summaryStatus(scanId: String, expected: SessionSnapshot): SummarySyncStatus? = null
            override suspend fun rules(): EvaluationRules? = null
        }
        val address = Ipv4Address.parse("192.168.1.2")
        val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80))
        val record = ScanRecord("scan", "user:account", config, 1, ScanRecordStatus.COMPLETED,
            listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 1)), 2, 1)
        val viewModel = ViewModelProvider(lifecycle, viewModelFactory {
            initializer { ScanEvaluationViewModel(record.id, record.ownerId, sessions, actions) }
        })[ScanEvaluationViewModel::class.java]
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { ScanEvaluationPanel(viewModel, record) }
        } }
        compose.onNodeWithText("IPv4 del objetivo").performScrollTo().performTextInput(address.toString())
        compose.onNodeWithText("Fabricante").performScrollTo().performTextInput("Acme")
        compose.onNodeWithText("Producto").performScrollTo().performTextInput("Router")
        compose.onNodeWithText("Versión").performScrollTo().performTextInput("1")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Acme").assertExists()
        compose.onNodeWithText("Router").assertExists()
        compose.onNodeWithText("Agregar identificación").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Quitar identificación de $address").assertExists()
    }
}
