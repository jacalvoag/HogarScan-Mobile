package com.example.hogarscan.evaluations.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanEvaluationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(evaluations: List<ScanEvaluationEntity>)

    @Query("""SELECT e.* FROM scan_evaluations e
        INNER JOIN scan_records s ON s.id = e.scan_id
        WHERE e.scan_id = :scanId AND s.owner_id = :ownerId ORDER BY e.address""")
    suspend fun findByScan(scanId: String, ownerId: String): List<ScanEvaluationEntity>
}
