package com.example.hogarscan.auth.domain.policy

import com.example.hogarscan.auth.domain.model.AuthFailure
import java.util.Locale

object AuthInputPolicy {
    fun normalizeEmail(email: String): String =
        email.trim().lowercase(Locale.ROOT)

    fun validateLogin(
        email: String,
        password: String
    ): AuthFailure? = validate(email, password, 1)

    fun validateRegistration(
        email: String,
        password: String,
        confirmation: String
    ): AuthFailure? = validate(email, password, 15)
        ?: AuthFailure.PASSWORD_CONFIRMATION_MISMATCH.takeIf {
            password != confirmation
        }

    private fun validate(
        email: String,
        password: String,
        minimum: Int
    ): AuthFailure? {
        if (
            email.length !in 3..254 ||
            email.any(Char::isWhitespace) ||
            !wellFormed(email) ||
            !EMAIL.matches(email)
        ) {
            return AuthFailure.INVALID_EMAIL
        }

        if (
            !wellFormed(password) ||
            password.codePointCount(0, password.length) !in minimum..128
        ) {
            return AuthFailure.INVALID_PASSWORD
        }

        return null
    }

    private fun wellFormed(value: String): Boolean =
        Charsets.UTF_8.newEncoder().canEncode(value)

    private val EMAIL = Regex("[^@\\s]+@[^@\\s]+")
}