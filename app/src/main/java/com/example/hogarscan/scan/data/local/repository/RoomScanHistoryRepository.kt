package com.example.hogarscan.scan.data.local.repository

import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.mapper.toDomain
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.data.local.mapper.toSummary
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScanHistoryRepository(
    private val dao: ScanRecordDao
) : ScanHistoryRepository {

    override suspend fun insert(record: ScanRecord) {
        dao.insertWithDetails(record.toLocal())
    }

    override suspend fun findById(
        scanId: String,
        ownerId: String
    ): ScanRecord? {
        return dao.findById(
            scanId = scanId,
            ownerId = ownerId
        )?.toDomain()
    }

    override fun observeByOwner(
        ownerId: String
    ): Flow<List<ScanRecordSummary>> {
        return dao.observeByOwner(ownerId).map { records ->
            records.map { it.toSummary() }
        }
    }
}