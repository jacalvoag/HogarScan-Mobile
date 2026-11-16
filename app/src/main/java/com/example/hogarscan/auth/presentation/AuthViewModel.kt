package com.example.hogarscan.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.AuthRepository
import com.example.hogarscan.auth.domain.repository.SessionReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessions: SessionReader
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(AuthUiState(sessions.state.value))

    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.state.collect { current ->
                mutableState.update { previous ->
                    previous.copy(
                        session = current,
                        notice = if (
                            current is SessionState.Authenticated &&
                            !sameSession(current, previous.session)
                        ) {
                            null
                        } else {
                            previous.notice
                        }
                    )
                }
            }
        }

        if (sessions.state.value == SessionState.Restoring) {
            perform(AuthOperation.RESTORE) {
                repository.restoreSession().failureNotice()
            }
        }
    }

    fun register(
        email: String,
        password: String,
        confirmation: String
    ) {
        if (sessions.state.value != SessionState.Guest) return

        perform(AuthOperation.REGISTER) {
            when (
                val result = repository.register(
                    email,
                    password,
                    confirmation
                )
            ) {
                is AuthResult.Success -> AuthNotice.AccountCreated
                is AuthResult.Failure -> result.failureNotice()
            }
        }
    }

    fun login(email: String, password: String) {
        if (sessions.state.value != SessionState.Guest) return

        perform(AuthOperation.LOGIN) {
            repository.login(email, password).failureNotice()
        }
    }

    fun logout(expected: SessionSnapshot) {
        val current =
            (sessions.state.value as? SessionState.Authenticated)?.session
                ?: return

        if (
            current.contextId != expected.contextId ||
            current.userId != expected.userId
        ) {
            return
        }

        perform(AuthOperation.LOGOUT) {
            when (val result = repository.logout(expected)) {
                is AuthResult.Success ->
                    AuthNotice.LoggedOut(result.value)

                is AuthResult.Failure ->
                    result.failureNotice()
            }
        }
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    private fun perform(
        operation: AuthOperation,
        action: suspend () -> AuthNotice?
    ) {
        while (true) {
            val current = mutableState.value

            if (current.operation != null) return

            if (
                mutableState.compareAndSet(
                    current,
                    current.copy(
                        operation = operation,
                        notice = null
                    )
                )
            ) {
                break
            }
        }

        val startedSession = sessions.state.value

        viewModelScope.launch {
            var notice: AuthNotice? = null

            try {
                notice = action()
            } finally {
                mutableState.update {
                    val current = sessions.state.value

                    it.copy(
                        session = current,
                        operation = null,
                        notice = notice.takeIf {
                            current !is SessionState.Authenticated ||
                                    (
                                            operation == AuthOperation.LOGOUT &&
                                                    notice is AuthNotice.Failure &&
                                                    sameSession(current, startedSession)
                                            )
                        }
                    )
                }
            }
        }
    }

    private fun sameSession(
        first: SessionState,
        second: SessionState
    ): Boolean {
        val a =
            (first as? SessionState.Authenticated)?.session
                ?: return false

        val b =
            (second as? SessionState.Authenticated)?.session
                ?: return false

        return a.contextId == b.contextId && a.userId == b.userId
    }

    private fun AuthResult<*>.failureNotice(): AuthNotice.Failure? =
        (this as? AuthResult.Failure)?.let {
            AuthNotice.Failure(it.reason, it.retryAfterSeconds)
        }
}