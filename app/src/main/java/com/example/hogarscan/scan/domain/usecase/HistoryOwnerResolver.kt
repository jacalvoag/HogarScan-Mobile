package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.auth.domain.repository.isCurrentSession

data class HistoryOwnerContext(
    val ownerId: String,
    val session: SessionSnapshot?
)

class HistoryOwnerResolver(
    private val sessions: SessionReader,
    private val guestOwnerId: suspend () -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /**
     * Devuelve null si todavía no puede obtenerse un propietario válido.
     * Una sesión vencida no se convierte automáticamente en invitado.
     */
    suspend fun resolve(): HistoryOwnerContext? {
        return when (val state = sessions.state.value) {
            SessionState.Restoring -> null

            SessionState.Guest -> {
                val owner = guestOwnerId()
                require(owner.isNotBlank()) {
                    "El historial de invitado debe tener un propietario."
                }

                // Leer el perfil local puede suspender y permitir otro login.
                if (sessions.state.value != SessionState.Guest) {
                    null
                } else {
                    HistoryOwnerContext(
                        ownerId = owner,
                        session = null
                    )
                }
            }

            is SessionState.Authenticated -> {
                val session = state.session
                if (!sessions.isCurrentSession(session, nowMillis())) {
                    null
                } else {
                    HistoryOwnerContext(
                        ownerId = "user:${session.userId}",
                        session = session
                    )
                }
            }
        }
    }

    /**
     * Comprueba el contexto antes de utilizar un resultado.
     * No hace atómica una escritura ni sustituye la autorización del servidor.
     */
    fun isCurrent(context: HistoryOwnerContext): Boolean {
        val session = context.session
        return if (session == null) {
            sessions.state.value == SessionState.Guest
        } else {
            sessions.isCurrentSession(session, nowMillis())
        }
    }
}