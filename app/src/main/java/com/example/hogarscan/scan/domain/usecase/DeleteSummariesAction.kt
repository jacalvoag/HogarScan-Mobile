package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.auth.domain.model.SessionSnapshot

fun interface DeleteSummariesAction {
    suspend fun delete(expected: SessionSnapshot): DeleteSummariesOutcome
}

enum class DeleteSummariesOutcome {
    DELETED, ALREADY_ADVANCED, NEW_CONFIRMATION_REQUIRED, SESSION_CHANGED, UNCONFIRMED
}
