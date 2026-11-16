package com.example.hogarscan.auth.data.repository

import com.example.hogarscan.auth.data.http.HttpAuthDataSource
import com.example.hogarscan.auth.data.session.AuthSessionStore
import com.example.hogarscan.auth.data.session.SessionCredentials
import com.example.hogarscan.auth.data.session.SessionStorageException
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.policy.AuthInputPolicy
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import com.squareup.moshi.JsonReader
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class DefaultAuthRepositoryTest {
    private val sessions = FakeSessions()
    private val requests = mutableListOf<ApiRequest>()
    private var response: ApiResult = ApiResult.Success(200, LOGIN_JSON)
    private var handler: (suspend (ApiRequest) -> ApiResult)? = null

    private val repository = DefaultAuthRepository(
        HttpAuthDataSource(
            ApiTransport { request ->
                requests += request
                handler?.invoke(request) ?: response
            }
        ),
        sessions,
        nowMillis = { 1_000L }
    )

    @Test
    fun `espera restauracion y no envia login prematuro`() = runTest {
        sessions.state.value = SessionState.Restoring

        assertReason(
            AuthFailure.SESSION_NOT_READY,
            repository.login(EMAIL, PASSWORD)
        )
        assertTrue(requests.isEmpty())

        assertEquals(AuthResult.Success(Unit), repository.restoreSession())
        assertTrue(repository.login(EMAIL, PASSWORD) is AuthResult.Success)
    }

    @Test
    fun `fallo de restauracion se comunica como almacenamiento`() = runTest {
        sessions.failRestore = true

        assertReason(AuthFailure.STORAGE, repository.restoreSession())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `normaliza correo pero conserva password y no envia confirmacion`() = runTest {
        response = ApiResult.Success(201, USER_JSON)
        val password = "  Clave de prueba con espacios  "

        assertTrue(
            repository.register(
                "  FIXTURE@EXAMPLE.COM ",
                password,
                password
            ) is AuthResult.Success
        )

        val body = JsonReader.of(
            Buffer().writeUtf8(requireNotNull(requests.single().bodyJson))
        ).use {
            it.readJsonValue() as Map<*, *>
        }

        assertEquals(mapOf("email" to EMAIL, "password" to password), body)
        assertEquals("/auth/register", requests.single().path)
        assertEquals(0, sessions.activations)
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `entradas invalidas no hacen solicitudes`() = runTest {
        assertReason(
            AuthFailure.INVALID_EMAIL,
            repository.login("sin-correo", PASSWORD)
        )
        assertReason(
            AuthFailure.INVALID_PASSWORD,
            repository.login(EMAIL, "")
        )
        assertReason(
            AuthFailure.INVALID_PASSWORD,
            repository.login(EMAIL, "x\uD800")
        )
        assertReason(
            AuthFailure.PASSWORD_CONFIRMATION_MISMATCH,
            repository.register(EMAIL, PASSWORD, PASSWORD + " ")
        )

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `registro y login usan limites distintos y cuentan puntos Unicode`() {
        val emoji = "🔐"

        assertEquals(
            AuthFailure.INVALID_PASSWORD,
            AuthInputPolicy.validateRegistration(
                EMAIL,
                emoji.repeat(14),
                emoji.repeat(14)
            )
        )

        listOf(
            emoji.repeat(15),
            emoji.repeat(128),
            " ".repeat(15)
        ).forEach { password ->
            assertNull(
                AuthInputPolicy.validateRegistration(
                    EMAIL,
                    password,
                    password
                )
            )
        }

        assertEquals(
            AuthFailure.INVALID_PASSWORD,
            AuthInputPolicy.validateLogin(EMAIL, emoji.repeat(129))
        )
        assertNull(AuthInputPolicy.validateLogin(EMAIL, " "))
    }

    @Test
    fun `login entrega solo la instantanea confirmada por el almacen`() = runTest {
        val result = repository.login(EMAIL, PASSWORD) as AuthResult.Success

        assertSame(
            (sessions.state.value as SessionState.Authenticated).session,
            result.value
        )
        assertEquals(1, sessions.activations)
        assertEquals(TOKEN, sessions.receivedToken)
        assertEquals(1, requests.size)
    }

    @Test
    fun `mapea credenciales incorrectas sin iniciar sesion`() = runTest {
        response = httpError(401, "INVALID_CREDENTIALS")

        assertReason(
            AuthFailure.INVALID_CREDENTIALS,
            repository.login(EMAIL, PASSWORD)
        )
        assertEquals(0, sessions.activations)
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `mapea conflicto validacion limite y fallos de transporte`() = runTest {
        listOf(
            httpError(409, "EMAIL_ALREADY_REGISTERED") to
                    AuthFailure.EMAIL_UNAVAILABLE,
            httpError(400, "INVALID_REQUEST") to
                    AuthFailure.INVALID_INPUT,
            ApiResult.NetworkFailure to AuthFailure.NETWORK,
            ApiResult.TimedOut to AuthFailure.TIMEOUT,
            ApiResult.TlsFailure to AuthFailure.TLS,
            httpError(503, "SERVICE_UNAVAILABLE") to
                    AuthFailure.SERVICE_UNAVAILABLE,
            httpError(401, "UNKNOWN") to AuthFailure.INVALID_RESPONSE
        ).forEach { (failure, expected) ->
            response = failure
            assertReason(expected, repository.login(EMAIL, PASSWORD))
        }

        response = ApiResult.HttpFailure(429, null, 60)

        assertEquals(
            AuthResult.Failure(AuthFailure.RATE_LIMITED, 60),
            repository.login(EMAIL, PASSWORD)
        )
    }

    @Test
    fun `fallo local intenta revocar solo el token recien recibido`() = runTest {
        sessions.failActivate = true
        handler = {
            if (it.path == "/auth/logout") {
                ApiResult.Success(204, null)
            } else {
                response
            }
        }

        assertReason(
            AuthFailure.STORAGE,
            repository.login(EMAIL, PASSWORD)
        )

        val cleanup = requests.last()
        assertEquals("/auth/logout", cleanup.path)
        assertEquals(TOKEN, cleanup.bearerToken)
        assertNull(cleanup.bodyJson)
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `limita limpieza remota y conserva el error original`() = runTest {
        sessions.failActivate = true
        handler = {
            if (it.path == "/auth/logout") {
                awaitCancellation()
            } else {
                response
            }
        }

        assertReason(
            AuthFailure.STORAGE,
            repository.login(EMAIL, PASSWORD)
        )
    }

    @Test
    fun `vigencia rechazada por el almacen no se publica como exito`() = runTest {
        sessions.rejectExpired = true

        assertReason(
            AuthFailure.SESSION_EXPIRED,
            repository.login(EMAIL, PASSWORD)
        )
        assertEquals("/auth/logout", requests.last().path)
    }

    @Test
    fun `no reemplaza una sesion activa y permite reautenticar una vencida`() = runTest {
        sessions.state.value = SessionState.Authenticated(snapshot(2_000))

        assertReason(
            AuthFailure.SESSION_ALREADY_ACTIVE,
            repository.login(EMAIL, PASSWORD)
        )
        assertTrue(requests.isEmpty())

        sessions.state.value = SessionState.Authenticated(snapshot(500))

        assertTrue(repository.login(EMAIL, PASSWORD) is AuthResult.Success)
        assertEquals(1, sessions.invalidations)
    }

    @Test
    fun `fallo al retirar una sesion vencida bloquea otro login`() = runTest {
        sessions.state.value = SessionState.Authenticated(snapshot(500))
        sessions.failInvalidate = true

        assertReason(
            AuthFailure.STORAGE,
            repository.login(EMAIL, PASSWORD)
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `segundo login simultaneo se rechaza sin enviarlo`() = runTest {
        val release = CompletableDeferred<Unit>()
        handler = {
            release.await()
            response
        }

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            repository.login(EMAIL, PASSWORD)
        }

        assertReason(
            AuthFailure.BUSY,
            repository.login(EMAIL, PASSWORD)
        )
        assertEquals(1, requests.size)

        release.complete(Unit)

        assertTrue(first.await() is AuthResult.Success)
        assertEquals(1, sessions.activations)
    }

    @Test
    fun `cancelar libera operacion sin convertirse en error de credenciales`() = runTest {
        handler = { awaitCancellation() }

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            repository.login(EMAIL, PASSWORD)
        }

        first.cancelAndJoin()
        assertTrue(first.isCancelled)

        handler = null

        assertTrue(repository.login(EMAIL, PASSWORD) is AuthResult.Success)
    }

    private fun assertReason(
        expected: AuthFailure,
        result: AuthResult<*>
    ) {
        assertEquals(expected, (result as AuthResult.Failure).reason)
    }

    private fun httpError(status: Int, code: String) =
        ApiResult.HttpFailure(
            status,
            """{"code":"$code","message":"Mensaje de prueba"}""",
            null
        )

    private class FakeSessions : AuthSessionStore {
        override val state =
            MutableStateFlow<SessionState>(SessionState.Guest)

        var activations = 0
        var invalidations = 0
        var receivedToken: String? = null
        var failRestore = false
        var failActivate = false
        var failInvalidate = false
        var rejectExpired = false

        override suspend fun tokenFor(
            expected: SessionSnapshot,
            nowMillis: Long
        ): String? {
            val active = (state.value as? SessionState.Authenticated)?.session
                ?: return null

            return receivedToken.takeIf {
                active.contextId == expected.contextId &&
                        active.userId == expected.userId &&
                        active.expiresAtMillis > nowMillis
            }
        }

        override suspend fun restore(): Boolean {
            if (failRestore) throw storageFailure()
            state.value = SessionState.Guest
            return true
        }

        override suspend fun activate(
            credentials: SessionCredentials
        ): SessionSnapshot? {
            activations++

            if (failActivate) throw storageFailure()
            if (rejectExpired) return null

            receivedToken = credentials.accessToken

            val snapshot = SessionSnapshot(
                "fixture-context",
                credentials.userId,
                credentials.email,
                credentials.expiresAtMillis,
                credentials.summaryGeneration
            )

            state.value = SessionState.Authenticated(snapshot)
            return snapshot
        }

        override suspend fun invalidate(
            expected: SessionSnapshot
        ): Boolean {
            if (failInvalidate) throw storageFailure()

            invalidations++
            state.value = SessionState.Guest
            return true
        }

        private fun storageFailure() =
            SessionStorageException(IOException("fixture"))
    }

    companion object {
        private const val EMAIL = "fixture@example.com"
        private const val PASSWORD = "Clave de prueba suficientemente larga"
        private const val ID = "00000000-0000-4000-8000-000000000001"

        private const val USER_JSON =
            """{"id":"$ID","email":"$EMAIL","created_at":"2026-11-16T01:02:03Z"}"""

        private val TOKEN = "fixture_a".repeat(5).take(43)

        private val LOGIN_JSON =
            """{"user":$USER_JSON,"access_token":"$TOKEN","token_type":"Bearer","expires_at":"2026-11-23T01:02:03Z","summary_generation":0}"""

        private fun snapshot(expires: Long) =
            SessionSnapshot("old-context", ID, EMAIL, expires, 0)
    }
}