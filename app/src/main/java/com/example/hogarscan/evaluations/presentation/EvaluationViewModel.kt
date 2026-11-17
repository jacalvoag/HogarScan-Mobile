package com.example.hogarscan.evaluations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.evaluations.domain.EvaluationFailure
import com.example.hogarscan.evaluations.domain.EvaluationGateway
import com.example.hogarscan.evaluations.domain.EvaluationOutcome
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EvaluationScreenState(
    val evaluation: EvaluationUiState = EvaluationUiState.Ready,
    val form: IdentificationForm = IdentificationForm(),
    val errors: IdentificationErrors = IdentificationErrors()
)

/** El objetivo y el propietario se fijan al crear el ViewModel. */
class EvaluationViewModel(
    private val scanId: String,
    private val ownerId: String,
    private val address: Ipv4Address,
    results: List<ProbeResult>,
    private val sessions: SessionReader,
    private val gateway: EvaluationGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val observations = results.toList()
    private val mutableState = MutableStateFlow(EvaluationScreenState())
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var operationId = 0L
    private var resultSession: SessionSnapshot? = null
    private var observedIdentity = identity(sessions.state.value)

    init {
        viewModelScope.launch {
            sessions.state.collect { state ->
                val currentIdentity = identity(state)
                val expected = resultSession
                if (currentIdentity != observedIdentity ||
                    (expected != null && !sessions.isCurrentSession(expected, nowMillis()))) {
                    observedIdentity = currentIdentity
                    invalidateForSessionChange()
                }
            }
        }
    }

    fun updateForm(form: IdentificationForm) {
        if (mutableState.value.evaluation is EvaluationUiState.Loading) return
        mutableState.update { it.copy(form = form, errors = IdentificationErrors()) }
    }

    fun setErrors(errors: IdentificationErrors) {
        mutableState.update { it.copy(errors = errors) }
    }

    fun evaluate() {
        if (mutableState.value.evaluation is EvaluationUiState.Loading) return
        val session = (sessions.state.value as? SessionState.Authenticated)?.session
        if (session == null || ownerId != "user:${session.userId}" ||
            !sessions.isCurrentSession(session, nowMillis())) {
            mutableState.update { it.copy(evaluation = EvaluationUiState.Error(SESSION_MESSAGE)) }
            return
        }
        val identification = when (val validation = mutableState.value.form.validate()) {
            is IdentificationValidation.Invalid -> {
                setErrors(validation.errors)
                return
            }
            is IdentificationValidation.Valid -> validation.identification
        }
        val input = try {
            EvaluationInput.fromResults(scanId, ownerId, address, observations, identification)
        } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(evaluation = EvaluationUiState.Error("Las observaciones no son válidas para esta evaluación.")) }
            return
        }
        val currentId = ++operationId
        resultSession = session
        mutableState.update { it.copy(evaluation = EvaluationUiState.Loading, errors = IdentificationErrors()) }
        job = viewModelScope.launch {
            val outcome = try {
                gateway.evaluate(input, session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                EvaluationOutcome.Failure(EvaluationFailure.OTHER)
            }
            currentCoroutineContext().ensureActive()
            if (currentId != operationId) return@launch
            if (!sessions.isCurrentSession(session, nowMillis())) {
                invalidateForSessionChange()
                return@launch
            }
            mutableState.update { it.copy(evaluation = when (outcome) {
                is EvaluationOutcome.Success -> EvaluationUiState.Success(outcome.result)
                is EvaluationOutcome.Failure -> EvaluationUiState.Error(outcome.reason.message())
            }) }
        }
    }

    fun cancel() {
        if (mutableState.value.evaluation !is EvaluationUiState.Loading) return
        operationId++
        job?.cancel()
        job = null
        resultSession = null
        mutableState.update { it.copy(evaluation = EvaluationUiState.Ready) }
    }

    private fun invalidateForSessionChange() {
        operationId++
        job?.cancel()
        job = null
        resultSession = null
        mutableState.value = EvaluationScreenState(evaluation = EvaluationUiState.Error(SESSION_MESSAGE))
    }

    private companion object {
        const val SESSION_MESSAGE = "La sesión cambió o no está disponible para esta revisión."

        fun identity(state: SessionState): Pair<String, String>? =
            (state as? SessionState.Authenticated)?.session?.let { it.userId to it.contextId }
    }
}

private fun EvaluationFailure.message(): String = when (this) {
    EvaluationFailure.CONNECTION -> "No fue posible conectar con la API. Revisa la conexión e inténtalo de nuevo."
    EvaluationFailure.TIMEOUT -> "La evaluación agotó el tiempo de espera. Puedes intentarlo de nuevo."
    EvaluationFailure.SESSION -> "La sesión no está disponible. Vuelve a iniciar sesión."
    EvaluationFailure.RATE_LIMITED -> "Se alcanzó el límite de solicitudes. Espera antes de intentar de nuevo."
    EvaluationFailure.INVALID_RESPONSE -> "La API devolvió una respuesta que no se pudo interpretar."
    EvaluationFailure.OTHER -> "No fue posible completar la evaluación. Inténtalo de nuevo."
}
