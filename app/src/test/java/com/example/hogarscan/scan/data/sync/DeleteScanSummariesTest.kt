package com.example.hogarscan.scan.data.sync

import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DeleteScanSummariesTest {
    private val session = SessionSnapshot("ctx", "user", "a@example.com", Long.MAX_VALUE, 3)
    private var obsolete: Pair<String, Int>? = null
    private val repository = object : ScanSummaryRepository {
        override suspend fun enqueue(summary: FrozenScanSummary) = false
        override suspend fun findPending(userId: String, generation: Int) = emptyList<FrozenScanSummary>()
        override suspend fun markSynced(summary: FrozenScanSummary, receivedAtMillis: Long) = false
        override suspend fun markConflict(summary: FrozenScanSummary) = false
        override suspend fun markOlderPendingObsolete(userId: String, currentGeneration: Int): Int {
            obsolete = userId to currentGeneration
            return 0
        }
    }
    private val success = """{"deleted_count":2,"summary_generation":4,"deleted_at":"2026-11-11T00:00:00Z"}"""

    @Test fun `envia generacion confirmada y actualiza pendientes`() = runTest {
        var generation = 0
        val action = DeleteScanSummaries(SessionApiClient { request, expected ->
            assertEquals(ApiMethod.DELETE, request.method)
            assertEquals("/me/scans", request.path)
            assertEquals(mapOf("summary_generation" to "3"), request.queryParameters)
            assertNull(request.bodyJson)
            assertEquals(session, expected)
            ApiResult.Success(200, success)
        }, repository) { _, value -> generation = value; true }
        assertEquals(DeleteSummariesResult.Deleted(2, 4), action.delete(session))
        assertEquals(4, generation)
        assertEquals("user" to 4, obsolete)
    }

    @Test fun `generacion antigua no repite borrado y marca pendientes obsoletos`() = runTest {
        var calls = 0
        val error = """{"code":"STALE_SUMMARY_GENERATION","message":"Anterior","details":{"current_summary_generation":5}}"""
        val action = DeleteScanSummaries(SessionApiClient { _, _ -> calls++; ApiResult.HttpFailure(409, error, null) }, repository) { _, _ -> true }
        assertEquals(DeleteSummariesResult.AlreadyAdvanced(5), action.delete(session))
        assertEquals(1, calls)
        assertEquals("user" to 5, obsolete)
    }

    @Test fun `respuesta invalida o sesion cambiada no modifica pendientes`() = runTest {
        val malformed = DeleteScanSummaries(SessionApiClient { _, _ -> ApiResult.Success(200, success.replace("\"summary_generation\":4", "\"summary_generation\":5")) }, repository) { _, _ -> true }
        assertEquals(ApiResult.InvalidResponse, (malformed.delete(session) as DeleteSummariesResult.Failure).error)
        val changed = DeleteScanSummaries(SessionApiClient { _, _ -> ApiResult.Success(200, success) }, repository) { _, _ -> false }
        assertEquals(DeleteSummariesResult.SessionChanged, changed.delete(session))
        assertNull(obsolete)
    }

    @Test fun `respuesta perdida no provoca reintento ni cambia generacion`() = runTest {
        var calls = 0
        var updates = 0
        val action = DeleteScanSummaries(SessionApiClient { _, _ -> calls++; ApiResult.TimedOut }, repository) { _, _ -> updates++; true }
        assertEquals(ApiResult.TimedOut, (action.delete(session) as DeleteSummariesResult.Failure).error)
        assertEquals(1, calls)
        assertEquals(0, updates)
        assertNull(obsolete)
    }
}
