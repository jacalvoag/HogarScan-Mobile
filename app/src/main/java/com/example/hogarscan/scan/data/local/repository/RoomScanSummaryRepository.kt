package com.example.hogarscan.scan.data.local.repository

import com.example.hogarscan.scan.data.local.dao.ScanSummaryDao
import com.example.hogarscan.scan.data.local.mapper.toFrozenSummary
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.repository.ScanSummaryRepository

class RoomScanSummaryRepository(
    private val dao: ScanSummaryDao
) : ScanSummaryRepository {

    override suspend fun enqueue(
        summary: FrozenScanSummary
    ): Boolean = dao.enqueue(summary)

    override suspend fun findPending(
        userId: String,
        generation: Int
    ): List<FrozenScanSummary> {
        require(generation >= 0) {
            "La generación no puede ser negativa."
        }

        return dao.findPending(
            userId = userId.lowercase(),
            generation = generation
        ).map { it.toFrozenSummary() }
    }

    override suspend fun markSynced(
        summary: FrozenScanSummary,
        receivedAtMillis: Long
    ): Boolean =
        dao.markSynced(
            scanId = summary.scanId,
            userId = summary.userId,
            generation = summary.summaryGeneration,
            receivedAtMillis = receivedAtMillis
        ) == 1

    override suspend fun markConflict(
        summary: FrozenScanSummary
    ): Boolean =
        dao.markConflict(
            scanId = summary.scanId,
            userId = summary.userId,
            generation = summary.summaryGeneration
        ) == 1

    override suspend fun markOlderPendingObsolete(
        userId: String,
        currentGeneration: Int
    ): Int {
        require(currentGeneration >= 0) {
            "La generación no puede ser negativa."
        }

        return dao.markOlderPendingObsolete(
            userId = userId.lowercase(),
            currentGeneration = currentGeneration
        )
    }
}