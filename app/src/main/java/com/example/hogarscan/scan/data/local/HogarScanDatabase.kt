package com.example.hogarscan.scan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.hogarscan.scan.data.local.dao.ScanRecordDao
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity

@Database(
    entities = [
        ScanRecordEntity::class,
        ScanPortEntity::class,
        ProbeResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class HogarScanDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao
}