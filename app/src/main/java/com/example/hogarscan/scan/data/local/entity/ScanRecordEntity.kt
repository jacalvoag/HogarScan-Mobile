package com.example.hogarscan.scan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hogarscan.scan.domain.model.ScanRecordStatus

@Entity(
    tableName = "scan_records",
    indices = [
        Index(value = ["owner_id", "started_at_millis"])
    ]
)
data class ScanRecordEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "owner_id")
    val ownerId: String,

    @ColumnInfo(name = "local_address")
    val localAddress: Long,

    @ColumnInfo(name = "prefix_length")
    val prefixLength: Int,

    @ColumnInfo(name = "start_address")
    val startAddress: Long,

    @ColumnInfo(name = "end_address")
    val endAddress: Long,

    @ColumnInfo(name = "execution_status")
    val status: ScanRecordStatus,

    @ColumnInfo(name = "started_at_millis")
    val startedAtMillis: Long,

    @ColumnInfo(name = "finished_at_millis")
    val finishedAtMillis: Long? = null
)