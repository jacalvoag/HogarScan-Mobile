package com.example.hogarscan.evaluations.data

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface EvaluationRemoteResult {
    data class Success(val result: EvaluationResult) : EvaluationRemoteResult
    data class Failure(val error: ApiResult) : EvaluationRemoteResult
}

/** No guarda resultados; la persistencia debe comprobar de nuevo el propietario. */
class HttpEvaluationDataSource(private val client: SessionApiClient) {
    suspend fun evaluate(input: EvaluationInput, session: SessionSnapshot): EvaluationRemoteResult {
        require(input.ownerId == "user:${session.userId}") {
            "La evaluación debe pertenecer a la cuenta de la sesión."
        }
        currentCoroutineContext().ensureActive()
        val response = client.execute(
            ApiRequest(ApiMethod.POST, "/evaluations", bodyJson = EvaluationRequestJson.encode(input)),
            session
        )
        currentCoroutineContext().ensureActive()
        if (response !is ApiResult.Success) return EvaluationRemoteResult.Failure(response)
        if (response.statusCode != 200 || response.bodyJson == null) return invalid()
        val result = try {
            EvaluationResponseJson.decode(response.bodyJson)
        } catch (_: IOException) {
            return invalid()
        } catch (_: JsonDataException) {
            return invalid()
        } catch (_: IllegalArgumentException) {
            return invalid()
        } catch (_: DateTimeException) {
            return invalid()
        }
        if (result.possibleCveMatches.any { match ->
                val declared = input.identification
                declared == null || match.vendor != declared.vendor ||
                    match.product != declared.product || match.version != declared.version ||
                    match.identificationSource != declared.source
            }) return invalid()
        return EvaluationRemoteResult.Success(result)
    }

    private fun invalid() = EvaluationRemoteResult.Failure(ApiResult.InvalidResponse)
}
