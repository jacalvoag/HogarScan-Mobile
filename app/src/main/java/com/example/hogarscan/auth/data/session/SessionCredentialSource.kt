package com.example.hogarscan.auth.data.session

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.repository.SessionReader

interface SessionCredentialSource : SessionReader {

    /**
     * Lee la credencial solo si cuenta y contexto coinciden y no ha vencido.
     * La comprobación y la lectura deben ser atómicas respecto a cambios de sesión.
     */
    suspend fun tokenFor(
        expected: SessionSnapshot,
        nowMillis: Long
    ): String?

    /**
     * Invalida únicamente la cuenta y contexto esperados.
     * Comparación e invalidación deben ser atómicas.
     * Devuelve false si cambiaron.
     */
    suspend fun invalidate(expected: SessionSnapshot): Boolean
}