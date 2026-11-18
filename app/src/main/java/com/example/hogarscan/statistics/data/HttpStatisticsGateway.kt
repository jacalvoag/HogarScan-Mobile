package com.example.hogarscan.statistics.data

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.statistics.domain.StatisticsFailure
import com.example.hogarscan.statistics.domain.StatisticsGateway
import com.example.hogarscan.statistics.domain.StatisticsOutcome
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class HttpStatisticsGateway(private val client: SessionApiClient) : StatisticsGateway {
    override suspend fun getStatistics(session: SessionSnapshot): StatisticsOutcome {
        currentCoroutineContext().ensureActive()
        val response = client.execute(ApiRequest(ApiMethod.GET, "/me/stats"), session)
        currentCoroutineContext().ensureActive()
        if (response !is ApiResult.Success) return StatisticsOutcome.Failure(when (response) {
            ApiResult.NetworkFailure, ApiResult.TlsFailure -> StatisticsFailure.CONNECTION
            ApiResult.TimedOut -> StatisticsFailure.TIMEOUT
            ApiResult.SessionChanged, ApiResult.SessionUnavailable, ApiResult.SessionStorageFailure -> StatisticsFailure.SESSION
            ApiResult.InvalidResponse -> StatisticsFailure.INVALID_RESPONSE
            is ApiResult.HttpFailure -> if (response.statusCode == 429) StatisticsFailure.RATE_LIMITED else StatisticsFailure.OTHER
            is ApiResult.Success -> StatisticsFailure.INVALID_RESPONSE
        })
        if (response.statusCode != 200 || response.bodyJson == null) return invalid()
        val statistics = try {
            StatisticsJson.decode(response.bodyJson)
        } catch (_: IOException) { return invalid() }
        catch (_: JsonDataException) { return invalid() }
        catch (_: IllegalArgumentException) { return invalid() }
        catch (_: ArithmeticException) { return invalid() }
        catch (_: DateTimeException) { return invalid() }
        return StatisticsOutcome.Success(statistics)
    }

    private fun invalid() = StatisticsOutcome.Failure(StatisticsFailure.INVALID_RESPONSE)
}
