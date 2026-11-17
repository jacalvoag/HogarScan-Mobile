package com.example.hogarscan.evaluations

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.auth.data.http.SessionApiClient
import com.example.hogarscan.auth.data.session.PersistentSessionStore
import com.example.hogarscan.auth.data.session.SessionCredentials
import com.example.hogarscan.auth.data.session.SessionStorage
import com.example.hogarscan.evaluations.data.DefaultEvaluationGateway
import com.example.hogarscan.evaluations.data.EvaluationWorkflow
import com.example.hogarscan.evaluations.data.EvaluationWorkflowResult
import com.example.hogarscan.evaluations.data.HttpEvaluationDataSource
import com.example.hogarscan.evaluations.data.local.RoomEvaluationStore
import com.example.hogarscan.evaluations.domain.EvaluateCompletedScan
import com.example.hogarscan.scan.data.http.HttpScanSummaryDataSource
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.data.local.repository.RoomScanHistoryRepository
import com.example.hogarscan.scan.data.local.repository.RoomScanSummaryRepository
import com.example.hogarscan.scan.data.sync.ScanSummarySyncCoordinator
import com.example.hogarscan.scan.data.sync.SummarySyncRunResult
import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.shared.data.http.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanEvaluationEndToEndTest {
    private lateinit var db: HogarScanDatabase
    private val user = "2c48661b-b872-4ea4-ad9e-3df603a5c0f9"
    private val scanId = "6ce5e6b1-9509-48a7-bc9d-f0f872364726"
    private val address = Ipv4Address.parse("192.168.1.2")
    private val sessions = PersistentSessionStore(object : SessionStorage {
        override suspend fun read(): SessionCredentials? = null
        override suspend fun write(credentials: SessionCredentials) = Unit
        override suspend fun clear() = Unit
    }, { 1 })

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HogarScanDatabase::class.java).build() }
    @After fun cleanup() { db.close() }

    @Test fun conservaEvaluacionYReintentaMismoResumenSinIP() = runBlocking {
        val session = requireNotNull(sessions.activate(SessionCredentials(user, "a@example.com", 1000, 1, "a".repeat(43))))
        val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80))
        val scan = ScanRecord(scanId, "user:$user", config, 10, ScanRecordStatus.COMPLETED,
            listOf(ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 5)), 20, 1)
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val bodies = mutableListOf<String>()
        val client = SessionApiClient { request, expected ->
            assertEquals(session, expected)
            when (request.path) {
                "/evaluations" -> {
                    assertFalse(request.bodyJson!!.contains(address.toString()))
                    ApiResult.Success(200, """{"ruleset_version":"1","evaluated_at":"2026-11-11T00:00:00Z","sources":{"nvd":"NOT_QUERIED","kev":"NOT_QUERIED","checked_at":null},"findings":[{"category":"TCP_SERVICE_ACCESSIBLE","title":"Conexión aceptada","priority":"LOW","confidence":"OBSERVED","evidence":"Puerto 80 aceptado","explanation":"Conexión","action":"Revisar","limitation":"No confirma vulnerabilidad","rule_id":"TCP_ACCEPTED","cve":null}],"possible_cve_matches":[]} """)
                }
                "/me/scans" -> {
                    bodies.add(requireNotNull(request.bodyJson))
                    assertFalse(bodies.last().contains(address.toString()))
                    if (bodies.size == 1) ApiResult.NetworkFailure else ApiResult.Success(201,
                        """{"scan_id":"$scanId","summary_generation":1,"received_at":"2026-11-11T00:00:00Z","created":true}""")
                }
                else -> error("Ruta inesperada")
            }
        }
        val store = RoomEvaluationStore(db, sessions)
        val queue = RoomScanSummaryRepository(db.scanSummaryDao())
        val remote = HttpScanSummaryDataSource(client)
        val sync = ScanSummarySyncCoordinator(queue, sessions, remote::send, sessions::updateSummaryGeneration, { 1 })
        val evaluator = EvaluateCompletedScan(sessions, DefaultEvaluationGateway(HttpEvaluationDataSource(client)), { 1 })
        val workflow = EvaluationWorkflow(sessions, RoomScanHistoryRepository(db.scanRecordDao(), { 1 }), evaluator,
            store::saveCompleted, { sync.syncPending(it) }, { 1 })

        val saved = workflow.run(scanId, session) as EvaluationWorkflowResult.Saved
        assertTrue(saved.sync is SummarySyncRunResult.Failure)
        assertEquals("Puerto 80 aceptado", store.load(scanId, session)?.single()?.result?.findings?.single()?.evidence)
        assertEquals(1, queue.findPending(user, 1).size)
        assertEquals(SummarySyncRunResult.Completed, sync.syncPending(session))
        assertTrue(queue.findPending(user, 1).isEmpty())
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertEquals(SummarySyncStatus.SYNCED, db.scanSummaryDao().findById(scanId, user)?.syncStatus)
    }
}
