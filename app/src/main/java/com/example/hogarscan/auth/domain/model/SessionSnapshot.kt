package com.example.hogarscan.auth.domain.model

/**
 * Datos de sesión disponibles para las funcionalidades.
 * Las credenciales se gestionan fuera de este modelo.
 */
data class SessionSnapshot(
    val contextId: String,
    val userId: String,
    val email: String,
    val expiresAtMillis: Long,
    val summaryGeneration: Int
) {
    init {
        require(contextId.isNotBlank()) {
            "La sesión debe tener un identificador de contexto."
        }
        require(userId.isNotBlank()) {
            "La sesión debe pertenecer a una cuenta."
        }
        require(email.isNotBlank()) {
            "La cuenta debe tener un correo."
        }
        require(expiresAtMillis > 0L) {
            "La sesión debe tener una fecha de vencimiento válida."
        }
        require(summaryGeneration >= 0) {
            "La generación de resúmenes no puede ser negativa."
        }
    }
}