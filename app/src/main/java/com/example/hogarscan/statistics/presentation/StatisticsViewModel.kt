package com.example.hogarscan.statistics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.statistics.domain.AccountStatistics
import com.example.hogarscan.statistics.domain.StatisticsFailure
import com.example.hogarscan.statistics.domain.StatisticsGateway
import com.example.hogarscan.statistics.domain.StatisticsOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StatisticsUiState {
    data object Idle : StatisticsUiState
    data object Loading : StatisticsUiState
    data object SignInRequired : StatisticsUiState
    data class Error(val message: String) : StatisticsUiState
    data class Data(val statistics: AccountStatistics) : StatisticsUiState
}

class StatisticsViewModel(
    private val sessions: SessionReader,
    private val gateway: StatisticsGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Idle)
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestId = 0L
    private var identity = sessionKey(sessions.state.value)

    init {
        viewModelScope.launch {
            sessions.state.collect { state ->
                val current = sessionKey(state)
                if (identity != current) {
                    identity = current
                    requestId++
                    job?.cancel()
                    mutableState.value = if (current == null) StatisticsUiState.SignInRequired else StatisticsUiState.Idle
                }
            }
        }
    }

    /** También se llama después de sincronizar o borrar, cuando lo indique el integrador. */
    fun refresh() {
        requestId++
        val id = requestId
        job?.cancel()
        val session = (sessions.state.value as? SessionState.Authenticated)?.session
        if (session == null || !sessions.isCurrentSession(session, nowMillis())) {
            mutableState.value = StatisticsUiState.SignInRequired
            return
        }
        mutableState.value = StatisticsUiState.Loading
        job = viewModelScope.launch {
            val result = try { gateway.getStatistics(session) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { StatisticsOutcome.Failure(StatisticsFailure.OTHER) }
            currentCoroutineContext().ensureActive()
            if (id != requestId) return@launch
            if (!sessions.isCurrentSession(session, nowMillis())) {
                mutableState.value = StatisticsUiState.SignInRequired
                return@launch
            }
            val current = (sessions.state.value as SessionState.Authenticated).session
            if (current.summaryGeneration != session.summaryGeneration) {
                mutableState.value = StatisticsUiState.Error("La información cambió. Actualiza las estadísticas.")
                return@launch
            }
            mutableState.value = when (result) {
                is StatisticsOutcome.Success -> if (result.statistics.summaryGeneration == current.summaryGeneration) {
                    StatisticsUiState.Data(result.statistics)
                } else {
                    StatisticsUiState.Error("La generación de estadísticas cambió. Actualiza la sesión antes de volver a consultar.")
                }
                is StatisticsOutcome.Failure -> StatisticsUiState.Error(result.reason.message())
            }
        }
    }

    private companion object {
        // Una nueva generación invalida los datos visibles aunque la sesión sea la misma.
        fun sessionKey(state: SessionState): Triple<String, String, Int>? =
            (state as? SessionState.Authenticated)?.session?.let { Triple(it.userId, it.contextId, it.summaryGeneration) }
    }
}

private fun StatisticsFailure.message(): String = when (this) {
    StatisticsFailure.CONNECTION -> "No fue posible conectar con la API."
    StatisticsFailure.TIMEOUT -> "La consulta agotó el tiempo de espera."
    StatisticsFailure.SESSION -> "La sesión no está disponible."
    StatisticsFailure.RATE_LIMITED -> "Se alcanzó el límite de solicitudes. Espera antes de intentar de nuevo."
    StatisticsFailure.INVALID_RESPONSE -> "La API devolvió una respuesta que no se pudo interpretar."
    StatisticsFailure.OTHER -> "No fue posible consultar las estadísticas."
}
