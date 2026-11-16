package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRegistry(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newContextId: () -> String = {
        UUID.randomUUID().toString()
    }
) : SessionCredentialSource {

    private val mutex = Mutex()
    private val mutableState =
        MutableStateFlow<SessionState>(SessionState.Restoring)

    private var active: ActiveSession? = null

    override val state: StateFlow<SessionState> =
        mutableState.asStateFlow()

    suspend fun activate(
        credentials: SessionCredentials
    ): SessionSnapshot = mutex.withLock {
        require(credentials.expiresAtMillis > nowMillis()) {
            "No se puede activar una sesión vencida."
        }

        activateLocked(credentials)
    }

    suspend fun completeRestoration(
        credentials: SessionCredentials?
    ): Boolean = mutex.withLock {
        if (mutableState.value != SessionState.Restoring) {
            return@withLock false
        }

        if (credentials != null &&
            credentials.expiresAtMillis > nowMillis()
        ) {
            activateLocked(credentials)
        } else {
            active = null
            mutableState.value = SessionState.Guest
        }

        true
    }

    override suspend fun tokenFor(
        expected: SessionSnapshot,
        nowMillis: Long
    ): String? = mutex.withLock {
        val current = active ?: return@withLock null

        if (current.matches(expected) &&
            current.snapshot.expiresAtMillis > nowMillis
        ) {
            current.accessToken
        } else {
            null
        }
    }

    override suspend fun invalidate(
        expected: SessionSnapshot
    ): Boolean = mutex.withLock {
        val current = active ?: return@withLock false

        if (!current.matches(expected)) {
            return@withLock false
        }

        active = null
        mutableState.value = SessionState.Guest
        true
    }

    suspend fun updateSummaryGeneration(
        expected: SessionSnapshot,
        generation: Int
    ): Boolean {
        require(generation >= 0) {
            "La generación no puede ser negativa."
        }

        return mutex.withLock {
            val current = active ?: return@withLock false

            if (!current.matches(expected) ||
                current.snapshot.expiresAtMillis <= nowMillis() ||
                generation < current.snapshot.summaryGeneration
            ) {
                return@withLock false
            }

            val updated = current.snapshot.copy(
                summaryGeneration = generation
            )

            active = ActiveSession(updated, current.accessToken)
            mutableState.value = SessionState.Authenticated(updated)
            true
        }
    }

    private fun activateLocked(
        credentials: SessionCredentials
    ): SessionSnapshot {
        val snapshot = SessionSnapshot(
            contextId = newContextId(),
            userId = credentials.userId,
            email = credentials.email,
            expiresAtMillis = credentials.expiresAtMillis,
            summaryGeneration = credentials.summaryGeneration
        )

        active = ActiveSession(snapshot, credentials.accessToken)
        mutableState.value = SessionState.Authenticated(snapshot)

        return snapshot
    }

    private class ActiveSession(
        val snapshot: SessionSnapshot,
        val accessToken: String
    ) {
        fun matches(expected: SessionSnapshot): Boolean =
            snapshot.contextId == expected.contextId &&
                    snapshot.userId == expected.userId
    }
}