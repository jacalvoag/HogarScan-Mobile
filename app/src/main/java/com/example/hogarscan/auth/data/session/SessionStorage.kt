package com.example.hogarscan.auth.data.session

import java.io.IOException

interface SessionStorage {
    suspend fun read(): SessionCredentials?
    suspend fun write(credentials: SessionCredentials)
    suspend fun clear()
}

class SessionStorageException(cause: Exception) : IOException(
    "No se pudo acceder al almacenamiento protegido de sesión.",
    cause
)