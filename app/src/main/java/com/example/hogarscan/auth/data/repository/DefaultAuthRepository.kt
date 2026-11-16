package com.example.hogarscan.auth.data.repository

import com.example.hogarscan.auth.data.http.AuthRemoteResult
import com.example.hogarscan.auth.data.http.HttpAuthDataSource
import com.example.hogarscan.auth.data.session.AuthSessionStore
import com.example.hogarscan.auth.data.session.SessionStorageException
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.AuthUser
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.policy.AuthInputPolicy
import com.example.hogarscan.auth.domain.repository.AuthRepository
import com.example.hogarscan.shared.data.http.ApiErrorDecoder
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

class DefaultAuthRepository(
    private val remote: HttpAuthDataSource,
    private val sessions: AuthSessionStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AuthRepository {
    private val operation = Mutex()

    override suspend fun restoreSession(): AuthResult<Unit> = exclusive {
        try {
            sessions.restore()
            AuthResult.Success(Unit)
        } catch (_: SessionStorageException) {
            failure(AuthFailure.STORAGE)
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        confirmation: String
    ): AuthResult<AuthUser> = exclusive {
        guestProblem()?.let {
            return@exclusive failure(it)
        }

        val normalizedEmail = AuthInputPolicy.normalizeEmail(email)

        AuthInputPolicy.validateRegistration(
            normalizedEmail,
            password,
            confirmation
        )?.let {
            return@exclusive failure(it)
        }

        when (val result = remote.register(normalizedEmail, password)) {
            is AuthRemoteResult.Success ->
                AuthResult.Success(result.value)

            is AuthRemoteResult.Failure ->
                mapFailure(result.error)
        }
    }

    override suspend fun login(
        email: String,
        password: String
    ): AuthResult<SessionSnapshot> = exclusive {
        guestProblem()?.let {
            return@exclusive failure(it)
        }

        val normalizedEmail = AuthInputPolicy.normalizeEmail(email)

        AuthInputPolicy.validateLogin(normalizedEmail, password)?.let {
            return@exclusive failure(it)
        }

        when (val result = remote.login(normalizedEmail, password)) {
            is AuthRemoteResult.Failure -> mapFailure(result.error)

            is AuthRemoteResult.Success -> {
                currentCoroutineContext().ensureActive()

                val snapshot = try {
                    sessions.activate(result.value)
                } catch (_: SessionStorageException) {
                    revokeUnused(result.value.accessToken)
                    return@exclusive failure(AuthFailure.STORAGE)
                }

                if (snapshot == null) {
                    revokeUnused(result.value.accessToken)
                    failure(AuthFailure.SESSION_EXPIRED)
                } else {
                    AuthResult.Success(snapshot)
                }
            }
        }
    }

    private suspend fun guestProblem(): AuthFailure? {
        val active = sessions.state.value as? SessionState.Authenticated

        if (
            active != null &&
            active.session.expiresAtMillis <= nowMillis()
        ) {
            try {
                sessions.invalidate(active.session)
            } catch (_: SessionStorageException) {
                return AuthFailure.STORAGE
            }
        }

        return when (sessions.state.value) {
            SessionState.Guest -> null
            SessionState.Restoring -> AuthFailure.SESSION_NOT_READY
            is SessionState.Authenticated ->
                AuthFailure.SESSION_ALREADY_ACTIVE
        }
    }

    private suspend fun revokeUnused(token: String) {
        withTimeoutOrNull(5_000) {
            remote.revoke(token)
        }
    }

    private fun mapFailure(result: ApiResult): AuthResult.Failure =
        when (result) {
            ApiResult.NetworkFailure -> failure(AuthFailure.NETWORK)
            ApiResult.TimedOut -> failure(AuthFailure.TIMEOUT)
            ApiResult.TlsFailure -> failure(AuthFailure.TLS)
            ApiResult.SessionStorageFailure -> failure(AuthFailure.STORAGE)

            is ApiResult.HttpFailure -> {
                val error = ApiErrorDecoder.decode(result)

                when {
                    error.statusCode == 401 &&
                            error.code == "INVALID_CREDENTIALS" ->
                        failure(AuthFailure.INVALID_CREDENTIALS)

                    error.statusCode == 409 &&
                            error.code == "EMAIL_ALREADY_REGISTERED" ->
                        failure(AuthFailure.EMAIL_UNAVAILABLE)

                    error.statusCode == 400 &&
                            error.code == "INVALID_REQUEST" ->
                        failure(AuthFailure.INVALID_INPUT)

                    error.statusCode == 429 ->
                        AuthResult.Failure(
                            AuthFailure.RATE_LIMITED,
                            error.retryAfterSeconds
                        )

                    error.statusCode in 500..599 ->
                        failure(AuthFailure.SERVICE_UNAVAILABLE)

                    else -> failure(AuthFailure.INVALID_RESPONSE)
                }
            }

            else -> failure(AuthFailure.INVALID_RESPONSE)
        }

    private suspend fun <T> exclusive(
        block: suspend () -> AuthResult<T>
    ): AuthResult<T> {
        currentCoroutineContext().ensureActive()

        if (!operation.tryLock()) {
            return failure(AuthFailure.BUSY)
        }

        return try {
            block()
        } finally {
            operation.unlock()
        }
    }

    private fun failure(reason: AuthFailure) =
        AuthResult.Failure(reason)
}