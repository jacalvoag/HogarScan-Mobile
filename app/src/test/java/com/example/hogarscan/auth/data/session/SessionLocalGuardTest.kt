package com.example.hogarscan.auth.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLocalGuardTest {
    private val storage = object : SessionStorage {
        override suspend fun read(): SessionCredentials? = null
        override suspend fun write(credentials: SessionCredentials) = Unit
        override suspend fun clear() = Unit
    }
    private val store = PersistentSessionStore(storage) { 1 }
    private fun credentials(user: String) = SessionCredentials(user, "$user@example.com", 1000, 1, "a".repeat(43))

    @Test fun `escritura local y cambio de cuenta no se intercalan`() = runTest {
        val current = requireNotNull(store.activate(credentials("a")))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val write = async { store.withCurrentSession(current) { started.complete(Unit); release.await(); "saved" } }
        started.await()
        val change = async { store.activate(credentials("b")) }
        runCurrent()
        assertFalse(change.isCompleted)
        release.complete(Unit)
        assertEquals("saved", write.await())
        assertEquals("b", change.await()?.userId)
        assertNull(store.withCurrentSession(current) { "should-not-run" })
    }

    @Test fun `rechaza generacion anterior`() = runTest {
        val current = requireNotNull(store.activate(credentials("a")))
        assertTrue(store.updateSummaryGeneration(current, 2))
        assertNull(store.withCurrentSession(current) { "should-not-run" })
    }
}
