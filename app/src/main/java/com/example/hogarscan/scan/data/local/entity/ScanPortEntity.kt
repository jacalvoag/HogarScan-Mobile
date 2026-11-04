package com.example.hogarscan.scan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "scan_ports",
    primaryKeys = ["scan_id", "port"],
    foreignKeys = [
        ForeignKey(
            entity = ScanRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["scan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScanPortEntity(
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    val port: Int
)