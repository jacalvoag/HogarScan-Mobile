package com.example.hogarscan.evaluations.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity

@Entity(
    tableName = "scan_evaluations",
    primaryKeys = ["scan_id", "address"],
    foreignKeys = [ForeignKey(entity = ScanRecordEntity::class, parentColumns = ["id"],
        childColumns = ["scan_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["scan_id"])]
)
data class ScanEvaluationEntity(
    @ColumnInfo(name = "scan_id") val scanId: String,
    val address: Long,
    @ColumnInfo(name = "response_json") val responseJson: String,
    val vendor: String?,
    val product: String?,
    val version: String?,
    @ColumnInfo(name = "identification_source") val identificationSource: String?,
    @ColumnInfo(name = "service_port") val servicePort: Int? = null
)
