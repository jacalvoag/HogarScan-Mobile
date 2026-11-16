package com.example.hogarscan.auth.domain.model

sealed interface SessionState {

    data object Restoring : SessionState

    data object Guest : SessionState

    data class Authenticated(
        val session: SessionSnapshot
    ) : SessionState
}