package com.example.hogarscan.scan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext
import com.example.hogarscan.scan.domain.usecase.HistoryOwnerResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
    private val owners: HistoryOwnerResolver,
    private val sessions: SessionReader,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ScanHistoryUiState>(ScanHistoryUiState.Loading)
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    private val _detailState =
        MutableStateFlow<ScanDetailUiState>(ScanDetailUiState.Closed)
    val detailState: StateFlow<ScanDetailUiState> = _detailState.asStateFlow()

    private var observationJob: Job? = null
    private var detailJob: Job? = null
    private var expirationJob: Job? = null

    private var restorationPending =
        savedStateHandle.get<String>(SELECTED_SCAN_ID) != null

    init {
        val initialState = sessions.state.value
        watchExpiration(initialState)
        retry()

        viewModelScope.launch {
            var previousState = initialState

            sessions.state.collect { currentState ->
                if (!sameSession(previousState, currentState)) {
                    previousState = currentState

                    // La selección recuperada se validará por propietario.
                    if (!restorationPending) {
                        closeDetail()
                    }

                    watchExpiration(currentState)
                    retry()
                }
            }
        }
    }

    fun retry() {
        observationJob?.cancel()
        _uiState.value = ScanHistoryUiState.Loading
        val expectedState = sessions.state.value

        observationJob = viewModelScope.launch {
            var context: HistoryOwnerContext? = null

            try {
                context = owners.resolve()
                currentCoroutineContext().ensureActive()

                if (!isCurrent(expectedState)) {
                    return@launch
                }

                val owner = context
                if (owner == null) {
                    if (expectedState != SessionState.Restoring) {
                        closeDetail()
                        _uiState.value = ScanHistoryUiState.Error
                    }
                    return@launch
                }

                if (!isCurrent(expectedState, owner)) {
                    return@launch
                }

                if (restorationPending) {
                    restorationPending = false
                    val scanId = savedStateHandle.get<String>(SELECTED_SCAN_ID)
                    val savedOwner =
                        savedStateHandle.get<String>(SELECTED_OWNER_ID)

                    if (scanId != null && savedOwner == owner.ownerId) {
                        loadDetail(scanId, savedOwner)
                    } else {
                        closeDetail()
                    }
                }

                repository.observeByOwner(owner.ownerId).collect { records ->
                    currentCoroutineContext().ensureActive()

                    if (isCurrent(expectedState, owner)) {
                        _uiState.value = ScanHistoryUiState.Loaded(
                            records.filter { it.ownerId == owner.ownerId }
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()

                if (isCurrent(expectedState, context)) {
                    _uiState.value = ScanHistoryUiState.Error
                }
            }
        }
    }

    fun openDetail(scanId: String) {
        loadDetail(scanId, expectedOwner = null)
    }

    private fun loadDetail(scanId: String, expectedOwner: String?) {
        restorationPending = false
        detailJob?.cancel()

        savedStateHandle[SELECTED_SCAN_ID] = scanId
        savedStateHandle[SELECTED_OWNER_ID] = expectedOwner
        _detailState.value = ScanDetailUiState.Loading

        val expectedState = sessions.state.value

        detailJob = viewModelScope.launch {
            var context: HistoryOwnerContext? = null

            try {
                context = owners.resolve()
                currentCoroutineContext().ensureActive()

                if (!isCurrent(expectedState)) {
                    return@launch
                }

                val owner = context
                if (owner == null ||
                    !owners.isCurrent(owner) ||
                    (expectedOwner != null && expectedOwner != owner.ownerId)
                ) {
                    closeDetail()
                    return@launch
                }

                savedStateHandle[SELECTED_OWNER_ID] = owner.ownerId

                val record = repository.findById(scanId, owner.ownerId)
                currentCoroutineContext().ensureActive()

                if (!isCurrent(expectedState, owner)) {
                    return@launch
                }

                _detailState.value = if (
                    record == null ||
                    record.id != scanId ||
                    record.ownerId != owner.ownerId
                ) {
                    ScanDetailUiState.NotFound
                } else {
                    ScanDetailUiState.Loaded(record)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()

                if (isCurrent(expectedState, context)) {
                    _detailState.value = ScanDetailUiState.Error
                }
            }
        }
    }

    fun retryDetail() {
        savedStateHandle.get<String>(SELECTED_SCAN_ID)?.let { scanId ->
            loadDetail(
                scanId,
                savedStateHandle.get<String>(SELECTED_OWNER_ID)
            )
        }
    }

    fun closeDetail() {
        restorationPending = false
        detailJob?.cancel()
        detailJob = null
        savedStateHandle[SELECTED_SCAN_ID] = null
        savedStateHandle[SELECTED_OWNER_ID] = null
        _detailState.value = ScanDetailUiState.Closed
    }

    private fun watchExpiration(state: SessionState) {
        expirationJob?.cancel()

        val session = (state as? SessionState.Authenticated)?.session
            ?: return

        expirationJob = viewModelScope.launch {
            delay((session.expiresAtMillis - nowMillis()).coerceAtLeast(0L))

            if (sameSession(state, sessions.state.value) &&
                session.expiresAtMillis <= nowMillis()
            ) {
                closeDetail()
                retry()
            }
        }
    }

    private fun isCurrent(
        expectedState: SessionState,
        context: HistoryOwnerContext? = null
    ): Boolean =
        sameSession(expectedState, sessions.state.value) &&
                (context == null || owners.isCurrent(context))

    private fun sameSession(
        previous: SessionState,
        current: SessionState
    ): Boolean {
        if (previous is SessionState.Authenticated &&
            current is SessionState.Authenticated
        ) {
            return previous.session.userId == current.session.userId &&
                    previous.session.contextId == current.session.contextId &&
                    previous.session.expiresAtMillis ==
                    current.session.expiresAtMillis
        }

        return previous == current
    }

    private companion object {
        const val SELECTED_SCAN_ID = "selected_scan_id"
        const val SELECTED_OWNER_ID = "selected_scan_owner_id"
    }
}