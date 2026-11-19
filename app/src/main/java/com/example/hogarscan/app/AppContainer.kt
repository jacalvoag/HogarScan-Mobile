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
import com.example.hogarscan.scan.data.local.HogarScanMigrations.MIGRATION_1_2
import com.example.hogarscan.scan.data.local.HogarScanMigrations.MIGRATION_2_3
import com.example.hogarscan.scan.data.local.repository.RoomScanSummaryRepository
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.scan.data.http.HttpScanSummaryDataSource
import com.example.hogarscan.scan.data.sync.ScanSummarySyncCoordinator
import com.example.hogarscan.scan.data.sync.DeleteSummariesResult
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesAction
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesOutcome
import com.example.hogarscan.scan.presentation.DeleteSummariesViewModel
import com.example.hogarscan.scan.data.sync.DeleteScanSummaries
import com.example.hogarscan.evaluations.data.DefaultScanEvaluationActions
import com.example.hogarscan.scan.domain.model.SummarySyncStatus
import com.example.hogarscan.evaluations.data.DefaultEvaluationGateway
import com.example.hogarscan.evaluations.data.HttpEvaluationDataSource
import com.example.hogarscan.evaluations.data.HttpRulesDataSource
import com.example.hogarscan.evaluations.data.EvaluationWorkflow
import com.example.hogarscan.evaluations.data.local.RoomEvaluationStore
import com.example.hogarscan.evaluations.domain.EvaluateCompletedScan
import com.example.hogarscan.evaluations.presentation.ScanEvaluationViewModel
import com.example.hogarscan.statistics.data.HttpStatisticsGateway
import com.example.hogarscan.statistics.presentation.StatisticsViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


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

    val scanSummaryDataSource by lazy {
        HttpScanSummaryDataSource(sessionApiClient)
    }

    val scanSummarySyncCoordinator by lazy {
        ScanSummarySyncCoordinator(
            repository = scanSummaryRepository,
            sessions = sessionReader,
            send = scanSummaryDataSource::send,
            updateGeneration = sessionStore::updateSummaryGeneration
        )
    }

    val deleteScanSummaries by lazy {
        DeleteScanSummaries(sessionApiClient, scanSummaryRepository, sessionStore::updateSummaryGeneration)
    }

    val deleteSummariesViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DeleteSummariesViewModel(sessionReader, DeleteSummariesAction { expected ->
                when (deleteScanSummaries.delete(expected)) {
                    is DeleteSummariesResult.Deleted -> DeleteSummariesOutcome.DELETED
                    is DeleteSummariesResult.AlreadyAdvanced -> DeleteSummariesOutcome.ALREADY_ADVANCED
                    DeleteSummariesResult.NewConfirmationRequired -> DeleteSummariesOutcome.NEW_CONFIRMATION_REQUIRED
                    DeleteSummariesResult.SessionChanged -> DeleteSummariesOutcome.SESSION_CHANGED
                    is DeleteSummariesResult.Failure -> DeleteSummariesOutcome.UNCONFIRMED
                }
            })
        }
    }

    val evaluationStore by lazy { RoomEvaluationStore(database, sessionStore) }
    val evaluationRules by lazy { HttpRulesDataSource(apiTransport) }

    val statisticsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            StatisticsViewModel(
                sessionReader,
                HttpStatisticsGateway(sessionApiClient),
                reconcileGeneration = { expected, generation ->
                    withContext(NonCancellable) {
                        if (sessionStore.updateSummaryGeneration(expected, generation)) {
                            scanSummaryRepository.markOlderPendingObsolete(expected.userId, generation)
                            true
                        } else false
                    }
                }
            )
        }
    }

    private val evaluationGateway by lazy {
        DefaultEvaluationGateway(HttpEvaluationDataSource(sessionApiClient))
    }

    val evaluationWorkflow by lazy {
        EvaluationWorkflow(
            sessions = sessionReader,
            history = scanHistoryRepository,
            evaluator = EvaluateCompletedScan(sessionReader, evaluationGateway),
            save = evaluationStore::saveCompleted,
            sync = { session -> scanSummarySyncCoordinator.syncPending(session) }
        )
    }

    fun scanEvaluationViewModelFactory(scanId: String, ownerId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ScanEvaluationViewModel(scanId, ownerId, sessionReader,
                    DefaultScanEvaluationActions(evaluationStore, evaluationWorkflow, scanSummarySyncCoordinator,
                        readStatus = { id, expected ->
                            database.scanSummaryDao().findById(id, expected.userId.lowercase())?.let {
                                if (it.summaryGeneration < expected.summaryGeneration) SummarySyncStatus.OBSOLETE
                                else it.syncStatus
                            }
                        }, rulesSource = evaluationRules))
            }
        }

    private val database: HogarScanDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            HogarScanDatabase::class.java,
            "hogarscan.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    val scanHistoryRepository: ScanHistoryRepository by lazy {
        RoomScanHistoryRepository(database.scanRecordDao())
    }

    val scanSummaryRepository: ScanSummaryRepository by lazy {
        RoomScanSummaryRepository(database.scanSummaryDao())
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
                ownerContext = {
                    checkNotNull(historyOwnerResolver.resolve()) {
                        "No se puede iniciar la revisión sin un propietario disponible."
                    }
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
                    owners = historyOwnerResolver,
                    sessions = sessionReader,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
}
