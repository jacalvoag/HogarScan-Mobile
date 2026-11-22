package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.FrozenScanSummary

interface ScanSummaryRepository {

    /**
     * Guarda un resumen construido desde una evaluación completa.
     * Devuelve false si ya existe y conserva el original.
     */
    suspend fun enqueue(summary: FrozenScanSummary): Boolean

    suspend fun findPending(
        userId: String,
        generation: Int
    ): List<FrozenScanSummary>

    suspend fun markSynced(
        summary: FrozenScanSummary,
        receivedAtMillis: Long
    ): Boolean

    suspend fun markConflict(summary: FrozenScanSummary): Boolean

    suspend fun markOlderPendingObsolete(
        userId: String,
        currentGeneration: Int
    ): Int
}