package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesAction
import com.example.hogarscan.scan.domain.usecase.DeleteSummariesOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeleteSummariesUiState(
    val session: SessionSnapshot? = null,
    val confirmation: SessionSnapshot? = null,
    val pending: Boolean = false,
    val message: String? = null
)

class DeleteSummariesViewModel(
    private val sessions: SessionReader,
    private val action: DeleteSummariesAction,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeleteSummariesUiState())
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            sessions.state.collect { state ->
                val session = (state as? SessionState.Authenticated)?.session
                val previous = mutableState.value.session
                if (session?.contextId != previous?.contextId || session?.userId != previous?.userId) {
                    job?.cancel()
                    mutableState.value = DeleteSummariesUiState(session = session)
                } else {
                    // La generación confirmada se conserva aunque cambie la sesión publicada.
                    mutableState.value = mutableState.value.copy(session = session)
                }
            }
        }
    }

    fun requestConfirmation() {
        if (mutableState.value.pending) return
        val session = activeSession() ?: return
        mutableState.value = mutableState.value.copy(confirmation = session, message = null)
    }

    fun cancelConfirmation() {
        mutableState.value = mutableState.value.copy(confirmation = null)
    }

    fun confirm() {
        val state = mutableState.value
        if (state.pending) return
        val expected = state.confirmation ?: return
        if (!sessions.isCurrentSession(expected, nowMillis())) {
            mutableState.value = state.copy(confirmation = null, message = "La sesión cambió. Vuelve a confirmar con la cuenta actual.")
            return
        }
        mutableState.value = state.copy(confirmation = null, pending = true)
        job = viewModelScope.launch {
            try {
                val result = action.delete(expected)
                currentCoroutineContext().ensureActive()
                publishResult(expected, result.message())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                publishResult(expected, DeleteSummariesOutcome.UNCONFIRMED.message())
            }
        }
    }

    private fun publishResult(expected: SessionSnapshot, message: String) {
        val current = (sessions.state.value as? SessionState.Authenticated)?.session
        if (current == null || current.contextId != expected.contextId || current.userId != expected.userId) return
        mutableState.value = if (sessions.isCurrentSession(expected, nowMillis())) {
            mutableState.value.copy(session = current, pending = false, message = message)
        } else {
            DeleteSummariesUiState()
        }
    }

    private fun activeSession(): SessionSnapshot? =
        (sessions.state.value as? SessionState.Authenticated)?.session?.takeIf {
            sessions.isCurrentSession(it, nowMillis())
        }
}

private fun DeleteSummariesOutcome.message(): String = when (this) {
    DeleteSummariesOutcome.DELETED -> "Resúmenes borrados."
    DeleteSummariesOutcome.ALREADY_ADVANCED -> "La generación ya cambió. Actualiza antes de confirmar otra operación."
    DeleteSummariesOutcome.NEW_CONFIRMATION_REQUIRED -> "La generación no coincide. Cierra sesión y vuelve a iniciarla antes de confirmar otra operación."
    DeleteSummariesOutcome.SESSION_CHANGED -> "La sesión cambió; comprueba el estado antes de volver a intentar."
    DeleteSummariesOutcome.UNCONFIRMED -> "No se pudo confirmar el borrado. Comprueba el estado antes de volver a intentar."
}
