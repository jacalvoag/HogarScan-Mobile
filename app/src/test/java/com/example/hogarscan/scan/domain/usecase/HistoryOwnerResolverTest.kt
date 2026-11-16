package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryOwnerResolverTest {

    private val state =
        MutableStateFlow<SessionState>(SessionState.Restoring)

    private val sessions = object : SessionReader {
        override val state: StateFlow<SessionState> =
            this@HistoryOwnerResolverTest.state
    }

    private val session = SessionSnapshot(
        contextId = "context-a",
        userId = "account-a",
        email = "account-a@example.com",
        expiresAtMillis = 10_000L,
        summaryGeneration = 0
    )

    private var guestReads = 0
    private var now = 1_000L

    private val resolver = HistoryOwnerResolver(
        sessions = sessions,
        guestOwnerId = {
            guestReads++
            "local:existing-profile"
        },
        nowMillis = { now }
    )

    @Test
    fun `restaurar no utiliza el historial del invitado`() = runTest {
        assertNull(resolver.resolve())
        assertEquals(0, guestReads)
    }

    @Test
    fun `invitado conserva su propietario local existente`() = runTest {
        state.value = SessionState.Guest

        val context = checkNotNull(resolver.resolve())

        assertEquals("local:existing-profile", context.ownerId)
        assertNull(context.session)
        assertTrue(resolver.isCurrent(context))

        state.value = SessionState.Authenticated(session)

        assertFalse(resolver.isCurrent(context))
    }

    @Test
    fun `otro login conserva el propietario pero invalida el contexto anterior`() =
        runTest {
            state.value = SessionState.Authenticated(session)
            val previous = checkNotNull(resolver.resolve())

            state.value = SessionState.Authenticated(
                session.copy(contextId = "context-b")
            )
            val current = checkNotNull(resolver.resolve())

            assertEquals("user:account-a", current.ownerId)
            assertEquals(previous.ownerId, current.ownerId)
            assertFalse(resolver.isCurrent(previous))
            assertTrue(resolver.isCurrent(current))
            assertEquals(0, guestReads)
        }

    @Test
    fun `otra cuenta recibe otro propietario y logout invalida el contexto`() =
        runTest {
            state.value = SessionState.Authenticated(session)
            val first = checkNotNull(resolver.resolve())

            state.value = SessionState.Authenticated(
                session.copy(
                    contextId = "context-b",
                    userId = "account-b",
                    email = "account-b@example.com"
                )
            )
            val second = checkNotNull(resolver.resolve())

            assertNotEquals(first.ownerId, second.ownerId)
            assertFalse(resolver.isCurrent(first))
            assertTrue(resolver.isCurrent(second))

            state.value = SessionState.Guest

            assertFalse(resolver.isCurrent(second))
        }

    @Test
    fun `vencer la sesion no asigna el historial al invitado`() = runTest {
        state.value = SessionState.Authenticated(session)
        val context = checkNotNull(resolver.resolve())

        now = session.expiresAtMillis

        assertNull(resolver.resolve())
        assertFalse(resolver.isCurrent(context))
        assertEquals(0, guestReads)
    }

    @Test
    fun `login durante la lectura local descarta el propietario de invitado`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val delayedResolver = HistoryOwnerResolver(
                sessions = sessions,
                guestOwnerId = {
                    started.complete(Unit)
                    release.await()
                    "local:existing-profile"
                },
                nowMillis = { now }
            )

            state.value = SessionState.Guest

            val pending = async {
                delayedResolver.resolve()
            }

            started.await()
            state.value = SessionState.Authenticated(session)
            release.complete(Unit)

            assertNull(pending.await())
            assertEquals(
                "user:account-a",
                checkNotNull(delayedResolver.resolve()).ownerId
            )
        }
}