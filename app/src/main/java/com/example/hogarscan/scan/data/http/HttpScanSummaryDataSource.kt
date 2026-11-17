package com.example.hogarscan.scan.data.http

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class HttpScanSummaryDataSource(
    private val client: SessionApiClient
) {

    suspend fun send(
        summary: FrozenScanSummary,
        expectedSession: SessionSnapshot
    ): ScanSummaryRemoteResult {
        require(summary.userId == expectedSession.userId.lowercase()) {
            "El resumen debe pertenecer a la cuenta de la sesión."
        }
        require(
            summary.summaryGeneration == expectedSession.summaryGeneration
        ) {
            "El resumen debe pertenecer a la generación de la sesión."
        }

        currentCoroutineContext().ensureActive()

        val result = client.execute(
            request = ApiRequest(
                method = ApiMethod.POST,
                path = "/me/scans",
                bodyJson = ScanSummaryJsonCodec.encode(summary)
            ),
            expectedSession = expectedSession
        )

        currentCoroutineContext().ensureActive()

        if (result !is ApiResult.Success) {
            return ScanSummaryRemoteResult.Failure(result)
        }

        val json = result.bodyJson

        if (result.statusCode !in setOf(200, 201) || json == null) {
            return invalidResponse()
        }

        val receipt = try {
            ScanSummaryJsonCodec.decodeReceipt(json)
        } catch (_: IOException) {
            return invalidResponse()
        } catch (_: JsonDataException) {
            return invalidResponse()
        } catch (_: IllegalArgumentException) {
            return invalidResponse()
        } catch (_: DateTimeException) {
            return invalidResponse()
        } catch (_: ArithmeticException) {
            return invalidResponse()
        }

        if (
            receipt.scanId != summary.scanId ||
            receipt.summaryGeneration != summary.summaryGeneration ||
            receipt.created != (result.statusCode == 201)
        ) {
            return invalidResponse()
        }

        return ScanSummaryRemoteResult.Success(receipt)
    }

    private fun invalidResponse(): ScanSummaryRemoteResult.Failure =
        ScanSummaryRemoteResult.Failure(ApiResult.InvalidResponse)
}