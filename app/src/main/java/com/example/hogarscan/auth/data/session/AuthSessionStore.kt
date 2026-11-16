package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.repository.SessionReader

interface AuthSessionStore : SessionReader {
    suspend fun restore(): Boolean

    suspend fun activate(
        credentials: SessionCredentials
    ): SessionSnapshot?

    suspend fun invalidate(
        expected: SessionSnapshot
    ): Boolean
}