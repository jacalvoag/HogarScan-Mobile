package com.example.hogarscan.scan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hogarscan.scan.domain.model.SummarySyncStatus

@Entity(
    tableName = "scan_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["scan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "sync_status"])
    ]
)
data class ScanSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "summary_generation")
    val summaryGeneration: Int,

    @ColumnInfo(name = "started_at_millis")
    val startedAtMillis: Long,

    @ColumnInfo(name = "finished_at_millis")
    val finishedAtMillis: Long,

    @ColumnInfo(name = "addresses_scanned")
    val addressesScanned: Int,

    @ColumnInfo(name = "addresses_responded")
    val addressesResponded: Int,

    @ColumnInfo(name = "accepted_services")
    val acceptedServices: Int,

    @ColumnInfo(name = "tcp_service_accessible")
    val tcpServiceAccessible: Int,

    @ColumnInfo(name = "protocol_identified")
    val protocolIdentified: Int,

    @ColumnInfo(name = "preventive_review")
    val preventiveReview: Int,

    @ColumnInfo(name = "possible_cve_match")
    val possibleCveMatch: Int,

    @ColumnInfo(name = "insufficient_evidence")
    val insufficientEvidence: Int,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SummarySyncStatus = SummarySyncStatus.PENDING,

    @ColumnInfo(name = "received_at_millis")
    val receivedAtMillis: Long? = null
)