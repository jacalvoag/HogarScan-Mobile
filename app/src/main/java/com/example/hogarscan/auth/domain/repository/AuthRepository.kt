package com.example.hogarscan.auth.domain.repository

import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.AuthUser
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.LogoutOutcome

interface AuthRepository {
    suspend fun restoreSession(): AuthResult<Unit>

    suspend fun register(
        email: String,
        password: String,
        confirmation: String
    ): AuthResult<AuthUser>

    suspend fun login(
        email: String,
        password: String
    ): AuthResult<SessionSnapshot>

    suspend fun logout(
        expected: SessionSnapshot
    ): AuthResult<LogoutOutcome>
}