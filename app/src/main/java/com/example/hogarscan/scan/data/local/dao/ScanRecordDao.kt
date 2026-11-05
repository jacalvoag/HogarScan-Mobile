package com.example.hogarscan.scan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.relation.ScanRecordWithDetails
import kotlinx.coroutines.flow.Flow
import androidx.room.Update
import com.example.hogarscan.scan.data.local.mapper.toDomain
import com.example.hogarscan.scan.domain.model.ScanRecordStatus

@Dao
abstract class ScanRecordDao {

    @Transaction
    open suspend fun insertWithDetails(details: ScanRecordWithDetails) {
        val scanId = details.record.id

        require(details.ports.all { it.scanId == scanId }) {
            "Los puertos deben pertenecer a la revisión."
        }
        require(details.results.all { it.scanId == scanId }) {
            "Los resultados deben pertenecer a la revisión."
        }

        insertRecord(details.record)
        insertPorts(details.ports)
        insertResults(details.results)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecord(record: ScanRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPorts(ports: List<ScanPortEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertResults(results: List<ProbeResultEntity>)

    @Transaction
    @Query(
        """
        SELECT *
        FROM scan_records
        WHERE id = :scanId AND owner_id = :ownerId
        """
    )
    abstract suspend fun findById(
        scanId: String,
        ownerId: String
    ): ScanRecordWithDetails?

    @Query(
        """
        SELECT *
        FROM scan_records
        WHERE owner_id = :ownerId
        ORDER BY started_at_millis DESC, id DESC
        """
    )
    abstract fun observeByOwner(
        ownerId: String
    ): Flow<List<ScanRecordEntity>>

    @Transaction
    open suspend fun appendResult(
        ownerId: String,
        result: ProbeResultEntity
    ) {
        val record = requireNotNull(findRecord(result.scanId, ownerId)) {
            "La revisión no existe para este propietario."
        }

        require(record.status == ScanRecordStatus.RUNNING) {
            "Solo se pueden agregar resultados a una revisión en curso."
        }
        require(
            result.address in record.startAddress..record.endAddress &&
                    result.address != record.localAddress
        ) {
            "La dirección debe pertenecer al rango y excluir al teléfono."
        }

        insertResults(listOf(result))
    }

    @Transaction
    open suspend fun finish(
        scanId: String,
        ownerId: String,
        status: ScanRecordStatus,
        finishedAtMillis: Long
    ) {
        require(status != ScanRecordStatus.RUNNING) {
            "El cierre requiere un estado final."
        }

        val details = requireNotNull(findById(scanId, ownerId)) {
            "La revisión no existe para este propietario."
        }
        require(details.record.status == ScanRecordStatus.RUNNING) {
            "La revisión ya está cerrada."
        }

        val updated = details.record.copy(
            status = status,
            finishedAtMillis = finishedAtMillis
        )

        // Reutiliza las validaciones del dominio antes de escribir.
        details.copy(record = updated).toDomain()

        check(updateRecord(updated) == 1) {
            "No se pudo cerrar la revisión."
        }
    }

    @Query(
        """
        SELECT *
        FROM scan_records
        WHERE id = :scanId AND owner_id = :ownerId
        """
    )
    protected abstract suspend fun findRecord(
        scanId: String,
        ownerId: String
    ): ScanRecordEntity?

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateRecord(
        record: ScanRecordEntity
    ): Int

    @Query(
        """
        UPDATE scan_records
        SET execution_status = :interruptedStatus,
            finished_at_millis = :finishedAtMillis
        WHERE execution_status = :runningStatus
        """
    )
    abstract suspend fun recoverRunningRecords(
        finishedAtMillis: Long,
        runningStatus: ScanRecordStatus,
        interruptedStatus: ScanRecordStatus
    ): Int
}