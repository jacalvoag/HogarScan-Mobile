package com.example.hogarscan.scan.data.sync

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.shared.data.http.ApiErrorDecoder
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer

sealed interface DeleteSummariesResult {
    data class Deleted(val deletedCount: Long, val generation: Int) : DeleteSummariesResult
    data class AlreadyAdvanced(val generation: Int) : DeleteSummariesResult
    data object NewConfirmationRequired : DeleteSummariesResult
    data object SessionChanged : DeleteSummariesResult
    data class Failure(val error: ApiResult) : DeleteSummariesResult
}

/** Una llamada representa una intención confirmada; nunca reintenta con otra generación. */
class DeleteScanSummaries(
    private val client: SessionApiClient,
    private val repository: ScanSummaryRepository,
    private val updateGeneration: suspend (SessionSnapshot, Int) -> Boolean
) {
    suspend fun delete(expected: SessionSnapshot): DeleteSummariesResult {
        currentCoroutineContext().ensureActive()
        val response = client.execute(ApiRequest(ApiMethod.DELETE, "/me/scans",
            queryParameters = mapOf("summary_generation" to expected.summaryGeneration.toString())), expected)
        currentCoroutineContext().ensureActive()
        if (response is ApiResult.HttpFailure && response.statusCode == 409) {
            val error = ApiErrorDecoder.decode(response)
            val generation = error.currentSummaryGeneration
            return when {
                error.code == "STALE_SUMMARY_GENERATION" && generation != null && generation > expected.summaryGeneration -> {
                    if (!updateGeneration(expected, generation)) DeleteSummariesResult.SessionChanged
                    else { repository.markOlderPendingObsolete(expected.userId, generation); DeleteSummariesResult.AlreadyAdvanced(generation) }
                }
                error.code == "INVALID_SUMMARY_GENERATION" && generation != null && generation < expected.summaryGeneration -> DeleteSummariesResult.NewConfirmationRequired
                else -> DeleteSummariesResult.Failure(ApiResult.InvalidResponse)
            }
        }
        if (response !is ApiResult.Success) return DeleteSummariesResult.Failure(response)
        if (response.statusCode != 200 || response.bodyJson == null) return invalid()
        val receipt = try { decodeReceipt(response.bodyJson) }
        catch (_: IOException) { return invalid() }
        catch (_: JsonDataException) { return invalid() }
        catch (_: IllegalArgumentException) { return invalid() }
        catch (_: ArithmeticException) { return invalid() }
        catch (_: DateTimeException) { return invalid() }
        if (expected.summaryGeneration == Int.MAX_VALUE || receipt.second != expected.summaryGeneration + 1) return invalid()
        if (!updateGeneration(expected, receipt.second)) return DeleteSummariesResult.SessionChanged
        repository.markOlderPendingObsolete(expected.userId, receipt.second)
        return DeleteSummariesResult.Deleted(receipt.first, receipt.second)
    }

    private fun invalid() = DeleteSummariesResult.Failure(ApiResult.InvalidResponse)

    private fun decodeReceipt(json: String): Pair<Long, Int> = JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
        var count: Long? = null
        var generation: Int? = null
        var date: String? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(seen.add(name))
            when (name) {
                "deleted_count" -> { require(reader.peek() == JsonReader.Token.NUMBER); count = reader.nextString().toBigDecimal().longValueExact().also { require(it >= 0) } }
                "summary_generation" -> { require(reader.peek() == JsonReader.Token.NUMBER); generation = reader.nextString().toBigDecimal().intValueExact().also { require(it > 0) } }
                "deleted_at" -> { require(reader.peek() == JsonReader.Token.STRING); date = reader.nextString().also { UtcTimestamp.parseMillis(it) } }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(reader.peek() == JsonReader.Token.END_DOCUMENT)
        requireNotNull(date)
        requireNotNull(count) to requireNotNull(generation)
    }
}
