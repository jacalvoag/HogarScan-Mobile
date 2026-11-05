package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface ScanHistoryUiState {
    data object Loading : ScanHistoryUiState
    data class Loaded(val records: List<ScanRecordSummary>) : ScanHistoryUiState
    data object Error : ScanHistoryUiState
}

class ScanHistoryViewModel(
    private val repository: ScanHistoryRepository,
    private val ownerId: suspend () -> String
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ScanHistoryUiState>(ScanHistoryUiState.Loading)
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        retry()
    }

    fun retry() {
        observationJob?.cancel()
        _uiState.value = ScanHistoryUiState.Loading
        observationJob = viewModelScope.launch {
            try {
                val owner = ownerId()
                repository.observeByOwner(owner).collect { records ->
                    _uiState.value = ScanHistoryUiState.Loaded(records)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = ScanHistoryUiState.Error
            }
        }
    }
}