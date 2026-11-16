package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.domain.model.SessionSnapshot

interface AuthSessionStore : SessionCredentialSource {
    suspend fun restore(): Boolean

    suspend fun activate(
        credentials: SessionCredentials
    ): SessionSnapshot?
}