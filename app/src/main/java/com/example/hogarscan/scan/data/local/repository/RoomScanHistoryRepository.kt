package com.example.hogarscan.scan.data.local.repository

import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.mapper.toDomain
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.data.local.mapper.toSummary
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AppContainer comparte esta instancia durante la vida del proceso.
 * La recuperación debe terminar antes de aceptar operaciones nuevas.
 */
class RoomScanHistoryRepository(
    private val dao: ScanRecordDao,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ScanHistoryRepository {

    private val initializationMutex = Mutex()
    private var initialized = false

    private suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (!initialized) {
                val timestamp = nowMillis()
                require(timestamp >= 0L) {
                    "La fecha de recuperación no puede ser negativa."
                }
                dao.recoverRunningRecords(
                    finishedAtMillis = timestamp,
                    runningStatus = ScanRecordStatus.RUNNING,
                    interruptedStatus = ScanRecordStatus.INTERRUPTED
                )
                initialized = true
            }
        }
    }

    override suspend fun insert(record: ScanRecord) {
        ensureInitialized()
        dao.insertWithDetails(record.toLocal())
    }

    override suspend fun findById(
        scanId: String,
        ownerId: String
    ): ScanRecord? {
        ensureInitialized()
        return dao.findById(
            scanId = scanId,
            ownerId = ownerId
        )?.toDomain()
    }

    override fun observeByOwner(
        ownerId: String
    ): Flow<List<ScanRecordSummary>> = flow {
        ensureInitialized()
        emitAll(
            dao.observeByOwner(ownerId).map { records ->
                records.map { it.toSummary() }
            }
        )
    }

    override suspend fun appendResult(
        scanId: String,
        ownerId: String,
        result: ProbeResult
    ) {
        ensureInitialized()
        dao.appendResult(
            ownerId = ownerId,
            result = ProbeResultEntity(
                scanId = scanId,
                address = result.address.value,
                port = result.port,
                outcome = result.outcome,
                durationMillis = result.durationMillis
            )
        )
    }

    override suspend fun finish(
        scanId: String,
        ownerId: String,
        status: ScanRecordStatus,
        finishedAtMillis: Long
    ) {
        ensureInitialized()
        dao.finish(
            scanId = scanId,
            ownerId = ownerId,
            status = status,
            finishedAtMillis = finishedAtMillis
        )
    }
}