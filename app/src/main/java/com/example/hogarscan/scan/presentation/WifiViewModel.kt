package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private var observationJob: Job? = null
    private var queryJob: Job? = null

    init {
        startObserving()
    }

    fun refresh() {
        startObserving()

        if (_uiState.value == WifiUiState.Loading) {
            return
        }

        reload()
    }

    private fun startObserving() {
        if (observationJob?.isActive == true) {
            return
        }

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
                _uiState.value = WifiUiState.Error
            }
        }
    }

    private fun reload() {
        queryJob?.cancel()
        _uiState.value = WifiUiState.Loading

        queryJob = viewModelScope.launch {
            val nextState: WifiUiState = try {
                WifiUiState.Loaded(
                    repository.getCurrentNetwork()
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                WifiUiState.Error
            }

            currentCoroutineContext().ensureActive()
            _uiState.value = nextState
        }
    }
}