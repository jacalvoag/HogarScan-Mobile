package com.example.hogarscan.auth.data.repository

import com.example.hogarscan.auth.data.http.HttpAuthDataSource
import com.example.hogarscan.auth.data.session.AuthSessionStore
import com.example.hogarscan.auth.data.session.SessionCredentials
import com.example.hogarscan.auth.data.session.SessionStorageException
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.LogoutOutcome
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LogoutTest {
    private val original = snapshot("original")
    private val sessions = FakeSessions(original)
    private val requests = mutableListOf<ApiRequest>()

    private var response: ApiResult = ApiResult.Success(204, null)
    private var handler: (suspend () -> ApiResult)? = null

    private val repository = DefaultAuthRepository(
        HttpAuthDataSource(
            ApiTransport { request ->
                requests += request
                handler?.invoke() ?: response
            }
        ),
        sessions,
        nowMillis = { 1_000L }
    )

    @Test
    fun `retira la sesion local antes de la llamada remota`() = runTest {
        handler = {
            assertSame(SessionState.Guest, sessions.state.value)
            assertNull(sessions.token)
            response
        }

        assertOutcome(
            LogoutOutcome.REMOTE_CONFIRMED,
            repository.logout(original)
        )

        val request = requests.single()
        assertEquals(ApiMethod.POST, request.method)
        assertEquals("/auth/logout", request.path)
        assertEquals(TOKEN, request.bearerToken)
        assertNull(request.bodyJson)
    }

    @Test
    fun `fallos remotos conservan el cierre local sin afirmar revocacion`() = runTest {
        listOf(
            ApiResult.NetworkFailure,
            ApiResult.TimedOut,
            ApiResult.TlsFailure,
            ApiResult.InvalidResponse,
            ApiResult.HttpFailure(429, null, 60),
            ApiResult.HttpFailure(503, null, null),
            ApiResult.HttpFailure(401, "{}", null),
            ApiResult.Success(200, "{}"),
            ApiResult.Success(204, "{}")
        ).forEach { failure ->
            sessions.reset(original)
            response = failure

            assertOutcome(
                LogoutOutcome.REMOTE_UNCONFIRMED,
                repository.logout(original)
            )
            assertSame(SessionState.Guest, sessions.state.value)
            assertNull(sessions.token)
        }
    }

    @Test
    fun `401 reconocido confirma que el token ya no es vigente`() = runTest {
        response = ApiResult.HttpFailure(
            401,
            """{"code":"UNAUTHENTICATED","message":"Inicia sesión de nuevo."}""",
            null
        )

        assertOutcome(
            LogoutOutcome.REMOTE_CONFIRMED,
            repository.logout(original)
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `espera remota acotada no retiene la sesion local`() = runTest {
        handler = { awaitCancellation() }

        assertOutcome(
            LogoutOutcome.REMOTE_UNCONFIRMED,
            repository.logout(original)
        )
        assertSame(SessionState.Guest, sessions.state.value)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `sin token vigente solo confirma cierre local`() = runTest {
        sessions.reset(snapshot("original", expires = 500))

        assertOutcome(
            LogoutOutcome.REMOTE_UNCONFIRMED,
            repository.logout(original)
        )
        assertTrue(requests.isEmpty())
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `un contexto anterior no puede cerrar otra sesion de la misma cuenta`() = runTest {
        val replacement = snapshot("replacement")
        sessions.reset(replacement)

        assertFailure(
            AuthFailure.SESSION_CHANGED,
            repository.logout(original)
        )
        assertEquals(
            SessionState.Authenticated(replacement),
            sessions.state.value
        )
        assertEquals(0, sessions.invalidations)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `no acepta una cuenta distinta aunque coincida el contexto`() = runTest {
        val other = original.copy(userId = "other-user")

        assertFailure(
            AuthFailure.SESSION_CHANGED,
            repository.logout(other)
        )
        assertEquals(0, sessions.invalidations)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `comprueba cambio ocurrido entre leer el token y retirar sesion`() = runTest {
        val replacement = snapshot("replacement")
        sessions.afterToken = { sessions.reset(replacement) }

        assertFailure(
            AuthFailure.SESSION_CHANGED,
            repository.logout(original)
        )
        assertEquals(
            SessionState.Authenticated(replacement),
            sessions.state.value
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `no inicia logout durante restauracion ni desde invitado`() = runTest {
        sessions.state.value = SessionState.Restoring

        assertFailure(
            AuthFailure.SESSION_NOT_READY,
            repository.logout(original)
        )

        sessions.state.value = SessionState.Guest

        assertFailure(
            AuthFailure.SESSION_CHANGED,
            repository.logout(original)
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `fallo de lectura no se anuncia como cierre correcto`() = runTest {
        sessions.failRead = true

        assertFailure(
            AuthFailure.STORAGE,
            repository.logout(original)
        )
        assertEquals(0, sessions.invalidations)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `fallo al borrar sigue siendo error aunque se revoque remotamente`() = runTest {
        sessions.failClear = true

        assertFailure(
            AuthFailure.STORAGE,
            repository.logout(original)
        )
        assertEquals(TOKEN, requests.single().bearerToken)
    }

    @Test
    fun `otro logout concurrente no duplica revocacion`() = runTest {
        val release = CompletableDeferred<Unit>()

        handler = {
            release.await()
            response
        }

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            repository.logout(original)
        }

        assertFailure(
            AuthFailure.BUSY,
            repository.logout(original)
        )
        assertEquals(1, requests.size)

        release.complete(Unit)

        assertOutcome(LogoutOutcome.REMOTE_CONFIRMED, first.await())
    }

    @Test
    fun `cancelacion remota deja cierre local y libera operacion`() = runTest {
        handler = { awaitCancellation() }

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            repository.logout(original)
        }

        first.cancelAndJoin()

        assertTrue(first.isCancelled)
        assertSame(SessionState.Guest, sessions.state.value)
        assertNull(sessions.token)

        handler = null
        sessions.reset(original)

        assertOutcome(
            LogoutOutcome.REMOTE_CONFIRMED,
            repository.logout(original)
        )
    }

    @Test
    fun `respuesta tardia no altera la sesion que aparecio durante la solicitud`() = runTest {
        val replacement = snapshot("replacement")

        handler = {
            sessions.reset(replacement)
            response
        }

        assertOutcome(
            LogoutOutcome.REMOTE_CONFIRMED,
            repository.logout(original)
        )
        assertEquals(
            SessionState.Authenticated(replacement),
            sessions.state.value
        )
        assertEquals(1, sessions.invalidations)
    }

    private fun assertOutcome(
        expected: LogoutOutcome,
        result: AuthResult<LogoutOutcome>
    ) {
        assertEquals(AuthResult.Success(expected), result)
    }

    private fun assertFailure(
        expected: AuthFailure,
        result: AuthResult<*>
    ) {
        assertEquals(expected, (result as AuthResult.Failure).reason)
    }

    private class FakeSessions(
        initial: SessionSnapshot
    ) : AuthSessionStore {
        override val state = MutableStateFlow<SessionState>(
            SessionState.Authenticated(initial)
        )

        var token: String? = TOKEN
        var invalidations = 0
        var failRead = false
        var failClear = false
        var afterToken: (() -> Unit)? = null

        fun reset(snapshot: SessionSnapshot) {
            state.value = SessionState.Authenticated(snapshot)
            token = TOKEN
        }

        override suspend fun restore(): Boolean =
            error("No se usa en estas pruebas")

        override suspend fun activate(
            credentials: SessionCredentials
        ): SessionSnapshot? =
            error("No se usa en estas pruebas")

        override suspend fun tokenFor(
            expected: SessionSnapshot,
            nowMillis: Long
        ): String? {
            if (failRead) throw storageFailure()

            val active =
                (state.value as? SessionState.Authenticated)?.session
                    ?: return null

            val result = token.takeIf {
                matches(active, expected) &&
                        active.expiresAtMillis > nowMillis
            }

            afterToken?.invoke()
            return result
        }

        override suspend fun invalidate(
            expected: SessionSnapshot
        ): Boolean {
            invalidations++

            val active =
                (state.value as? SessionState.Authenticated)?.session
                    ?: return false

            if (!matches(active, expected)) return false
            if (failClear) throw storageFailure()

            state.value = SessionState.Guest
            token = null
            return true
        }

        private fun matches(
            a: SessionSnapshot,
            b: SessionSnapshot
        ): Boolean =
            a.contextId == b.contextId && a.userId == b.userId

        private fun storageFailure() =
            SessionStorageException(IOException("fixture"))
    }

    companion object {
        private val TOKEN = "fixture_a".repeat(5).take(43)

        private fun snapshot(
            context: String,
            expires: Long = 10_000
        ) = SessionSnapshot(
            context,
            "fixture-user",
            "fixture@example.com",
            expires,
            0
        )
    }
}