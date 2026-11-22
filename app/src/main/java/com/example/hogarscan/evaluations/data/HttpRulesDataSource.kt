package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface RulesRemoteResult {
    data class Success(val rules: EvaluationRules) : RulesRemoteResult
    data class Failure(val error: ApiResult) : RulesRemoteResult
}

/** /rules es pública; no incorpora credenciales ni interpreta observaciones. */
class HttpRulesDataSource(private val transport: ApiTransport) {
    suspend fun getRules(): RulesRemoteResult {
        currentCoroutineContext().ensureActive()
        val response = transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
        currentCoroutineContext().ensureActive()
        if (response !is ApiResult.Success) return RulesRemoteResult.Failure(response)
        if (response.statusCode != 200 || response.bodyJson == null) return invalid()
        val rules = try {
            RulesResponseJson.decode(response.bodyJson)
        } catch (_: IOException) {
            return invalid()
        } catch (_: JsonDataException) {
            return invalid()
        } catch (_: IllegalArgumentException) {
            return invalid()
        } catch (_: ArithmeticException) {
            return invalid()
        } catch (_: DateTimeException) {
            return invalid()
        }
        return RulesRemoteResult.Success(rules)
    }

    private fun invalid() = RulesRemoteResult.Failure(ApiResult.InvalidResponse)
}
