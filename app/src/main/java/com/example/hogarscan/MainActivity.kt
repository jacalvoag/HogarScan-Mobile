package com.example.hogarscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hogarscan.app.HogarScanApplication
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

            HogarScanTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    WifiRoute(
                        viewModel = wifiViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}