package com.example.hogarscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hogarscan.app.HogarScanApplication
import com.example.hogarscan.scan.presentation.ScanHistoryRoute
import com.example.hogarscan.scan.presentation.ScanHistoryViewModel
import com.example.hogarscan.scan.presentation.WifiRoute
import com.example.hogarscan.scan.presentation.WifiViewModel
import com.example.hogarscan.ui.theme.HogarScanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container =
            (application as HogarScanApplication).container

        setContent {
            val wifiViewModel: WifiViewModel = viewModel(
                factory = container.wifiViewModelFactory
            )

            var selectedTab by rememberSaveable { mutableStateOf(0) }

            HogarScanTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Red Wi-Fi") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Historial") }
                            )
                        }
                    }
                ) { innerPadding ->
                    if (selectedTab == 0) {
                        WifiRoute(
                            viewModel = wifiViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        val historyViewModel: ScanHistoryViewModel = viewModel(
                            factory = container.scanHistoryViewModelFactory
                        )
                        ScanHistoryRoute(
                            viewModel = historyViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}