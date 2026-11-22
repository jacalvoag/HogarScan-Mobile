package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.data.http.DefaultSessionApiClient
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRegistryTest {
    private var now = 1_000L
    private var sequence = 0

    private val registry = SessionRegistry(
        nowMillis = { now },
        newContextId = { "context-${++sequence}" }
    )

    @Test
    fun `inicia restaurando y permite terminar sin credenciales`() = runTest {
        assertSame(SessionState.Restoring, registry.state.value)

        assertTrue(registry.completeRestoration(null))
        assertSame(SessionState.Guest, registry.state.value)

        assertFalse(registry.completeRestoration(credentials()))
    }

    @Test
    fun `restaura credenciales vigentes con un contexto local`() = runTest {
        assertTrue(registry.completeRestoration(credentials()))

        val snapshot =
            (registry.state.value as SessionState.Authenticated).session

        assertEquals("account-a", snapshot.userId)
        assertEquals("context-1", snapshot.contextId)
        assertEquals(TOKEN_A, registry.tokenFor(snapshot, now))
    }

    @Test
    fun `credenciales restauradas vencidas dejan el perfil invitado`() = runTest {
        assertTrue(
            registry.completeRestoration(
                credentials(expiresAt = now)
            )
        )

        assertSame(SessionState.Guest, registry.state.value)
    }

    @Test
    fun `una restauracion tardia no reemplaza una sesion mas reciente`() = runTest {
        val current = registry.activate(
            credentials(
                userId = "account-b",
                token = TOKEN_B
            )
        )

        assertFalse(registry.completeRestoration(credentials()))
        assertEquals(
            SessionState.Authenticated(current),
            registry.state.value
        )
        assertEquals(TOKEN_B, registry.tokenFor(current, now))
    }

    @Test
    fun `cada login de la misma cuenta recibe un contexto distinto`() = runTest {
        val first = registry.activate(credentials())
        val second = registry.activate(credentials(token = TOKEN_B))

        assertNotEquals(first.contextId, second.contextId)
        assertNull(registry.tokenFor(first, now))
        assertEquals(TOKEN_B, registry.tokenFor(second, now))

        assertTrue(registry.invalidate(second))

        val third = registry.activate(credentials())

        assertNotEquals(second.contextId, third.contextId)
    }

    @Test
    fun `no entrega credenciales a otra cuenta ni a una sesion vencida`() = runTest {
        val snapshot = registry.activate(credentials())

        assertNull(
            registry.tokenFor(
                snapshot.copy(userId = "account-b"),
                now
            )
        )
        assertNull(registry.tokenFor(snapshot, 10_000L))
        assertNull(registry.tokenFor(snapshot, 10_001L))
        assertEquals(TOKEN_A, registry.tokenFor(snapshot, now))
    }

    @Test
    fun `invalidar un contexto anterior conserva el login mas reciente`() = runTest {
        val old = registry.activate(credentials())
        val current = registry.activate(credentials(token = TOKEN_B))

        assertFalse(registry.invalidate(old))
        assertEquals(TOKEN_B, registry.tokenFor(current, now))

        assertTrue(registry.invalidate(current))
        assertSame(SessionState.Guest, registry.state.value)
        assertNull(registry.tokenFor(current, now))
    }

    @Test
    fun `login e invalidacion concurrentes conservan la nueva sesion`() = runTest {
        repeat(30) {
            val old = registry.activate(credentials())

            val login = async(Dispatchers.Default) {
                registry.activate(credentials(token = TOKEN_B))
            }
            val logout = async(Dispatchers.Default) {
                registry.invalidate(old)
            }

            val current = login.await()
            logout.await()

            assertEquals(
                SessionState.Authenticated(current),
                registry.state.value
            )
            assertEquals(TOKEN_B, registry.tokenFor(current, now))
        }
    }

    @Test
    fun `una activacion vencida no reemplaza la cuenta vigente`() = runTest {
        val current = registry.activate(credentials())

        val failure = runCatching {
            registry.activate(credentials(expiresAt = now))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            SessionState.Authenticated(current),
            registry.state.value
        )
    }

    @Test
    fun `actualiza la generacion sin cambiar contexto ni token`() = runTest {
        val original = registry.activate(credentials())

        assertTrue(registry.updateSummaryGeneration(original, 2))

        val updated =
            (registry.state.value as SessionState.Authenticated).session

        assertEquals(original.contextId, updated.contextId)
        assertEquals(2, updated.summaryGeneration)
        assertEquals(0, original.summaryGeneration)
        assertEquals(TOKEN_A, registry.tokenFor(original, now))

        assertFalse(registry.updateSummaryGeneration(original, 1))
        assertEquals(
            SessionState.Authenticated(updated),
            registry.state.value
        )
    }

    @Test
    fun `una respuesta de otro contexto no actualiza la generacion`() = runTest {
        val old = registry.activate(credentials())
        val current = registry.activate(credentials(token = TOKEN_B))

        assertFalse(registry.updateSummaryGeneration(old, 5))

        now = 10_000L

        assertFalse(registry.updateSummaryGeneration(current, 5))
        assertEquals(
            0,
            (registry.state.value as SessionState.Authenticated)
                .session.summaryGeneration
        )
    }

    @Test
    fun `el cliente autenticado utiliza el registro real de sesion`() = runTest {
        val snapshot = registry.activate(credentials())

        val transport = ApiTransport { request ->
            assertEquals(TOKEN_A, request.bearerToken)

            ApiResult.HttpFailure(
                401,
                """{"code":"UNAUTHENTICATED","message":"Inicia sesión."}""",
                null
            )
        }

        val client =
            DefaultSessionApiClient(transport, registry) { now }

        assertSame(
            ApiResult.SessionUnavailable,
            client.execute(
                ApiRequest(ApiMethod.GET, "/auth/me"),
                snapshot
            )
        )
        assertSame(SessionState.Guest, registry.state.value)
    }

    @Test
    fun `rechaza tokens con un formato distinto al contrato`() {
        listOf(
            "",
            " ",
            "x".repeat(42),
            "x".repeat(44),
            "!".repeat(43)
        ).forEach { token ->
            assertThrows(IllegalArgumentException::class.java) {
                credentials(token = token)
            }
        }
    }

    private fun credentials(
        userId: String = "account-a",
        token: String = TOKEN_A,
        expiresAt: Long = 10_000L
    ) = SessionCredentials(
        userId = userId,
        email = "$userId@example.com",
        expiresAtMillis = expiresAt,
        summaryGeneration = 0,
        accessToken = token
    )

    companion object {
        private val TOKEN_A = "fixture_a".repeat(5).take(43)
        private val TOKEN_B = "fixture_b".repeat(5).take(43)
    }
}