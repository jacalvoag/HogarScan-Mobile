package com.example.hogarscan.scan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.evaluations.presentation.FindingDetail
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.usecase.DeleteLocalScan
import com.example.hogarscan.scan.domain.usecase.LocalScanDeletion
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
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val deleteLocal: DeleteLocalScan? = null
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ScanHistoryUiState>(ScanHistoryUiState.Loading)
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    private val _detailState =
        MutableStateFlow<ScanDetailUiState>(ScanDetailUiState.Closed)
    val detailState: StateFlow<ScanDetailUiState> = _detailState.asStateFlow()

    // El ViewModel conserva el nivel de navegación durante una recreación de la Activity.
    // El contenido del hallazgo no se serializa en el Bundle de estado de Android.
    private val _selectedFinding = MutableStateFlow<FindingDetail?>(null)
    val selectedFinding: StateFlow<FindingDetail?> = _selectedFinding.asStateFlow()

    fun openFinding(scanId: String, ownerId: String, detail: FindingDetail) {
        val record = (_detailState.value as? ScanDetailUiState.Loaded)?.record ?: return
        if (record.id == scanId && record.ownerId == ownerId) {
            _selectedFinding.value = detail
        }
    }

    fun closeFinding() {
        _selectedFinding.value = null
    }

    data class DeletionState(val confirmation: ScanRecord? = null, val pending: Boolean = false, val message: String? = null)
    private val mutableDeletion = MutableStateFlow(DeletionState())
    val deletion = mutableDeletion.asStateFlow()
    private var deletionOwner: HistoryOwnerContext? = null
    private var deletionJob: Job? = null

    fun requestDeletion() {
        if (mutableDeletion.value.pending || deleteLocal == null) return
        val record = (_detailState.value as? ScanDetailUiState.Loaded)?.record ?: return
        if (record.status == com.example.hogarscan.scan.domain.model.ScanRecordStatus.RUNNING) return
        viewModelScope.launch {
            val owner = owners.resolve() ?: return@launch
            if (owners.isCurrent(owner) && owner.ownerId == record.ownerId &&
                (_detailState.value as? ScanDetailUiState.Loaded)?.record?.id == record.id) {
                deletionOwner = owner
                mutableDeletion.value = DeletionState(confirmation = record)
            }
        }
    }

    fun cancelDeletion() {
        if (!mutableDeletion.value.pending) { deletionOwner = null; mutableDeletion.value = DeletionState() }
    }

    fun confirmDeletion() {
        val state = mutableDeletion.value
        val record = state.confirmation ?: return
        val owner = deletionOwner ?: return
        val action = deleteLocal ?: return
        if (state.pending || !owners.isCurrent(owner)) { cancelDeletion(); return }
        mutableDeletion.value = DeletionState(pending = true)
        deletionJob = viewModelScope.launch {
            try {
                val result = action.delete(record.id, owner)
                currentCoroutineContext().ensureActive()
                if (!owners.isCurrent(owner)) return@launch
                if (result == LocalScanDeletion.DELETED || result == LocalScanDeletion.NOT_FOUND) {
                    closeDetail()
                    mutableDeletion.value = DeletionState(message = "Revisión eliminada del teléfono. Los resúmenes del servidor permanecen.")
                } else {
                    mutableDeletion.value = DeletionState(message = "No se borró la revisión: está en curso o cambió la sesión.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owners.isCurrent(owner)) mutableDeletion.value = DeletionState(message = "No se pudo borrar la revisión.")
            }
        }
    }

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
                    deletionJob?.cancel()
                    deletionOwner = null
                    mutableDeletion.value = DeletionState()

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
        closeFinding()
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
        closeFinding()
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
