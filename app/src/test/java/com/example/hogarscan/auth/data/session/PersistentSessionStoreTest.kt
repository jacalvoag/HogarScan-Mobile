package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.data.http.DefaultSessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSessionStoreTest {
    private var now = 1_000L
    private val storage = FakeStorage()
    private val store = PersistentSessionStore(storage) { now }

    @Test
    fun `restaurar sin archivo termina como invitado y no vuelve a leer`() = runTest {
        assertTrue(store.restore())
        assertSame(SessionState.Guest, store.state.value)
        assertFalse(store.restore())
        assertEquals(listOf("read"), storage.operations)
    }

    @Test
    fun `otra instancia restaura la cuenta con un contexto nuevo`() = runTest {
        val first = requireNotNull(store.activate(credentials()))
        val reopened = PersistentSessionStore(storage) { now }

        assertTrue(reopened.restore())

        val restored = active(reopened)

        assertEquals(first.userId, restored.userId)
        assertNotEquals(first.contextId, restored.contextId)
        assertEquals(TOKEN, reopened.tokenFor(restored, now))
    }

    @Test
    fun `restaurar credenciales vencidas limpia el archivo`() = runTest {
        storage.saved = credentials(expiresAt = now)

        assertTrue(store.restore())
        assertNull(storage.saved)
        assertSame(SessionState.Guest, store.state.value)
        assertEquals(listOf("read", "clear"), storage.operations)
    }

    @Test
    fun `una restauracion posterior no reemplaza el login actual`() = runTest {
        val current = requireNotNull(
            store.activate(credentials("account-b"))
        )

        assertFalse(store.restore())
        assertEquals(current, active())
        assertEquals(listOf("write"), storage.operations)
    }

    @Test
    fun `un fallo de lectura bloquea la sesion y exige limpieza antes de activar`() = runTest {
        storage.saved = credentials()
        storage.failRead = true

        assertTrue(
            runCatching { store.restore() }.exceptionOrNull()
                    is SessionStorageException
        )
        assertSame(SessionState.Guest, store.state.value)

        val current = requireNotNull(
            store.activate(credentials("account-b"))
        )

        assertEquals("account-b", current.userId)
        assertEquals(
            listOf("read", "clear", "write"),
            storage.operations
        )
    }

    @Test
    fun `un fallo de escritura no publica otra cuenta aunque el disco cambiara`() = runTest {
        val old = requireNotNull(store.activate(credentials()))
        storage.operations.clear()
        storage.failWriteAfterSave = true

        assertTrue(
            runCatching {
                store.activate(credentials("account-b"))
            }.exceptionOrNull() is SessionStorageException
        )
        assertSame(SessionState.Guest, store.state.value)
        assertNull(store.tokenFor(old, now))

        val next = requireNotNull(
            store.activate(credentials("account-c"))
        )

        assertEquals("account-c", next.userId)
        assertEquals("account-c", storage.saved?.userId)
        assertEquals(
            listOf("write", "clear", "write"),
            storage.operations
        )
    }

    @Test
    fun `una credencial vencida antes o durante la escritura no queda activa`() = runTest {
        store.restore()

        assertNull(store.activate(credentials(expiresAt = now)))
        assertEquals(listOf("read"), storage.operations)

        storage.onWrite = { now = 10_000L }

        assertNull(store.activate(credentials()))
        assertSame(SessionState.Guest, store.state.value)
        assertNull(storage.saved)
    }

    @Test
    fun `una invalidacion anterior no borra la nueva cuenta`() = runTest {
        val old = requireNotNull(store.activate(credentials()))
        val current = requireNotNull(
            store.activate(credentials("account-b"))
        )

        assertFalse(store.invalidate(old))
        assertEquals(current, active())
        assertEquals("account-b", storage.saved?.userId)
        assertEquals(listOf("write", "write"), storage.operations)
    }

    @Test
    fun `invalidar elimina el archivo antes de publicar invitado`() = runTest {
        val current = requireNotNull(store.activate(credentials()))

        storage.onClear = {
            assertEquals(current, active())
        }

        assertTrue(store.invalidate(current))
        assertNull(storage.saved)
        assertSame(SessionState.Guest, store.state.value)
    }

    @Test
    fun `el cliente comunica un fallo al eliminar credenciales`() = runTest {
        val current = requireNotNull(store.activate(credentials()))
        storage.failClear = true

        val transport = ApiTransport {
            ApiResult.HttpFailure(
                401,
                """{"code":"UNAUTHENTICATED","message":"Inicia sesión."}""",
                null
            )
        }
        val client =
            DefaultSessionApiClient(transport, store) { now }

        assertSame(
            ApiResult.SessionStorageFailure,
            client.execute(
                ApiRequest(ApiMethod.GET, "/auth/me"),
                current
            )
        )
        assertSame(SessionState.Guest, store.state.value)
        assertNull(store.tokenFor(current, now))
    }

    @Test
    fun `el cliente comunica un fallo al obtener credenciales`() = runTest {
        val current = requireNotNull(store.activate(credentials()))

        val source = object : SessionCredentialSource by store {
            override suspend fun tokenFor(
                expected: SessionSnapshot,
                nowMillis: Long
            ): String? = throw SessionStorageException(
                IOException("fixture")
            )
        }

        var sent = false
        val client = DefaultSessionApiClient(
            ApiTransport {
                sent = true
                ApiResult.Success(200, "{}")
            },
            source
        ) { now }

        assertSame(
            ApiResult.SessionStorageFailure,
            client.execute(
                ApiRequest(ApiMethod.GET, "/auth/me"),
                current
            )
        )
        assertFalse(sent)
    }

    @Test
    fun `la generacion se persiste sin cambiar contexto ni snapshots anteriores`() = runTest {
        val original = requireNotNull(store.activate(credentials()))

        assertTrue(store.updateSummaryGeneration(original, 2))
        assertEquals(2, storage.saved?.summaryGeneration)
        assertEquals(2, active().summaryGeneration)
        assertEquals(original.contextId, active().contextId)
        assertEquals(0, original.summaryGeneration)

        assertFalse(store.updateSummaryGeneration(original, 1))
        assertTrue(store.updateSummaryGeneration(original, 2))
        assertEquals(listOf("write", "write"), storage.operations)
    }

    @Test
    fun `una generacion de otro contexto o de una sesion vencida no se escribe`() = runTest {
        val old = requireNotNull(store.activate(credentials()))
        val current = requireNotNull(
            store.activate(credentials("account-b"))
        )

        assertFalse(store.updateSummaryGeneration(old, 5))

        now = 10_000L

        assertFalse(store.updateSummaryGeneration(current, 5))
        assertEquals(listOf("write", "write"), storage.operations)
    }

    @Test
    fun `un fallo al persistir la generacion deja la sesion bloqueada`() = runTest {
        val current = requireNotNull(store.activate(credentials()))
        storage.failWriteAfterSave = true

        assertTrue(
            runCatching {
                store.updateSummaryGeneration(current, 2)
            }.exceptionOrNull() is SessionStorageException
        )
        assertSame(SessionState.Guest, store.state.value)
        assertNull(store.tokenFor(current, now))
    }

    @Test
    fun `cancelar una activacion iniciada termina la confirmacion local`() = runTest {
        store.restore()

        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        storage.onWrite = {
            started.complete(Unit)
            release.await()
        }

        var returned = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            store.activate(credentials())
            returned = true
        }

        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(returned)
        assertEquals(storage.saved?.userId, active().userId)
        assertEquals(TOKEN, store.tokenFor(active(), now))
    }

    @Test
    fun `cancelar mientras espera el bloqueo no inicia otra escritura`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        storage.onWrite = {
            started.complete(Unit)
            release.await()
        }

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            store.activate(credentials())
        }

        started.await()

        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            store.activate(credentials("account-b"))
        }

        second.cancelAndJoin()
        release.complete(Unit)
        first.join()

        assertEquals("account-a", active().userId)
        assertEquals(listOf("write"), storage.operations)
    }

    @Test
    fun `cancelar la invalidacion no deja credenciales utilizables`() = runTest {
        val current = requireNotNull(store.activate(credentials()))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        storage.onClear = {
            started.complete(Unit)
            release.await()
        }

        var returned = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            store.invalidate(current)
            returned = true
        }

        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertFalse(returned)
        assertNull(storage.saved)
        assertSame(SessionState.Guest, store.state.value)
    }

    private fun active(
        source: PersistentSessionStore = store
    ): SessionSnapshot =
        (source.state.value as SessionState.Authenticated).session

    private fun credentials(
        userId: String = "account-a",
        expiresAt: Long = 10_000L
    ) = SessionCredentials(
        userId = userId,
        email = "$userId@example.com",
        expiresAtMillis = expiresAt,
        summaryGeneration = 0,
        accessToken = TOKEN
    )

    private class FakeStorage : SessionStorage {
        var saved: SessionCredentials? = null
        val operations = mutableListOf<String>()
        var failRead = false
        var failWriteAfterSave = false
        var failClear = false
        var onWrite: suspend () -> Unit = {}
        var onClear: suspend () -> Unit = {}

        override suspend fun read(): SessionCredentials? {
            operations += "read"

            if (failRead) fail()

            return saved
        }

        override suspend fun write(credentials: SessionCredentials) {
            operations += "write"
            onWrite()
            saved = credentials

            if (failWriteAfterSave) {
                failWriteAfterSave = false
                fail()
            }
        }

        override suspend fun clear() {
            operations += "clear"
            onClear()

            if (failClear) fail()

            saved = null
        }

        private fun fail(): Nothing =
            throw SessionStorageException(IOException("fixture"))
    }

    companion object {
        private val TOKEN = "fixture_a".repeat(5).take(43)
    }
}