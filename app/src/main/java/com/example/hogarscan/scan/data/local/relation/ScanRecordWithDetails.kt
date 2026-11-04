package com.example.hogarscan.scan.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity

data class ScanRecordWithDetails(
    @Embedded
    val record: ScanRecordEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "scan_id"
    )
    val ports: List<ScanPortEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "scan_id"
    )
    val results: List<ProbeResultEntity>
)