package com.example.hogarscan.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.repository.RoomScanHistoryRepository
import com.example.hogarscan.scan.data.network.AndroidTcpProbe
import com.example.hogarscan.scan.data.network.AndroidWifiNetworkRepository
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.usecase.ScanCoordinator
import com.example.hogarscan.scan.presentation.WifiViewModel

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: HogarScanDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            HogarScanDatabase::class.java,
            "hogarscan.db"
        ).build()
    }

    val scanHistoryRepository: ScanHistoryRepository by lazy {
        RoomScanHistoryRepository(database.scanRecordDao())
    }

    private val wifiNetworkRepository =
        AndroidWifiNetworkRepository(appContext)

    val tcpProbe: TcpProbe = AndroidTcpProbe(wifiNetworkRepository)

    private val scanCoordinator = ScanCoordinator(wifiNetworkRepository, tcpProbe)

    val wifiViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                WifiViewModel(wifiNetworkRepository, scanCoordinator)
            }
        }
}