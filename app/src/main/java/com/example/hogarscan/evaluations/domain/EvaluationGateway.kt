package com.example.hogarscan.evaluations.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.evaluations.domain.model.EvaluationResult

enum class EvaluationFailure { CONNECTION, TIMEOUT, SESSION, RATE_LIMITED, INVALID_RESPONSE, OTHER }

sealed interface EvaluationOutcome {
    data class Success(val result: EvaluationResult) : EvaluationOutcome
    data class Failure(val reason: EvaluationFailure) : EvaluationOutcome
}

fun interface EvaluationGateway {
    suspend fun evaluate(input: EvaluationInput, session: SessionSnapshot): EvaluationOutcome
}
