package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.EvaluationFailure
import com.example.hogarscan.evaluations.domain.EvaluationGateway
import com.example.hogarscan.evaluations.domain.EvaluationOutcome
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.shared.data.http.ApiResult

class DefaultEvaluationGateway(private val remote: HttpEvaluationDataSource) : EvaluationGateway {
    override suspend fun evaluate(input: EvaluationInput, session: SessionSnapshot): EvaluationOutcome =
        when (val response = remote.evaluate(input, session)) {
            is EvaluationRemoteResult.Success -> EvaluationOutcome.Success(response.result)
            is EvaluationRemoteResult.Failure -> EvaluationOutcome.Failure(when (val error = response.error) {
                ApiResult.NetworkFailure, ApiResult.TlsFailure -> EvaluationFailure.CONNECTION
                ApiResult.TimedOut -> EvaluationFailure.TIMEOUT
                ApiResult.SessionChanged, ApiResult.SessionUnavailable, ApiResult.SessionStorageFailure -> EvaluationFailure.SESSION
                ApiResult.InvalidResponse -> EvaluationFailure.INVALID_RESPONSE
                is ApiResult.HttpFailure -> if (error.statusCode == 429) EvaluationFailure.RATE_LIMITED else EvaluationFailure.OTHER
                is ApiResult.Success -> EvaluationFailure.INVALID_RESPONSE
            })
        }
}
