package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.scan.domain.model.AuthorizedScan
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import com.example.hogarscan.scan.domain.usecase.ScanCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WifiViewModel(
    private val repository: WifiNetworkRepository,
    private val coordinator: ScanCoordinator
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WifiUiState>(WifiUiState.Idle)
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private val setup = ScanSetupController()
    private val _setupState = MutableStateFlow(setup.state)
    val setupState: StateFlow<ScanSetupState> = _setupState.asStateFlow()

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private var observationJob: Job? = null
    private var queryJob: Job? = null
    private var scanJob: Job? = null
    private var activeRequest: AuthorizedScan? = null
    private var stopReason: StopReason? = null

    private enum class StopReason { USER, NETWORK }

    init {
        startObserving()
    }

    fun updateStartAddress(value: String) {
        if (_scanState.value.isActive) return
        setup.updateStartAddress(value)
        publishSetup()
    }

    fun updateEndAddress(value: String) {
        if (_scanState.value.isActive) return
        setup.updateEndAddress(value)
        publishSetup()
    }

    fun togglePort(port: Int) {
        if (_scanState.value.isActive) return
        setup.togglePort(port)
        publishSetup()
    }

    fun reviewConfiguration() {
        if (_scanState.value.isActive) return
        setup.reviewConfiguration()
        publishSetup()
    }

    fun dismissConfirmation() {
        setup.dismissConfirmation()
        publishSetup()
    }

    fun confirmAuthorization() {
        if (_scanState.value.isActive ||
            setup.state.confirmation == null ||
            setup.state.checkingNetwork
        ) return

        reload(confirmAfterRead = true)
    }

    fun revokeAuthorization() {
        if (_scanState.value.isActive) {
            cancelScan()
            return
        }
        setup.revokeAuthorization()
        publishSetup()
    }

    fun startScan() {
        if (_scanState.value.isActive) return

        val request = setup.takeAuthorizedScan()
        publishSetup()
        if (request == null) return
        activeRequest = request
        stopReason = null
        _scanState.value = ScanUiState(
            status = ScanStatus.STARTING,
            config = request.config,
            message = "Comprobando la conexión autorizada…"
        )

        // Instala los manejadores antes de que pueda llegar una cancelación.
        scanJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                coordinator.execute(
                    request = request,
                    onStarted = {
                        if (_scanState.value.status == ScanStatus.STARTING) {
                            _scanState.update {
                                it.copy(
                                    status = ScanStatus.RUNNING,
                                    message = null
                                )
                            }
                        }
                    },
                    onResult = { result ->
                        _scanState.update {
                            it.copy(results = it.results + result)
                        }
                    }
                )
                _scanState.update {
                    it.copy(
                        status = ScanStatus.COMPLETED,
                        message = null
                    )
                }
            } catch (exception: CancellationException) {
                finishStoppedScan()
                throw exception
            } catch (_: ScanConnectionChangedException) {
                if (stopReason == StopReason.USER) {
                    finishStoppedScan()
                } else {
                    _scanState.update {
                        it.copy(
                            status = ScanStatus.INTERRUPTED,
                            message = "La conexión cambió. Se conservan los resultados parciales."
                        )
                    }
                }
            } catch (_: Exception) {
                _scanState.update {
                    it.copy(
                        status = ScanStatus.FAILED,
                        message = "No se pudo completar la revisión. Se conservan los resultados parciales."
                    )
                }
            } finally {
                request.revoke()
                activeRequest = null
                stopReason = null
            }
        }
    }

    fun cancelScan() {
        stopScan(StopReason.USER)
    }

    fun refresh() {
        startObserving()
        if (_uiState.value == WifiUiState.Loading) return
        reload()
    }

    private fun stopScan(reason: StopReason) {
        if (!_scanState.value.isActive) return
        if (stopReason == null) stopReason = reason
        activeRequest?.revoke()
        _scanState.update {
            it.copy(
                status = ScanStatus.CANCELLING,
                message = "Deteniendo la revisión…"
            )
        }
        scanJob?.cancel()
    }

    private fun finishStoppedScan() {
        val networkChanged = stopReason == StopReason.NETWORK
        _scanState.update {
            it.copy(
                status = if (networkChanged) {
                    ScanStatus.INTERRUPTED
                } else {
                    ScanStatus.CANCELLED
                },
                message = if (networkChanged) {
                    "La conexión cambió. Se conservan los resultados parciales."
                } else {
                    "Revisión cancelada. Se conservan los resultados parciales."
                }
            )
        }
    }

    private fun startObserving() {
        if (observationJob?.isActive == true) return

        observationJob = viewModelScope.launch {
            try {
                repository.observeNetworkChanges().collect {
                    reload()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                queryJob?.cancel()
                applyNetworkResult(
                    WifiNetworkResult.Unavailable(
                        WifiNetworkResult.Reason.NETWORK_INFO_UNAVAILABLE
                    )
                )
                _uiState.value = WifiUiState.Error
            }
        }
    }

    private fun reload(confirmAfterRead: Boolean = false) {
        queryJob?.cancel()
        setup.onNetworkChecking()
        publishSetup()
        _uiState.value = WifiUiState.Loading

        queryJob = viewModelScope.launch {
            val nextState: WifiUiState = try {
                WifiUiState.Loaded(repository.getCurrentNetwork())
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                WifiUiState.Error
            }

            currentCoroutineContext().ensureActive()
            val result = (nextState as? WifiUiState.Loaded)?.result
                ?: WifiNetworkResult.Unavailable(
                    WifiNetworkResult.Reason.NETWORK_INFO_UNAVAILABLE
                )
            applyNetworkResult(result)

            if (confirmAfterRead && !_scanState.value.isActive) {
                setup.confirmAuthorization()
                publishSetup()
            }

            _uiState.value = nextState
        }
    }

    private fun applyNetworkResult(result: WifiNetworkResult) {
        setup.onNetworkResult(result)
        val request = activeRequest
        if (request != null && !request.validate(result)) {
            stopScan(StopReason.NETWORK)
        }
        publishSetup()
    }

    private fun publishSetup() {
        _setupState.value = setup.state
    }
}
