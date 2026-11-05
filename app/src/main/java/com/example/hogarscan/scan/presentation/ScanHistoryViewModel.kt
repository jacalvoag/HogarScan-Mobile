package com.example.hogarscan.scan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

sealed interface ScanDetailUiState {
    data object Closed : ScanDetailUiState
    data object Loading : ScanDetailUiState
    data class Loaded(val record: ScanRecord) : ScanDetailUiState
    data object NotFound : ScanDetailUiState
    data object Error : ScanDetailUiState
}

class ScanHistoryViewModel(
    private val repository: ScanHistoryRepository,
    private val ownerId: suspend () -> String,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ScanHistoryUiState>(ScanHistoryUiState.Loading)
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    private val _detailState =
        MutableStateFlow<ScanDetailUiState>(ScanDetailUiState.Closed)
    val detailState: StateFlow<ScanDetailUiState> = _detailState.asStateFlow()

    private var observationJob: Job? = null
    private var detailJob: Job? = null

    init {
        retry()
        savedStateHandle.get<String>(SELECTED_SCAN_ID)?.let(::openDetail)
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

    fun openDetail(scanId: String) {
        detailJob?.cancel()
        savedStateHandle[SELECTED_SCAN_ID] = scanId
        _detailState.value = ScanDetailUiState.Loading

        detailJob = viewModelScope.launch {
            try {
                val record = repository.findById(scanId, ownerId())
                currentCoroutineContext().ensureActive()
                _detailState.value = if (record == null) {
                    ScanDetailUiState.NotFound
                } else {
                    ScanDetailUiState.Loaded(record)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _detailState.value = ScanDetailUiState.Error
            }
        }
    }

    fun retryDetail() {
        savedStateHandle.get<String>(SELECTED_SCAN_ID)?.let(::openDetail)
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailJob = null
        savedStateHandle[SELECTED_SCAN_ID] = null
        _detailState.value = ScanDetailUiState.Closed
    }

    private companion object {
        const val SELECTED_SCAN_ID = "selected_scan_id"
    }
}