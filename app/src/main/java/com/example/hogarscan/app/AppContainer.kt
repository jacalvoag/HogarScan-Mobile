package com.example.hogarscan.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.data.network.AndroidWifiNetworkRepository
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import com.example.hogarscan.scan.presentation.WifiViewModel

class AppContainer(context: Context) {

    private val wifiNetworkRepository: WifiNetworkRepository =
        AndroidWifiNetworkRepository(context.applicationContext)

    val wifiViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                WifiViewModel(wifiNetworkRepository)
            }
        }
}