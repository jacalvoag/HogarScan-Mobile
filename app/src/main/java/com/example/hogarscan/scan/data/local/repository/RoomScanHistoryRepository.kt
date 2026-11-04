package com.example.hogarscan.scan.data.local.repository

import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.mapper.toDomain
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.repository.ScanHistoryRepository

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
}