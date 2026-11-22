package com.example.hogarscan.scan.domain.usecase

fun interface DeleteLocalScan {
    suspend fun delete(scanId: String, expected: HistoryOwnerContext): LocalScanDeletion
}
enum class LocalScanDeletion { DELETED, NOT_FOUND, IN_PROGRESS, SESSION_CHANGED }
