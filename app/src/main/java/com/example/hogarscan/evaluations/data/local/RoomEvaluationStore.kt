package com.example.hogarscan.evaluations.data.local

import androidx.room.withTransaction
import com.example.hogarscan.auth.data.session.PersistentSessionStore
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.evaluations.data.EvaluationResultJson
import com.example.hogarscan.evaluations.domain.TargetEvaluation
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import com.example.hogarscan.evaluations.domain.prepareEvaluatedScanSummary
import com.example.hogarscan.scan.data.local.HogarScanDatabase
import com.example.hogarscan.scan.data.local.mapper.toDomain
import com.example.hogarscan.scan.domain.model.Ipv4Address

enum class EvaluationSaveResult { SAVED, ALREADY_QUEUED, SESSION_CHANGED }

interface EvaluationStore {
    suspend fun saveCompleted(scanId: String, evaluations: List<TargetEvaluation>, expected: SessionSnapshot): EvaluationSaveResult
    suspend fun load(scanId: String, expected: SessionSnapshot): List<TargetEvaluation>?
}

/** La sesión permanece bloqueada solo durante la transacción local, nunca durante HTTP. */
class RoomEvaluationStore(
    private val database: HogarScanDatabase,
    private val sessions: PersistentSessionStore
) : EvaluationStore {
    override suspend fun saveCompleted(
        scanId: String,
        evaluations: List<TargetEvaluation>,
        expected: SessionSnapshot
    ): EvaluationSaveResult {
        val records = evaluations.map { evaluation ->
            val identification = evaluation.input.identification
            ScanEvaluationEntity(scanId, evaluation.input.address.value,
                EvaluationResultJson.encode(evaluation.result), identification?.vendor,
                identification?.product, identification?.version, identification?.source?.name)
        }
        return sessions.withCurrentSession(expected) {
            database.withTransaction {
                val owner = "user:${expected.userId}"
                val scan = requireNotNull(database.scanRecordDao().findById(scanId, owner)).toDomain()
                val summary = prepareEvaluatedScanSummary(scan, evaluations, expected)
                if (!database.scanSummaryDao().enqueue(summary)) {
                    EvaluationSaveResult.ALREADY_QUEUED
                } else {
                    database.scanEvaluationDao().insertAll(records)
                    EvaluationSaveResult.SAVED
                }
            }
        } ?: EvaluationSaveResult.SESSION_CHANGED
    }

    override suspend fun load(scanId: String, expected: SessionSnapshot): List<TargetEvaluation>? =
        sessions.withCurrentSession(expected) {
            database.withTransaction {
                val owner = "user:${expected.userId}"
                val scan = database.scanRecordDao().findById(scanId, owner)?.toDomain()
                    ?: return@withTransaction emptyList()
                database.scanEvaluationDao().findByScan(scanId, owner).map { row ->
                    val address = Ipv4Address.fromValue(row.address)
                    val identification = if (row.vendor == null && row.product == null &&
                        row.version == null && row.identificationSource == null) null else {
                        DeclaredIdentification(requireNotNull(row.vendor), requireNotNull(row.product),
                            requireNotNull(row.version), enumValueOf<IdentificationSource>(requireNotNull(row.identificationSource)))
                    }
                    val input = EvaluationInput.fromResults(scan.id, scan.ownerId, address,
                        scan.results.filter { it.address == address }, identification)
                    TargetEvaluation(input, EvaluationResultJson.decode(row.responseJson))
                }
            }
        }
}
