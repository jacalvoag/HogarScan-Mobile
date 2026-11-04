package com.example.hogarscan.scan.domain.model

data class ScanRecordSummary(
    val id: String,
    val ownerId: String,
    val status: ScanRecordStatus,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?
)