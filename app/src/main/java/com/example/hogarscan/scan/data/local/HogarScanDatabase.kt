package com.example.hogarscan.scan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.dao.ScanSummaryDao
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.entity.ScanSummaryEntity
import com.example.hogarscan.evaluations.data.local.ScanEvaluationEntity
import com.example.hogarscan.evaluations.data.local.ScanEvaluationDao

@Database(
    entities = [
        ScanRecordEntity::class,
        ScanPortEntity::class,
        ProbeResultEntity::class,
        ScanSummaryEntity::class,
        ScanEvaluationEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class HogarScanDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun scanSummaryDao(): ScanSummaryDao
    abstract fun scanEvaluationDao(): ScanEvaluationDao
}
