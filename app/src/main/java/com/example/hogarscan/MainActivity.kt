package com.example.hogarscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hogarscan.app.HogarScanApplication
import com.example.hogarscan.auth.presentation.AuthRoute
import com.example.hogarscan.auth.presentation.AccountPage
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.presentation.AuthViewModel
import com.example.hogarscan.scan.presentation.DeleteSummariesPanel
import com.example.hogarscan.scan.presentation.DeleteSummariesViewModel
import com.example.hogarscan.scan.presentation.ScanDetailUiState
import com.example.hogarscan.scan.presentation.ScanHistoryRoute
import com.example.hogarscan.scan.presentation.ScanHistoryUiState
import com.example.hogarscan.scan.presentation.ScanHistoryViewModel
import com.example.hogarscan.scan.presentation.WifiPage
import com.example.hogarscan.scan.presentation.WifiRoute
import com.example.hogarscan.scan.presentation.WifiViewModel
import com.example.hogarscan.statistics.presentation.StatisticsRoute
import com.example.hogarscan.statistics.presentation.StatisticsViewModel
import com.example.hogarscan.ui.navigation.HogarBottomNavigation
import com.example.hogarscan.ui.navigation.RootDestination
import com.example.hogarscan.ui.theme.HogarScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HogarScanApplication).container
        setContent {
            val wifi: WifiViewModel = viewModel(factory = container.wifiViewModelFactory)
            val auth: AuthViewModel = viewModel(factory = container.authViewModelFactory)
            val history: ScanHistoryViewModel = viewModel(factory = container.scanHistoryViewModelFactory)
            val historyState by history.uiState.collectAsStateWithLifecycle()
            val historyDetail by history.detailState.collectAsStateWithLifecycle()
            val session by container.sessionReader.state.collectAsStateWithLifecycle()
            var destination by rememberSaveable { mutableStateOf(RootDestination.Home) }
            var wifiPage by rememberSaveable { mutableStateOf(WifiPage.Home) }
            var accountPage by rememberSaveable { mutableStateOf(AccountPage.Account) }
            var summaryOpen by rememberSaveable { mutableStateOf(false) }
            val showBottom = when (destination) {
                RootDestination.Home -> wifiPage == WifiPage.Home
                RootDestination.History -> historyDetail == ScanDetailUiState.Closed
                RootDestination.Account -> accountPage == AccountPage.Account
                RootDestination.Statistics -> !summaryOpen
            }
            HogarScanTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottom) HogarBottomNavigation(destination) { destination = it }
                    }
                ) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                        .consumeWindowInsets(innerPadding).statusBarsPadding()
                    when (destination) {
                        RootDestination.Home -> WifiRoute(wifi, wifiPage, { wifiPage = it }, historyState,
                            onOpenReview = { id ->
                                history.openDetail(id)
                                destination = RootDestination.History
                            }, modifier = contentModifier,
                            guest = session == SessionState.Guest,
                            onLogin = { accountPage = AccountPage.Login; destination = RootDestination.Account },
                            onRegister = { accountPage = AccountPage.Register; destination = RootDestination.Account },
                            onHistory = { destination = RootDestination.History })
                        RootDestination.History -> ScanHistoryRoute(history, modifier = contentModifier,
                            evaluationFactory = container::scanEvaluationViewModelFactory)
                        RootDestination.Account -> {
                            val delete: DeleteSummariesViewModel = viewModel(factory = container.deleteSummariesViewModelFactory)
                            AuthRoute(auth, contentModifier, accountPage, { accountPage = it },
                                localCount = (historyState as? ScanHistoryUiState.Loaded)?.records?.size,
                                onHistory = { destination = RootDestination.History },
                                deleteContent = { DeleteSummariesPanel(delete) })
                        }
                        RootDestination.Statistics -> {
                            val statistics: StatisticsViewModel = viewModel(factory = container.statisticsViewModelFactory)
                            StatisticsRoute(statistics, modifier = contentModifier,
                                onLogin = {
                                    accountPage = AccountPage.Login
                                    destination = RootDestination.Account
                                },
                                onHistory = { destination = RootDestination.History },
                                onSummaryOpenChange = { summaryOpen = it })
                        }
                    }
                }
            }
        }
    }
}
