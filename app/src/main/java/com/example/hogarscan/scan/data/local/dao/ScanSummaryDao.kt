package com.example.hogarscan.scan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.entity.ScanSummaryEntity
import com.example.hogarscan.scan.data.local.mapper.toLocal
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.ScanRecordStatus

@Dao
abstract class ScanSummaryDao {

    /**
     * Devuelve true si se insertó.
     * Si ya existe, conserva íntegramente el resumen anterior.
     *
     * El llamador debe proporcionar los conteos de una evaluación completa.
     */
    @Transaction
    open suspend fun enqueue(summary: FrozenScanSummary): Boolean {
        val source = requireNotNull(
            findSource(summary.scanId, "user:${summary.userId}")
        ) {
            "La revisión no existe para esta cuenta."
        }

        require(source.status == ScanRecordStatus.COMPLETED) {
            "Solo se pueden sincronizar revisiones completadas."
        }
        require(source.summaryGeneration == summary.summaryGeneration) {
            "La generación debe coincidir con la capturada al iniciar."
        }
        require(
            source.startedAtMillis == summary.startedAtMillis &&
                    source.finishedAtMillis == summary.finishedAtMillis
        ) {
            "Las fechas deben coincidir con la revisión guardada."
        }

        return insertIfAbsent(summary.toLocal()) != -1L
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(
        summary: ScanSummaryEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM scan_records
        WHERE id = :scanId AND owner_id = :ownerId
        """
    )
    protected abstract suspend fun findSource(
        scanId: String,
        ownerId: String
    ): ScanRecordEntity?

    @Query(
        """
        SELECT *
        FROM scan_summaries
        WHERE scan_id = :scanId AND user_id = :userId
        """
    )
    abstract suspend fun findById(
        scanId: String,
        userId: String
    ): ScanSummaryEntity?

    @Query(
        """
        SELECT *
        FROM scan_summaries
        WHERE user_id = :userId
          AND summary_generation = :generation
          AND sync_status = 'PENDING'
        ORDER BY started_at_millis ASC, scan_id ASC
        """
    )
    abstract suspend fun findPending(
        userId: String,
        generation: Int
    ): List<ScanSummaryEntity>

    @Transaction
    open suspend fun markSynced(
        scanId: String,
        userId: String,
        generation: Int,
        receivedAtMillis: Long
    ): Int {
        require(receivedAtMillis in 0L..253_402_300_799_999L) {
            "La fecha de recepción no es válida."
        }

        return confirmReceipt(
            scanId,
            userId,
            generation,
            receivedAtMillis
        )
    }

    @Query(
        """
        UPDATE scan_summaries
        SET sync_status = 'SYNCED',
            received_at_millis = :receivedAtMillis
        WHERE scan_id = :scanId
          AND user_id = :userId
          AND summary_generation = :generation
          AND sync_status = 'PENDING'
        """
    )
    protected abstract suspend fun confirmReceipt(
        scanId: String,
        userId: String,
        generation: Int,
        receivedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE scan_summaries
        SET sync_status = 'CONFLICT'
        WHERE scan_id = :scanId
          AND user_id = :userId
          AND summary_generation = :generation
          AND sync_status = 'PENDING'
        """
    )
    abstract suspend fun markConflict(
        scanId: String,
        userId: String,
        generation: Int
    ): Int

    @Query(
        """
        UPDATE scan_summaries
        SET sync_status = 'OBSOLETE'
        WHERE user_id = :userId
          AND summary_generation < :currentGeneration
          AND sync_status = 'PENDING'
        """
    )
    abstract suspend fun markOlderPendingObsolete(
        userId: String,
        currentGeneration: Int
    ): Int
}