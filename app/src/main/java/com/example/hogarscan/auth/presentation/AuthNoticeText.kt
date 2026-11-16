package com.example.hogarscan.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.hogarscan.R
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.LogoutOutcome

@Composable
internal fun AuthNotice.message(): String = when (this) {
    AuthNotice.AccountCreated ->
        stringResource(R.string.auth_account_created)

    is AuthNotice.LoggedOut -> stringResource(
        if (outcome == LogoutOutcome.REMOTE_CONFIRMED) {
            R.string.auth_logout_confirmed
        } else {
            R.string.auth_logout_unconfirmed
        }
    )

    is AuthNotice.Failure -> {
        val message = stringResource(
            when (reason) {
                AuthFailure.INVALID_EMAIL -> R.string.auth_error_email
                AuthFailure.INVALID_PASSWORD -> R.string.auth_error_password
                AuthFailure.PASSWORD_CONFIRMATION_MISMATCH ->
                    R.string.auth_error_confirmation

                AuthFailure.INVALID_INPUT -> R.string.auth_error_input
                AuthFailure.INVALID_CREDENTIALS -> R.string.auth_error_credentials
                AuthFailure.EMAIL_UNAVAILABLE -> R.string.auth_error_email_unavailable
                AuthFailure.RATE_LIMITED -> R.string.auth_error_rate_limit
                AuthFailure.NETWORK -> R.string.auth_error_network
                AuthFailure.TIMEOUT -> R.string.auth_error_timeout
                AuthFailure.TLS -> R.string.auth_error_tls
                AuthFailure.INVALID_RESPONSE -> R.string.auth_error_response
                AuthFailure.STORAGE -> R.string.auth_error_storage
                AuthFailure.SERVICE_UNAVAILABLE -> R.string.auth_error_service
                AuthFailure.BUSY -> R.string.auth_error_busy
                AuthFailure.SESSION_NOT_READY -> R.string.auth_error_restoring
                AuthFailure.SESSION_ALREADY_ACTIVE -> R.string.auth_error_active
                AuthFailure.SESSION_EXPIRED -> R.string.auth_error_expired
                AuthFailure.SESSION_CHANGED -> R.string.auth_error_changed
            }
        )

        if (
            reason == AuthFailure.RATE_LIMITED &&
            retryAfterSeconds != null &&
            retryAfterSeconds > 0
        ) {
            val retryMessage = stringResource(
                R.string.auth_retry_after,
                retryAfterSeconds
            )
            "$message $retryMessage"
        } else {
            message
        }
    }
}