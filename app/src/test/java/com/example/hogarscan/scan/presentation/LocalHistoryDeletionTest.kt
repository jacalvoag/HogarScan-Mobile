package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.domain.model.*
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import com.example.hogarscan.scan.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalHistoryDeletionTest {
    private val lifecycle = ViewModelStore()
    private val sessions = object : SessionReader { override val state = MutableStateFlow<SessionState>(SessionState.Guest) }
    private val ip = Ipv4Address.parse("192.168.1.2")
    private val config = ScanConfig(NetworkScope(Ipv4Address.parse("192.168.1.1"),24),ip,ip,setOf(80))
    private val record = ScanRecord("scan", "local:guest", config, 1, ScanRecordStatus.COMPLETED,
        listOf(ProbeResult(ip,80,ProbeOutcome.ACCEPTED,1)),2)
    private val history = object : ScanHistoryRepository {
        override suspend fun insert(record: ScanRecord) = Unit
        override suspend fun findById(scanId: String, ownerId: String) = record.takeIf { it.id == scanId && it.ownerId == ownerId }
        override fun observeByOwner(ownerId: String) = MutableStateFlow(emptyList<ScanRecordSummary>())
        override suspend fun appendResult(scanId: String, ownerId: String, result: ProbeResult) = Unit
        override suspend fun finish(scanId: String, ownerId: String, status: ScanRecordStatus, finishedAtMillis: Long) = Unit
    }
    private var calls = 0
    private fun vm() = ViewModelProvider(lifecycle, viewModelFactory { initializer {
        ScanHistoryViewModel(history, HistoryOwnerResolver(sessions, { "local:guest" }), sessions,
            deleteLocal = DeleteLocalScan { scanId, expected ->
                assertEquals(record.id, scanId)
                assertEquals(record.ownerId, expected.ownerId)
                calls++
                LocalScanDeletion.DELETED
            })
    } })[ScanHistoryViewModel::class.java]
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { lifecycle.clear(); Dispatchers.resetMain() }

    @Test fun `borrar requiere confirmacion y cancelar no elimina`() = runTest {
        val viewModel = vm(); runCurrent()
        viewModel.openDetail(record.id); runCurrent()
        viewModel.requestDeletion(); runCurrent()
        assertEquals(record, viewModel.deletion.value.confirmation)
        viewModel.cancelDeletion(); viewModel.confirmDeletion(); runCurrent()
        assertEquals(0,calls)
        viewModel.requestDeletion(); runCurrent(); viewModel.confirmDeletion(); runCurrent()
        assertEquals(1,calls)
        assertEquals(ScanDetailUiState.Closed,viewModel.detailState.value)
    }

    @Test fun `un cambio de sesion cancela la confirmacion anterior`() = runTest {
        val viewModel = vm(); runCurrent()
        viewModel.openDetail(record.id); runCurrent(); viewModel.requestDeletion(); runCurrent()
        sessions.state.value = SessionState.Restoring
        runCurrent(); viewModel.confirmDeletion(); runCurrent()
        assertEquals(0,calls)
        assertNull(viewModel.deletion.value.confirmation)
    }
}
