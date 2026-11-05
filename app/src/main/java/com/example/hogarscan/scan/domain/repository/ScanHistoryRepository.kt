package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordSummary
import kotlinx.coroutines.flow.Flow
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanRecordStatus

interface ScanHistoryRepository {

    /**
     * Inserta una revisión nueva.
     * Un identificador duplicado provoca un error.
     */
    suspend fun insert(record: ScanRecord)

    /**
     * Devuelve la revisión si pertenece al propietario indicado.
     */
    suspend fun findById(
        scanId: String,
        ownerId: String
    ): ScanRecord?

    /**
     * Observa el historial del propietario, de más reciente a más antiguo.
     */
    fun observeByOwner(ownerId: String): Flow<List<ScanRecordSummary>>

    suspend fun appendResult(
        scanId: String,
        ownerId: String,
        result: ProbeResult
    )

    suspend fun finish(
        scanId: String,
        ownerId: String,
        status: ScanRecordStatus,
        finishedAtMillis: Long
    )
}