package com.example.hogarscan.auth.data.http

import com.example.hogarscan.auth.data.session.SessionCredentialSource
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSessionApiClientTest {
    private val session = SessionSnapshot(
        contextId = "context-a",
        userId = "account-a",
        email = "a@example.com",
        expiresAtMillis = 10_000L,
        summaryGeneration = 0
    )

    private val replacement = session.copy(contextId = "context-b")
    private val request = ApiRequest(ApiMethod.GET, "/me/stats")
    private val sessions = FakeSessions(session)
    private val transport = FakeTransport()
    private var now = 1_000L

    private val client =
        DefaultSessionApiClient(transport, sessions) { now }

    @Test
    fun `el consumidor no puede elegir una credencial manualmente`() = runTest {
        val error = runCatching {
            client.execute(
                ApiRequest(
                    ApiMethod.GET,
                    "/me/stats",
                    bearerToken = "manual-token"
                ),
                session
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `no envia solicitudes mientras restaura o esta como invitado`() = runTest {
        listOf(
            SessionState.Restoring,
            SessionState.Guest
        ).forEach {
            sessions.mutableState.value = it

            assertSame(
                ApiResult.SessionUnavailable,
                client.execute(request, session)
            )
        }

        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `rechaza otra cuenta y otro login de la misma cuenta`() = runTest {
        listOf(
            replacement,
            replacement.copy(
                userId = "account-b",
                email = "b@example.com"
            )
        ).forEach {
            sessions.mutableState.value = SessionState.Authenticated(it)

            assertSame(
                ApiResult.SessionChanged,
                client.execute(request, session)
            )
        }

        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `una sesion vencida se invalida sin enviar la solicitud`() = runTest {
        now = 10_000L

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(request, session)
        )
        assertSame(SessionState.Guest, sessions.state.value)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `una sesion sin credencial se invalida antes del envio`() = runTest {
        sessions.token = null

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(request, session)
        )
        assertSame(SessionState.Guest, sessions.state.value)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `incorpora la credencial sin modificar la solicitud original`() = runTest {
        val result = client.execute(request, session)

        assertTrue(result is ApiResult.Success)
        assertEquals(
            "fixture-token",
            transport.requests.single().bearerToken
        )
        assertEquals(request.path, transport.requests.single().path)
        assertNull(request.bearerToken)
    }

    @Test
    fun `un cambio al leer la credencial impide enviar la solicitud`() = runTest {
        sessions.afterTokenRead = {
            sessions.mutableState.value =
                SessionState.Authenticated(replacement)
        }

        assertSame(
            ApiResult.SessionChanged,
            client.execute(request, session)
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `descarta el resultado si se cerro la sesion durante la solicitud`() = runTest {
        transport.handler = {
            sessions.mutableState.value = SessionState.Guest
            ApiResult.Success(200, "{}")
        }

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(request, session)
        )
    }

    @Test
    fun `descarta respuestas anteriores sin cerrar la nueva sesion`() = runTest {
        listOf(
            replacement,
            replacement.copy(
                userId = "account-b",
                email = "b@example.com"
            )
        ).forEach { next ->
            sessions.mutableState.value =
                SessionState.Authenticated(session)

            transport.handler = {
                sessions.mutableState.value =
                    SessionState.Authenticated(next)
                ApiResult.Success(200, "{}")
            }

            assertSame(
                ApiResult.SessionChanged,
                client.execute(request, session)
            )
            assertEquals(
                SessionState.Authenticated(next),
                sessions.state.value
            )
        }
    }

    @Test
    fun `descarta el resultado si la sesion vencio durante la espera`() = runTest {
        transport.handler = {
            now = 10_000L
            ApiResult.Success(200, "{}")
        }

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(request, session)
        )
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `UNAUTHENTICATED invalida exclusivamente la sesion utilizada`() = runTest {
        transport.handler = { unauthenticated() }

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(request, session)
        )
        assertSame(SessionState.Guest, sessions.state.value)
    }

    @Test
    fun `una invalidacion tardia no elimina una sesion nueva`() = runTest {
        transport.handler = { unauthenticated() }

        sessions.beforeInvalidate = {
            sessions.mutableState.value =
                SessionState.Authenticated(replacement)
        }

        assertSame(
            ApiResult.SessionChanged,
            client.execute(request, session)
        )
        assertEquals(
            SessionState.Authenticated(replacement),
            sessions.state.value
        )
    }

    @Test
    fun `otros errores no eliminan una sesion vigente`() = runTest {
        listOf(
            ApiResult.NetworkFailure,
            ApiResult.TimedOut,
            ApiResult.HttpFailure(
                401,
                """{"code":"INVALID_CREDENTIALS","message":"Error."}""",
                null
            ),
            ApiResult.HttpFailure(401, null, null)
        ).forEach { failure ->
            transport.handler = { failure }

            assertSame(failure, client.execute(request, session))
            assertEquals(
                SessionState.Authenticated(session),
                sessions.state.value
            )
        }
    }

    @Test
    fun `actualizar la generacion no sustituye el contexto de sesion`() = runTest {
        transport.handler = {
            sessions.mutableState.value = SessionState.Authenticated(
                session.copy(summaryGeneration = 1)
            )
            ApiResult.Success(200, "{}")
        }

        assertTrue(client.execute(request, session) is ApiResult.Success)
        assertEquals(0, session.summaryGeneration)
    }

    @Test
    fun `cancelar conserva la cancelacion y no invalida la cuenta`() = runTest {
        transport.handler = { awaitCancellation() }
        var returned = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            client.execute(request, session)
            returned = true
        }

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(returned)
        assertEquals(
            SessionState.Authenticated(session),
            sessions.state.value
        )
    }

    private fun unauthenticated() = ApiResult.HttpFailure(
        401,
        """{"code":"UNAUTHENTICATED","message":"Inicia sesión de nuevo."}""",
        null
    )

    private class FakeTransport : ApiTransport {
        val requests = mutableListOf<ApiRequest>()

        var handler: suspend (ApiRequest) -> ApiResult = {
            ApiResult.Success(200, "{}")
        }

        override suspend fun execute(request: ApiRequest): ApiResult {
            requests += request
            return handler(request)
        }
    }

    private class FakeSessions(
        session: SessionSnapshot
    ) : SessionCredentialSource {
        val mutableState = MutableStateFlow<SessionState>(
            SessionState.Authenticated(session)
        )

        override val state: StateFlow<SessionState> = mutableState

        var token: String? = "fixture-token"
        var afterTokenRead: (() -> Unit)? = null
        var beforeInvalidate: (() -> Unit)? = null

        override suspend fun tokenFor(
            expected: SessionSnapshot,
            nowMillis: Long
        ): String? {
            val current = (state.value as? SessionState.Authenticated)
                ?.session

            val result = token.takeIf {
                current?.contextId == expected.contextId &&
                        current.userId == expected.userId &&
                        current.expiresAtMillis > nowMillis
            }

            afterTokenRead?.invoke()
            return result
        }

        override suspend fun invalidate(
            expected: SessionSnapshot
        ): Boolean {
            beforeInvalidate?.invoke()

            val current = (state.value as? SessionState.Authenticated)
                ?.session
                ?: return false

            if (current.contextId != expected.contextId ||
                current.userId != expected.userId
            ) {
                return false
            }

            token = null
            mutableState.value = SessionState.Guest
            return true
        }
    }
}