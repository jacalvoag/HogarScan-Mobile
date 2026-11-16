package com.example.hogarscan.auth.domain.repository

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReaderTest {

    private val session = SessionSnapshot(
        contextId = "context-a",
        userId = "account-a",
        email = "account-a@example.com",
        expiresAtMillis = 10_000L,
        summaryGeneration = 0
    )

    private val mutableState =
        MutableStateFlow<SessionState>(SessionState.Restoring)

    private val reader = object : SessionReader {
        override val state: StateFlow<SessionState> = mutableState
    }

    @Test
    fun `restaurar o estar como invitado no permite usar una sesion`() {
        listOf(
            SessionState.Restoring,
            SessionState.Guest
        ).forEach { state ->
            mutableState.value = state

            assertFalse(reader.isCurrentSession(session, 1_000L))
        }
    }

    @Test
    fun `reconoce el contexto autenticado mientras no venza`() {
        mutableState.value = SessionState.Authenticated(session)

        assertTrue(reader.isCurrentSession(session, 9_999L))
    }

    @Test
    fun `rechaza la sesion desde el instante de vencimiento`() {
        mutableState.value = SessionState.Authenticated(session)

        assertFalse(reader.isCurrentSession(session, 10_000L))
        assertFalse(reader.isCurrentSession(session, 10_001L))
    }

    @Test
    fun `rechaza una respuesta que pertenece a otra cuenta`() {
        mutableState.value = SessionState.Authenticated(
            session.copy(
                userId = "account-b",
                email = "account-b@example.com"
            )
        )

        assertFalse(reader.isCurrentSession(session, 1_000L))
    }

    @Test
    fun `otro login de la misma cuenta invalida el contexto anterior`() {
        mutableState.value = SessionState.Authenticated(
            session.copy(contextId = "context-b")
        )

        assertFalse(reader.isCurrentSession(session, 1_000L))
    }

    @Test
    fun `cerrar la sesion invalida el contexto capturado`() {
        mutableState.value = SessionState.Authenticated(session)
        assertTrue(reader.isCurrentSession(session, 1_000L))

        mutableState.value = SessionState.Guest

        assertFalse(reader.isCurrentSession(session, 1_000L))
    }

    @Test
    fun `actualizar la generacion no equivale a cambiar de sesion`() {
        mutableState.value = SessionState.Authenticated(
            session.copy(summaryGeneration = 1)
        )

        assertTrue(reader.isCurrentSession(session, 1_000L))
    }
}