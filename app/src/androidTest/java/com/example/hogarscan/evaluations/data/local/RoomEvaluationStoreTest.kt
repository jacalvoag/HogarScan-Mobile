package com.example.hogarscan.evaluations.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hogarscan.auth.data.session.*
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEvaluationStoreTest {
    private lateinit var db: HogarScanDatabase
    private val sessions = PersistentSessionStore(object : SessionStorage {
        override suspend fun read(): SessionCredentials? = null
        override suspend fun write(credentials: SessionCredentials) = Unit
        override suspend fun clear() = Unit
    }, { 1 })
    private val user = "2c48661b-b872-4ea4-ad9e-3df603a5c0f9"
    private val scanId = "6ce5e6b1-9509-48a7-bc9d-f0f872364726"
    private val address = Ipv4Address.parse("192.168.1.2")
    private val probe = ProbeResult(address, 80, ProbeOutcome.ACCEPTED, 5)
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"), 24), address, address, setOf(80))
    private val scan = ScanRecord(scanId, "user:$user", config, 10, ScanRecordStatus.COMPLETED, listOf(probe), 20, 1)
    private val response = EvaluationResult("1", "2026-11-11T00:00:00Z",
        EvaluationSources(SourceState.NOT_QUERIED, SourceState.NOT_QUERIED, null), emptyList(), emptyList())
    private val evaluation = TargetEvaluation(EvaluationInput.fromResults(scanId, "user:$user", address, listOf(probe)), response)
    private fun credentials(id: String) = SessionCredentials(id, "a@example.com", 1000, 1, "a".repeat(43))

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HogarScanDatabase::class.java).build() }
    @After fun cleanup() { db.close() }

    @Test fun guardaEvaluacionYResumenComoUnaUnidadYNoReemplaza() = runBlocking {
        val session = requireNotNull(sessions.activate(credentials(user)))
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val store = RoomEvaluationStore(db, sessions)
        assertEquals(EvaluationSaveResult.SAVED, store.saveCompleted(scanId, listOf(evaluation), session))
        assertEquals(response, store.load(scanId, session)?.single()?.result)
        assertEquals(1, db.scanSummaryDao().findPending(user, 1).size)
        assertEquals(EvaluationSaveResult.ALREADY_QUEUED, store.saveCompleted(scanId, listOf(evaluation), session))
        assertEquals(1, store.load(scanId, session)?.size)
    }

    @Test fun cuentaDiferenteNoPuedeLeerNiGuardar() = runBlocking {
        val first = requireNotNull(sessions.activate(credentials(user)))
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val store = RoomEvaluationStore(db, sessions)
        sessions.activate(credentials("a32807a8-c54a-47a5-8a4e-63093fcb0bda"))
        assertEquals(EvaluationSaveResult.SESSION_CHANGED, store.saveCompleted(scanId, listOf(evaluation), first))
        assertNull(store.load(scanId, first))
        assertTrue(db.scanSummaryDao().findPending(user, 1).isEmpty())
    }

    @Test fun cambiarCuentaDurantePreparacionNoGuardaElLote() = runBlocking {
        val first = requireNotNull(sessions.activate(credentials(user)))
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val queued = ArrayDeque<Runnable>()
        val preparation = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { queued.addLast(block) }
        }
        val store = RoomEvaluationStore(db, sessions, preparation)
        val save = async(start = CoroutineStart.UNDISPATCHED) {
            store.saveCompleted(scanId, listOf(evaluation), first)
        }
        assertFalse(save.isCompleted)
        assertEquals(1, queued.size)
        sessions.activate(credentials("a32807a8-c54a-47a5-8a4e-63093fcb0bda"))
        queued.removeFirst().run()
        assertEquals(EvaluationSaveResult.SESSION_CHANGED, save.await())
        assertTrue(db.scanSummaryDao().findPending(user, 1).isEmpty())
        assertTrue(db.scanEvaluationDao().findByScan(scanId, scan.ownerId).isEmpty())
    }
    @Test fun borrarRevisionEliminaDetalleEvaluacionesYPendienteSoloDelPropietario() = runBlocking {
        val session = requireNotNull(sessions.activate(credentials(user)))
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val store = RoomEvaluationStore(db, sessions)
        store.saveCompleted(scanId, listOf(evaluation), session)
        val other = scan.toLocal().let { it.copy(record = it.record.copy(id = "other", ownerId = "user:other"),
            ports = it.ports.map { port -> port.copy(scanId = "other") },
            results = it.results.map { probe -> probe.copy(scanId = "other") }) }
        db.scanRecordDao().insertWithDetails(other)
        val deletion = com.example.hogarscan.scan.data.local.repository.RoomDeleteLocalScan(db, sessions) { "local:guest" }
        val context = com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext(scan.ownerId, session)
        assertEquals(com.example.hogarscan.scan.domain.usecase.LocalScanDeletion.DELETED, deletion.delete(scanId, context))
        assertNull(db.scanRecordDao().findById(scanId, scan.ownerId))
        assertTrue(db.scanEvaluationDao().findByScan(scanId, scan.ownerId).isEmpty())
        assertTrue(db.scanSummaryDao().findPending(user, 1).isEmpty())
        assertNotNull(db.scanRecordDao().findById("other", "user:other"))
    }

    @Test fun borradoLocalRechazaContextoAnteriorYRevisionesEnCurso() = runBlocking {
        val first = requireNotNull(sessions.activate(credentials(user)))
        db.scanRecordDao().insertWithDetails(scan.toLocal())
        val deletion = com.example.hogarscan.scan.data.local.repository.RoomDeleteLocalScan(db, sessions) { "local:guest" }
        sessions.activate(credentials("a32807a8-c54a-47a5-8a4e-63093fcb0bda"))
        assertEquals(com.example.hogarscan.scan.domain.usecase.LocalScanDeletion.SESSION_CHANGED,
            deletion.delete(scanId, com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext(scan.ownerId, first)))
        assertNotNull(db.scanRecordDao().findById(scanId, scan.ownerId))
        val current = requireNotNull(sessions.activate(credentials(user)))
        val active = ScanRecord("running", scan.ownerId, config, 10, ScanRecordStatus.RUNNING, summaryGeneration = 1)
        db.scanRecordDao().insertWithDetails(active.toLocal())
        assertEquals(com.example.hogarscan.scan.domain.usecase.LocalScanDeletion.IN_PROGRESS,
            deletion.delete(active.id, com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext(active.ownerId, current)))
        assertNotNull(db.scanRecordDao().findById(active.id, active.ownerId))
    }

    @Test fun protocoloEIdentidadAnunciadaSeConservanTrasGuardarYCargar() = runBlocking {
        val session = requireNotNull(sessions.activate(credentials(user)))
        val observed = probe.copy(service = ServiceEvidence(ServiceProtocol.HTTP, "apache", "http_server", "2.4.49"))
        val record = ScanRecord(scanId, scan.ownerId, config, 10, ScanRecordStatus.COMPLETED, listOf(observed), 20, 1)
        db.scanRecordDao().insertWithDetails(record.toLocal())
        val input = EvaluationInput.fromResults(scanId, scan.ownerId, address, listOf(observed))
        val store = RoomEvaluationStore(db, sessions)
        store.saveCompleted(scanId, listOf(TargetEvaluation(input, response)), session)
        val loaded = store.load(scanId, session)!!.single()
        assertEquals(observed.service, loaded.input.probes.single().service)
        assertEquals(IdentificationSource.SERVICE_RESPONSE, loaded.input.identification!!.source)
        assertEquals(80, loaded.input.identification!!.servicePort)
    }

    @Test fun invitadoSoloPuedeBorrarSuPerfilLocal() = runBlocking {
        sessions.restore()
        val guest = ScanRecord(scanId, "local:guest", config, 10, ScanRecordStatus.COMPLETED, listOf(probe), 20)
        db.scanRecordDao().insertWithDetails(guest.toLocal())
        val deletion = com.example.hogarscan.scan.data.local.repository.RoomDeleteLocalScan(db, sessions) { "local:guest" }
        assertEquals(com.example.hogarscan.scan.domain.usecase.LocalScanDeletion.SESSION_CHANGED,
            deletion.delete(scanId, com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext("local:other", null)))
        assertNotNull(db.scanRecordDao().findById(scanId, guest.ownerId))
        assertEquals(com.example.hogarscan.scan.domain.usecase.LocalScanDeletion.DELETED,
            deletion.delete(scanId, com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext(guest.ownerId, null)))
        assertNull(db.scanRecordDao().findById(scanId, guest.ownerId))
    }

}
