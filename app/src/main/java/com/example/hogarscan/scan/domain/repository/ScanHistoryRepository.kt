package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.ScanRecord

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
}