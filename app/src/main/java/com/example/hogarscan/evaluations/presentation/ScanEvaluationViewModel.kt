package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.evaluations.domain.EvaluationSyncResult
import com.example.hogarscan.evaluations.domain.ScanEvaluationActions
import com.example.hogarscan.evaluations.domain.ScanEvaluationRunResult
import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.scan.domain.model.SummarySyncStatus
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
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
    data class Saved(val evaluations: List<TargetEvaluation>, val message: String, val canRetrySync: Boolean = false) : ScanEvaluationUiState
    data class Error(val message: String) : ScanEvaluationUiState
}

data class EvaluationRulesUiState(
    val loading: Boolean = false,
    val rules: EvaluationRules? = null,
    val error: Boolean = false
)

class ScanEvaluationViewModel(
    private val scanId: String,
    private val ownerId: String,
    private val sessions: SessionReader,
    private val actions: ScanEvaluationActions,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow<ScanEvaluationUiState>(ScanEvaluationUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private val mutableIdentification = MutableStateFlow(ScanIdentificationState())
    val identification = mutableIdentification.asStateFlow()
    private val mutableRules = MutableStateFlow(EvaluationRulesUiState())
    val rules = mutableRules.asStateFlow()
    private var job: Job? = null
    private var sequence = 0L
    private var rulesJob: Job? = null
    private var generationRefreshRequired = false
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
                    generationRefreshRequired = false
                    rulesJob?.cancel()
                    mutableRules.value = EvaluationRulesUiState()
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
                val evaluations = actions.load(scanId, session)
                val savedState = if (!evaluations.isNullOrEmpty()) savedState(evaluations, session) else null
                currentCoroutineContext().ensureActive()
                if (id != sequence || !current(session)) return@launch
                mutableState.value = when {
                    evaluations == null -> ScanEvaluationUiState.Error("La sesión cambió.")
                    evaluations.isEmpty() -> ScanEvaluationUiState.Ready
                    else -> requireNotNull(savedState)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (id == sequence && current(session)) mutableState.value = ScanEvaluationUiState.Error("No se pudieron cargar las evaluaciones.") }
        }
    }

    fun loadRules() {
        if (mutableRules.value.loading) return
        val id = identity
        mutableRules.value = mutableRules.value.copy(loading = true, error = false)
        rulesJob = viewModelScope.launch {
            try {
                val result = actions.rules()
                currentCoroutineContext().ensureActive()
                if (identity == id) mutableRules.value = EvaluationRulesUiState(rules = result, error = result == null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (identity == id) mutableRules.value = EvaluationRulesUiState(error = true)
            }
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

    fun useObservedIdentification(address: Ipv4Address, identification: DeclaredIdentification) = editIdentification {
        require(identification.source == IdentificationSource.SERVICE_RESPONSE)
        ScanIdentificationState(identifications = it.identifications + (address to identification))
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
                val outcome = actions.evaluate(scanId, session, identifications.toMap()) { done, total ->
                    if (id == sequence && current(session)) mutableState.value = ScanEvaluationUiState.Running(done, total)
                }
                currentCoroutineContext().ensureActive()
                if (id != sequence || !current(session)) return@launch
                val nextState = when (outcome) {
                    is ScanEvaluationRunResult.Saved -> savedState(outcome.evaluations, session, outcome.sync)
                    is ScanEvaluationRunResult.EvaluationFailed -> ScanEvaluationUiState.Error("No se completó la evaluación de ${outcome.address}. Puedes reintentar.")
                    ScanEvaluationRunResult.NotFoundOrIncomplete -> ScanEvaluationUiState.Error("La revisión no está completa o pertenece a otra generación.")
                    ScanEvaluationRunResult.AlreadyQueued -> ScanEvaluationUiState.Error("Ya existe un resumen para esta revisión. Vuelve a cargar los resultados.")
                    ScanEvaluationRunResult.SessionChanged -> ScanEvaluationUiState.Error("La sesión cambió durante la evaluación.")
                }
                currentCoroutineContext().ensureActive()
                if (id == sequence && current(session)) mutableState.value = nextState
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
        if (!saved.canRetrySync) return
        val session = currentSession() ?: return
        val id = ++sequence
        job?.cancel()
        mutableState.value = ScanEvaluationUiState.Loading
        job = viewModelScope.launch {
            try {
                val outcome = actions.sync(session)
                val updated = savedState(saved.evaluations, session, outcome)
                if (id == sequence && current(session)) mutableState.value = updated
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (id == sequence && current(session)) mutableState.value = saved.copy(message = "No se pudo sincronizar. Los datos siguen guardados.") }
        }
    }

    private suspend fun savedState(
        evaluations: List<TargetEvaluation>,
        session: SessionSnapshot,
        outcome: EvaluationSyncResult? = null
    ): ScanEvaluationUiState.Saved {
        val summary = actions.summaryStatus(scanId, session)
        val status = summary?.status
        if (outcome == EvaluationSyncResult.GENERATION_REFRESH_REQUIRED) generationRefreshRequired = true
        val refreshRequired = generationRefreshRequired
        val message = when {
            refreshRequired -> "La generación no coincide con el servidor. Cierra sesión y vuelve a iniciarla antes de sincronizar. El resumen permanece guardado sin cambios."
            summary != null && summary.generation > session.summaryGeneration -> "Este resumen tiene una generación posterior a la sesión vigente. Se conserva localmente sin modificar y no se reenviará."
            summary != null && summary.generation < session.summaryGeneration -> "Este resumen pertenece a una generación anterior al borrado. Se conserva localmente y no se reenviará."
            status == SummarySyncStatus.CONFLICT -> "El servidor tiene otro contenido para esta revisión. El resumen quedó en conflicto y no se reenviará automáticamente."
            status == SummarySyncStatus.OBSOLETE -> "Este resumen pertenece a una generación anterior al borrado. Se conserva localmente y no se reenviará."
            status == SummarySyncStatus.SYNCED -> "Evaluación guardada en este dispositivo. Resumen sincronizado con el servidor."
            status == SummarySyncStatus.PENDING && outcome == EvaluationSyncResult.BUSY -> "Resumen pendiente; hay otra sincronización en curso. Comprueba su estado antes de reintentar."
            status == SummarySyncStatus.PENDING -> "Evaluación guardada. El resumen sigue pendiente de sincronización."
            else -> "Evaluación guardada. No se pudo comprobar el estado del resumen; vuelve a cargarlo."
        }
        return ScanEvaluationUiState.Saved(evaluations, message,
            canRetrySync = status == SummarySyncStatus.PENDING && summary?.generation == session.summaryGeneration && !refreshRequired)
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
    }
}
