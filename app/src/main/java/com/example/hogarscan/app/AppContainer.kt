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
import com.example.hogarscan.scan.data.local.LocalHistoryOwner
import com.example.hogarscan.scan.domain.usecase.ScanHistoryRecorder
import com.example.hogarscan.scan.presentation.ScanHistoryViewModel
import androidx.lifecycle.createSavedStateHandle
import com.example.hogarscan.BuildConfig
import com.example.hogarscan.auth.data.http.DefaultSessionApiClient
import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.data.session.AndroidSessionStorage
import com.example.hogarscan.auth.data.session.PersistentSessionStore
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.shared.data.http.ApiEndpoint
import com.example.hogarscan.shared.data.http.ApiTransport
import com.example.hogarscan.shared.data.http.OkHttpApiTransport
import com.example.hogarscan.shared.data.http.createApiHttpClient
import com.example.hogarscan.auth.data.http.HttpAuthDataSource
import com.example.hogarscan.auth.data.repository.DefaultAuthRepository
import com.example.hogarscan.auth.domain.repository.AuthRepository
import com.example.hogarscan.auth.presentation.AuthViewModel
import com.example.hogarscan.scan.domain.usecase.HistoryOwnerResolver


class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val sessionStorage by lazy {
        AndroidSessionStorage(appContext)
    }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            remote = HttpAuthDataSource(apiTransport),
            sessions = sessionStore
        )
    }

    val authViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AuthViewModel(
                    repository = authRepository,
                    sessions = sessionReader
                )
            }
        }

    val sessionStore by lazy {
        PersistentSessionStore(sessionStorage)
    }

    val sessionReader: SessionReader
        get() = sessionStore

    val apiTransport: ApiTransport by lazy {
        OkHttpApiTransport(
            endpoint = ApiEndpoint.parse(BuildConfig.API_BASE_URL),
            callFactory = createApiHttpClient()
        )
    }

    val sessionApiClient: SessionApiClient by lazy {
        DefaultSessionApiClient(
            transport = apiTransport,
            sessions = sessionStore
        )
    }

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

    val localHistoryOwner by lazy { LocalHistoryOwner(appContext) }

    private val historyOwnerResolver by lazy {
        HistoryOwnerResolver(
            sessions = sessionReader,
            guestOwnerId = localHistoryOwner::getOwnerId
        )
    }

    private val scanCoordinator by lazy {
        ScanCoordinator(
            wifiNetworkRepository,
            tcpProbe,
            historyRecorder = ScanHistoryRecorder(
                repository = scanHistoryRepository,
                ownerId = {
                    checkNotNull(historyOwnerResolver.resolve()) {
                        "No se puede iniciar la revisión sin un propietario disponible."
                    }.ownerId
                }
            )
        )
    }

    val wifiViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                WifiViewModel(wifiNetworkRepository, scanCoordinator)
            }
        }

    val scanHistoryViewModelFactory: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ScanHistoryViewModel(
                    repository = scanHistoryRepository,
                    ownerId = localHistoryOwner::getOwnerId,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
}