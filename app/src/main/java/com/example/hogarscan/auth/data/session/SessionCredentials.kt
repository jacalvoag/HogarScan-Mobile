package com.example.hogarscan.auth.data.session

class SessionCredentials(
    val userId: String,
    val email: String,
    val expiresAtMillis: Long,
    val summaryGeneration: Int,
    val accessToken: String
) {
    init {
        require(userId.isNotBlank() && email.isNotBlank()) {
            "Las credenciales deben pertenecer a una cuenta."
        }
        require(expiresAtMillis > 0L && summaryGeneration >= 0) {
            "El vencimiento o la generación de sesión no son válidos."
        }
        require(TOKEN_PATTERN.matches(accessToken)) {
            "El token no tiene el formato esperado."
        }
    }

    companion object {
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43}")
    }
}