package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PersistentSessionStore(
    private val storage: SessionStorage,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AuthSessionStore {

    private val mutex = Mutex()
    private val registry = SessionRegistry(nowMillis = nowMillis)
    private var cleanupRequired = false

    override val state: StateFlow<SessionState> = registry.state

    override suspend fun restore(): Boolean = mutex.withLock {
        if (state.value != SessionState.Restoring) {
            return@withLock false
        }

        val credentials = try {
            storage.read()
        } catch (error: SessionStorageException) {
            withContext(NonCancellable) {
                storageFailed()
            }
            throw error
        }

        if (credentials != null &&
            credentials.expiresAtMillis <= nowMillis()
        ) {
            commit {
                storage.clear()
                registry.completeRestoration(null)
            }
        } else {
            registry.completeRestoration(credentials)
        }
    }

    override suspend fun activate(
        credentials: SessionCredentials
    ): SessionSnapshot? = mutex.withLock {
        if (credentials.expiresAtMillis <= nowMillis()) {
            return@withLock null
        }

        commit {
            if (cleanupRequired) {
                storage.clear()
                cleanupRequired = false
            }

            storage.write(credentials)

            try {
                registry.activate(credentials)
            } catch (error: IllegalArgumentException) {
                storage.clear()
                forgetActive()

                if (credentials.expiresAtMillis <= nowMillis()) {
                    null
                } else {
                    throw error
                }
            }
        }
    }

    override suspend fun tokenFor(
        expected: SessionSnapshot,
        nowMillis: Long
    ): String? = mutex.withLock {
        registry.tokenFor(expected, nowMillis)
    }

    override suspend fun invalidate(
        expected: SessionSnapshot
    ): Boolean = mutex.withLock {
        val current = currentFor(expected)
            ?: return@withLock false

        commit {
            storage.clear()
            cleanupRequired = false
            registry.invalidate(current)
        }
    }

    suspend fun updateSummaryGeneration(
        expected: SessionSnapshot,
        generation: Int
    ): Boolean {
        require(generation >= 0) {
            "La generación no puede ser negativa."
        }

        return mutex.withLock {
            val current = currentFor(expected)
                ?: return@withLock false

            if (current.expiresAtMillis <= nowMillis() ||
                generation < current.summaryGeneration
            ) {
                return@withLock false
            }

            if (generation == current.summaryGeneration) {
                return@withLock true
            }

            val token = registry.tokenFor(current, nowMillis())
                ?: return@withLock false

            commit {
                storage.write(
                    SessionCredentials(
                        userId = current.userId,
                        email = current.email,
                        expiresAtMillis = current.expiresAtMillis,
                        summaryGeneration = generation,
                        accessToken = token
                    )
                )

                val updated = registry.updateSummaryGeneration(
                    current,
                    generation
                )

                if (!updated) {
                    storage.clear()
                    forgetActive()
                }

                updated
            }
        }
    }

    private fun currentFor(
        expected: SessionSnapshot
    ): SessionSnapshot? {
        val current =
            (state.value as? SessionState.Authenticated)?.session
                ?: return null

        return current.takeIf {
            it.contextId == expected.contextId &&
                    it.userId == expected.userId
        }
    }

    private suspend fun forgetActive() {
        val current =
            (state.value as? SessionState.Authenticated)?.session

        if (current != null) {
            registry.invalidate(current)
        } else {
            registry.completeRestoration(null)
        }
    }

    private suspend fun storageFailed() {
        cleanupRequired = true
        forgetActive()
    }

    private suspend fun <T> commit(
        block: suspend () -> T
    ): T {
        currentCoroutineContext().ensureActive()

        val result = withContext(NonCancellable) {
            try {
                block()
            } catch (error: SessionStorageException) {
                storageFailed()
                throw error
            }
        }

        currentCoroutineContext().ensureActive()
        return result
    }
}