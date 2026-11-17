package com.example.hogarscan.scan.data.http

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import java.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpScanSummaryDataSourceTest {

    private val client = FakeClient()
    private val source = HttpScanSummaryDataSource(client)

    private val session = SessionSnapshot(
        contextId = "context-a",
        userId = USER_A,
        email = "account-a@example.com",
        expiresAtMillis = Long.MAX_VALUE,
        summaryGeneration = 7
    )

    @Test
    fun sendsAuthenticatedRequestAndAcceptsCreationAndIdenticalRetry() =
        runTest {
            val summary = summary()

            listOf(
                201 to true,
                200 to false
            ).forEach { (status, created) ->
                client.response = ApiResult.Success(
                    status,
                    receiptJson(created = created)
                )

                val result = source.send(summary, session)

                assertTrue(result is ScanSummaryRemoteResult.Success)

                val receipt =
                    (result as ScanSummaryRemoteResult.Success).receipt

                assertEquals(SCAN_ID, receipt.scanId)
                assertEquals(7, receipt.summaryGeneration)
                assertEquals(created, receipt.created)
                assertEquals(
                    Instant.parse(DATE).toEpochMilli(),
                    receipt.receivedAtMillis
                )

                val request = client.requests.last()

                assertEquals(ApiMethod.POST, request.method)
                assertEquals("/me/scans", request.path)
                assertTrue(request.queryParameters.isEmpty())
                assertNull(request.bearerToken)
                assertSame(session, client.sessions.last())
            }

            assertEquals(2, client.requests.size)
            assertEquals(
                client.requests.first().bodyJson,
                client.requests.last().bodyJson
            )
        }

    @Test
    fun rejectsUnexpectedStatusAndInconsistentCreatedFlag() = runTest {
        listOf(
            200 to true,
            201 to false,
            202 to true,
            204 to false
        ).forEach { (status, created) ->
            client.response = ApiResult.Success(
                status,
                receiptJson(created = created)
            )

            assertFailure(
                source.send(summary(), session),
                ApiResult.InvalidResponse
            )
        }
    }

    @Test
    fun rejectsReceiptsForAnotherScanOrGeneration() = runTest {
        listOf(
            receiptJson(scanId = OTHER_SCAN_ID),
            receiptJson(generation = 8)
        ).forEach { body ->
            client.response = ApiResult.Success(201, body)

            assertFailure(
                source.send(summary(), session),
                ApiResult.InvalidResponse
            )
        }
    }

    @Test
    fun rejectsMissingOrMalformedSuccessfulResponse() = runTest {
        listOf(
            null,
            "",
            "{}",
            "[]",
            "null",
            "{invalid}"
        ).forEach { body ->
            client.response = ApiResult.Success(201, body)

            assertFailure(
                source.send(summary(), session),
                ApiResult.InvalidResponse
            )
        }
    }

    @Test
    fun preservesHttpTransportAndSessionFailures() = runTest {
        listOf(
            ApiResult.HttpFailure(
                409,
                """{"code":"SUMMARY_CONFLICT","message":"Conflicto."}""",
                null
            ),
            ApiResult.HttpFailure(401, "{}", null),
            ApiResult.HttpFailure(429, "{}", 60L),
            ApiResult.HttpFailure(503, "{}", null),
            ApiResult.NetworkFailure,
            ApiResult.TimedOut,
            ApiResult.TlsFailure,
            ApiResult.InvalidResponse,
            ApiResult.SessionUnavailable,
            ApiResult.SessionChanged,
            ApiResult.SessionStorageFailure
        ).forEach { failure ->
            client.response = failure

            assertFailure(
                source.send(summary(), session),
                failure
            )
        }
    }

    @Test
    fun rejectsAnotherAccountOrGenerationBeforeCallingClient() = runTest {
        listOf(
            session.copy(userId = USER_B),
            session.copy(summaryGeneration = 8)
        ).forEach { unexpectedSession ->
            val failure = runCatching {
                source.send(summary(), unexpectedSession)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        }

        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun cancellationIsNotConvertedIntoRemoteFailure() = runTest {
        client.handler = { awaitCancellation() }
        var returned = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            source.send(summary(), session)
            returned = true
        }

        assertEquals(1, client.requests.size)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    private fun assertFailure(
        result: ScanSummaryRemoteResult,
        expected: ApiResult
    ) {
        assertTrue(result is ScanSummaryRemoteResult.Failure)

        assertSame(
            expected,
            (result as ScanSummaryRemoteResult.Failure).error
        )
    }

    private fun summary() = FrozenScanSummary(
        userId = USER_A,
        scanId = SCAN_ID,
        summaryGeneration = 7,
        startedAtMillis = 2_000L,
        finishedAtMillis = 1_000L,
        addressesScanned = 2,
        addressesResponded = 1,
        acceptedServices = 1,
        findingsByCategory = FindingCounts(
            tcpServiceAccessible = 1,
            protocolIdentified = 2,
            preventiveReview = 3,
            possibleCveMatch = 4,
            insufficientEvidence = 5
        )
    )

    private fun receiptJson(
        scanId: String = SCAN_ID,
        generation: Int = 7,
        created: Boolean = true
    ): String =
        """{"scan_id":"$scanId","summary_generation":$generation,"received_at":"$DATE","created":$created}"""

    private class FakeClient : SessionApiClient {
        val requests = mutableListOf<ApiRequest>()
        val sessions = mutableListOf<SessionSnapshot>()

        var response: ApiResult = ApiResult.InvalidResponse
        var handler: (suspend () -> ApiResult)? = null

        override suspend fun execute(
            request: ApiRequest,
            expectedSession: SessionSnapshot
        ): ApiResult {
            requests += request
            sessions += expectedSession

            return handler?.invoke() ?: response
        }
    }

    private companion object {
        const val USER_A = "0f136c4e-447b-4970-8189-a38c29cb3421"
        const val USER_B = "a7842f53-8a2a-4634-b99e-1dfdb4c468be"
        const val SCAN_ID = "6ce5e6b1-9509-48a7-bc9d-f0f872364726"
        const val OTHER_SCAN_ID =
            "00000000-0000-4000-8000-000000000001"
        const val DATE = "2026-11-16T01:02:03.123Z"
    }
}