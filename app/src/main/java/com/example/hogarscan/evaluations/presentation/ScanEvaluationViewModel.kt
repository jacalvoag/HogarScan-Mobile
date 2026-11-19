package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.evaluations.data.EvaluationWorkflow
import com.example.hogarscan.evaluations.data.EvaluationWorkflowResult
import com.example.hogarscan.evaluations.data.local.EvaluationStore
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.scan.data.sync.ScanSummarySyncCoordinator
import com.example.hogarscan.scan.data.sync.SummarySyncRunResult
import com.example.hogarscan.scan.domain.model.Ipv4Address
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanEvaluationUiState {
    data object Loading : ScanEvaluationUiState
    data object Ready : ScanEvaluationUiState
    data class Running(val completed: Int, val total: Int) : ScanEvaluationUiState
    data class Saved(val evaluations: List<TargetEvaluation>, val message: String) : ScanEvaluationUiState
    data class Error(val message: String) : ScanEvaluationUiState
}

class ScanEvaluationViewModel(
    private val scanId: String,
    private val ownerId: String,
    private val sessions: SessionReader,
    private val store: EvaluationStore,
    private val workflow: EvaluationWorkflow,
    private val sync: ScanSummarySyncCoordinator,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow<ScanEvaluationUiState>(ScanEvaluationUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private val mutableIdentification = MutableStateFlow(ScanIdentificationState())
    val identification = mutableIdentification.asStateFlow()
    private var job: Job? = null
    private var sequence = 0L
    private var identity = identity(sessions.state.value)

    init {
        viewModelScope.launch {
            sessions.state.collect { state ->
                val next = identity(state)
                if (identity != next) {
                    identity = next
                    sequence++
                    job?.cancel()
                    mutableIdentification.value = ScanIdentificationState()
                    mutableState.value = ScanEvaluationUiState.Error("La sesión cambió. Vuelve al historial para continuar.")
                }
            }
        }
        load()
    }

    fun load() {
        val session = currentSession() ?: run {
            mutableState.value = ScanEvaluationUiState.Error("Inicia sesión con la cuenta propietaria de esta revisión.")
            return
        }
        val id = ++sequence
        job?.cancel()
        mutableState.value = ScanEvaluationUiState.Loading
        job = viewModelScope.launch {
            try {
                val evaluations = store.load(scanId, session)
                currentCoroutineContext().ensureActive()
                if (id != sequence || !current(session)) return@launch
                mutableState.value = when {
                    evaluations == null -> ScanEvaluationUiState.Error("La sesión cambió.")
                    evaluations.isEmpty() -> ScanEvaluationUiState.Ready
                    else -> ScanEvaluationUiState.Saved(evaluations, "Resultados guardados en este dispositivo.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (id == sequence && current(session)) mutableState.value = ScanEvaluationUiState.Error("No se pudieron cargar las evaluaciones.") }
        }
    }

    fun editIdentification(transform: (ScanIdentificationState) -> ScanIdentificationState) {
        if (mutableState.value != ScanEvaluationUiState.Ready) return
        mutableIdentification.value = transform(mutableIdentification.value).copy(error = null)
    }

    fun toggleIdentificationSource() = editIdentification {
        it.copy(source = if (it.source == IdentificationSource.USER_DEVICE_LABEL)
            IdentificationSource.USER_ADMIN_PANEL else IdentificationSource.USER_DEVICE_LABEL)
    }

    fun addIdentification(allowedAddresses: Collection<Ipv4Address>) {
        if (mutableState.value != ScanEvaluationUiState.Ready) return
        val form = mutableIdentification.value
        val address = runCatching { Ipv4Address.parse(form.address.trim()) }.getOrNull()
        val declared = runCatching {
            DeclaredIdentification(form.vendor, form.product, form.version, form.source)
        }.getOrNull()
        mutableIdentification.value = if (address == null || address !in allowedAddresses || declared == null) {
            form.copy(error = "Revisa la dirección y completa fabricante, producto y versión.")
        } else {
            ScanIdentificationState(source = form.source,
                identifications = form.identifications + (address to declared))
        }
    }

    fun removeIdentification(address: Ipv4Address) = editIdentification {
        it.copy(identifications = it.identifications - address)
    }

    fun start(identifications: Map<Ipv4Address, DeclaredIdentification> = mutableIdentification.value.identifications) {
        if (mutableState.value != ScanEvaluationUiState.Ready || mutableIdentification.value.unfinished) return
        val session = currentSession() ?: run {
            mutableState.value = ScanEvaluationUiState.Error("Inicia sesión con la cuenta propietaria de esta revisión.")
            return
        }
        val id = ++sequence
        job?.cancel()
        mutableState.value = ScanEvaluationUiState.Running(0, 0)
        job = viewModelScope.launch {
            try {
                val outcome = workflow.run(scanId, session, identifications) { done, total ->
                    if (id == sequence && current(session)) mutableState.value = ScanEvaluationUiState.Running(done, total)
                }
                currentCoroutineContext().ensureActive()
                if (id != sequence || !current(session)) return@launch
                mutableState.value = when (outcome) {
                    is EvaluationWorkflowResult.Saved -> ScanEvaluationUiState.Saved(outcome.evaluations, syncMessage(outcome.sync))
                    is EvaluationWorkflowResult.EvaluationFailed -> ScanEvaluationUiState.Error("No se completó la evaluación de ${outcome.result.address}. Puedes reintentar.")
                    EvaluationWorkflowResult.NotFoundOrIncomplete -> ScanEvaluationUiState.Error("La revisión no está completa o pertenece a otra generación.")
                    EvaluationWorkflowResult.AlreadyQueued -> ScanEvaluationUiState.Error("Ya existe un resumen para esta revisión. Vuelve a cargar los resultados.")
                    EvaluationWorkflowResult.SessionChanged -> ScanEvaluationUiState.Error("La sesión cambió durante la evaluación.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (id == sequence && current(session)) mutableState.value = ScanEvaluationUiState.Error("No se pudo completar la evaluación. Inténtalo de nuevo.") }
        }
    }

    fun cancel() {
        sequence++
        job?.cancel()
        mutableState.value = ScanEvaluationUiState.Ready
    }

    fun retrySync() {
        val saved = mutableState.value as? ScanEvaluationUiState.Saved ?: return
        val session = currentSession() ?: return
        val id = ++sequence
        job?.cancel()
        mutableState.value = ScanEvaluationUiState.Loading
        job = viewModelScope.launch {
            try {
                val outcome = sync.syncPending(session)
                if (id == sequence && current(session)) mutableState.value = saved.copy(message = syncMessage(outcome))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (id == sequence && current(session)) mutableState.value = saved.copy(message = "No se pudo sincronizar. Los datos siguen guardados.") }
        }
    }

    private fun currentSession(): SessionSnapshot? =
        (sessions.state.value as? SessionState.Authenticated)?.session?.takeIf {
            ownerId == "user:${it.userId}" && sessions.isCurrentSession(it, nowMillis())
        }
    private fun current(expected: SessionSnapshot): Boolean = currentSession()?.let {
        it.contextId == expected.contextId && it.userId == expected.userId && it.summaryGeneration == expected.summaryGeneration
    } == true

    private companion object {
        fun identity(state: SessionState): Triple<String, String, Int>? =
            (state as? SessionState.Authenticated)?.session?.let { Triple(it.userId, it.contextId, it.summaryGeneration) }
        fun syncMessage(result: SummarySyncRunResult): String = when (result) {
            SummarySyncRunResult.Completed -> "Evaluación guardada y sincronización procesada."
            SummarySyncRunResult.Busy -> "Evaluación guardada; la sincronización ya está en curso."
            else -> "Evaluación guardada. La sincronización quedó pendiente o requiere atención."
        }
    }
}
