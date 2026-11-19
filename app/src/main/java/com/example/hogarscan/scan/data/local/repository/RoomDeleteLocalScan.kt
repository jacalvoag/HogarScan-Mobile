package com.example.hogarscan.scan.data.local.repository

import androidx.room.withTransaction
import com.example.hogarscan.auth.data.session.PersistentSessionStore
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.usecase.DeleteLocalScan
import com.example.hogarscan.scan.domain.usecase.HistoryOwnerContext
import com.example.hogarscan.scan.domain.usecase.LocalScanDeletion

class RoomDeleteLocalScan(
    private val database: HogarScanDatabase,
    private val sessions: PersistentSessionStore,
    private val guestOwnerId: suspend () -> String
) : DeleteLocalScan {
    override suspend fun delete(scanId: String, expected: HistoryOwnerContext): LocalScanDeletion {
        val session = expected.session
        return if (session == null) {
            sessions.withGuestSession {
                if (expected.ownerId != guestOwnerId()) LocalScanDeletion.SESSION_CHANGED
                else deleteOwned(scanId, expected.ownerId)
            }
        } else {
            if (expected.ownerId != "user:${session.userId}") return LocalScanDeletion.SESSION_CHANGED
            sessions.withCurrentSession(session) { deleteOwned(scanId, expected.ownerId) }
        } ?: LocalScanDeletion.SESSION_CHANGED
    }

    private suspend fun deleteOwned(scanId: String, ownerId: String): LocalScanDeletion = database.withTransaction {
        val source = database.scanRecordDao().findById(scanId, ownerId) ?: return@withTransaction LocalScanDeletion.NOT_FOUND
        if (source.record.status == ScanRecordStatus.RUNNING) return@withTransaction LocalScanDeletion.IN_PROGRESS
        if (database.scanRecordDao().deleteClosedRecord(scanId, ownerId) == 1) LocalScanDeletion.DELETED
        else LocalScanDeletion.NOT_FOUND
    }
}
