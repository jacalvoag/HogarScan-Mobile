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
}