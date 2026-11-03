package com.example.hogarscan.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.scan.data.network.AndroidWifiNetworkRepository
import com.example.hogarscan.scan.data.network.AndroidTcpProbe
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.usecase.ScanCoordinator
import com.example.hogarscan.scan.presentation.WifiViewModel

class AppContainer(context: Context) {

    private val wifiNetworkRepository =
        AndroidWifiNetworkRepository(context.applicationContext)

    val tcpProbe: TcpProbe = AndroidTcpProbe(wifiNetworkRepository)

    private val scanCoordinator = ScanCoordinator(wifiNetworkRepository, tcpProbe)

    val wifiViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                WifiViewModel(wifiNetworkRepository, scanCoordinator)
            }
        }
}
