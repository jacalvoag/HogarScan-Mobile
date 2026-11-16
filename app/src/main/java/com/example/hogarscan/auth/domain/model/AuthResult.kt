package com.example.hogarscan.auth.domain.model

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(
        val reason: AuthFailure,
        val retryAfterSeconds: Long? = null
    ) : AuthResult<Nothing>
}

enum class AuthFailure {
    INVALID_EMAIL,
    INVALID_PASSWORD,
    PASSWORD_CONFIRMATION_MISMATCH,
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    EMAIL_UNAVAILABLE,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    TLS,
    INVALID_RESPONSE,
    STORAGE,
    SERVICE_UNAVAILABLE,
    BUSY,
    SESSION_NOT_READY,
    SESSION_ALREADY_ACTIVE,
    SESSION_EXPIRED,
    SESSION_CHANGED
}