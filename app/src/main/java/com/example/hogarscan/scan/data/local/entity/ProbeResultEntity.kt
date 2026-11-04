package com.example.hogarscan.scan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.example.hogarscan.scan.domain.model.ProbeOutcome

@Entity(
    tableName = "probe_results",
    primaryKeys = ["scan_id", "port", "address"],
    foreignKeys = [
        ForeignKey(
            entity = ScanPortEntity::class,
            parentColumns = ["scan_id", "port"],
            childColumns = ["scan_id", "port"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProbeResultEntity(
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    val address: Long,

    val port: Int,

    val outcome: ProbeOutcome,

    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long
)