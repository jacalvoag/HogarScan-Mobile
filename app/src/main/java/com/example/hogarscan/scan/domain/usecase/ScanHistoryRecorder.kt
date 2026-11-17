package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import java.util.UUID

class ScanHistoryRecorder(
    private val repository: ScanHistoryRepository,
    private val ownerContext: suspend () -> HistoryOwnerContext,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun start(config: ScanConfig): ScanRecord {
        val context = ownerContext()

        val record = ScanRecord(
            id = newId(),
            ownerId = context.ownerId,
            config = config,
            startedAtMillis = nowMillis(),
            status = ScanRecordStatus.RUNNING,
            summaryGeneration = context.session?.summaryGeneration
        )

        repository.insert(record)
        return record
    }

    suspend fun append(record: ScanRecord, result: ProbeResult) {
        repository.appendResult(record.id, record.ownerId, result)
    }

    suspend fun finish(record: ScanRecord, status: ScanRecordStatus) {
        repository.finish(record.id, record.ownerId, status, nowMillis())
    }
}