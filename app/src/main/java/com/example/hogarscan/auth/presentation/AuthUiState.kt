package com.example.hogarscan.auth.presentation

import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.LogoutOutcome
import com.example.hogarscan.auth.domain.model.SessionState

data class AuthUiState(
    val session: SessionState,
    val operation: AuthOperation? = null,
    val notice: AuthNotice? = null
) {
    val canSubmitCredentials: Boolean
        get() = session == SessionState.Guest && operation == null
}

enum class AuthOperation {
    RESTORE,
    REGISTER,
    LOGIN,
    LOGOUT
}

sealed interface AuthNotice {
    data object AccountCreated : AuthNotice

    data class LoggedOut(
        val outcome: LogoutOutcome
    ) : AuthNotice

    data class Failure(
        val reason: AuthFailure,
        val retryAfterSeconds: Long? = null
    ) : AuthNotice
}