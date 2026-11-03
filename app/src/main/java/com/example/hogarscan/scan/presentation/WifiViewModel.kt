package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class WifiViewModel(
    private val repository: WifiNetworkRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WifiUiState>(WifiUiState.Idle)
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private val setup = ScanSetupController()
    private val _setupState = MutableStateFlow(setup.state)
    val setupState: StateFlow<ScanSetupState> = _setupState.asStateFlow()

    private var observationJob: Job? = null
    private var queryJob: Job? = null

    init {
        startObserving()
    }

    fun updateStartAddress(value: String) {
        setup.updateStartAddress(value)
        publishSetup()
    }

    fun updateEndAddress(value: String) {
        setup.updateEndAddress(value)
        publishSetup()
    }

    fun togglePort(port: Int) {
        setup.togglePort(port)
        publishSetup()
    }

    fun reviewConfiguration() {
        setup.reviewConfiguration()
        publishSetup()
    }

    fun dismissConfirmation() {
        setup.dismissConfirmation()
        publishSetup()
    }

    fun confirmAuthorization() {
        if (setup.state.confirmation == null ||
            setup.state.checkingNetwork
        ) return

        // Se comprueba de nuevo la red antes de aceptar la confirmación.
        reload(confirmAfterRead = true)
    }

    fun revokeAuthorization() {
        setup.revokeAuthorization()
        publishSetup()
    }

    fun refresh() {
        startObserving()
        if (_uiState.value == WifiUiState.Loading) return
        reload()
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
                setup.onNetworkResult(
                    WifiNetworkResult.Unavailable(
                        WifiNetworkResult.Reason.NETWORK_INFO_UNAVAILABLE
                    )
                )
                publishSetup()
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
            setup.onNetworkResult(result)

            if (confirmAfterRead) {
                setup.confirmAuthorization()
            }

            publishSetup()
            _uiState.value = nextState
        }
    }

    private fun publishSetup() {
        _setupState.value = setup.state
    }
}
