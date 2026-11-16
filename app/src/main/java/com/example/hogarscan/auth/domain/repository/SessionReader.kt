package com.example.hogarscan.auth.domain.repository

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionReader {
    val state: StateFlow<SessionState>
}

/**
 * Comprueba que el contexto esperado sigue activo y no ha vencido.
 *
 * No comprueba la generación del resumen ni sustituye las validaciones
 * de autorización del servidor.
 */
fun SessionReader.isCurrentSession(
    expected: SessionSnapshot,
    nowMillis: Long
): Boolean {
    val current = (state.value as? SessionState.Authenticated)
        ?.session
        ?: return false

    return current.contextId == expected.contextId &&
            current.userId == expected.userId &&
            current.expiresAtMillis > nowMillis
}