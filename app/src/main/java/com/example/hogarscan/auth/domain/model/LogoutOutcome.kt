package com.example.hogarscan.auth.domain.model

/**
 * Ambos resultados requieren haber retirado correctamente la sesión local.
 */
enum class LogoutOutcome {
    REMOTE_CONFIRMED,
    REMOTE_UNCONFIRMED
}