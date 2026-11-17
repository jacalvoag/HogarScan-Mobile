package com.example.hogarscan.statistics.domain

import com.example.hogarscan.auth.domain.model.SessionSnapshot

data class StatisticsFindingCounts(
    val tcpServiceAccessible: Long,
    val protocolIdentified: Long,
    val preventiveReview: Long,
    val possibleCveMatch: Long,
    val insufficientEvidence: Long
)

data class RecentScan(
    val scanId: String,
    val startedAt: String,
    val acceptedServices: Int,
    val findings: StatisticsFindingCounts
)

data class AccountStatistics(
    val summaryGeneration: Int,
    val generatedAt: String,
    val totalScans: Long,
    val totalAddressesScanned: Long,
    val totalAddressesResponded: Long,
    val totalAcceptedServices: Long,
    val findings: StatisticsFindingCounts,
    val recentScans: List<RecentScan>
)

enum class StatisticsFailure { CONNECTION, TIMEOUT, SESSION, RATE_LIMITED, INVALID_RESPONSE, OTHER }
sealed interface StatisticsOutcome {
    data class Success(val statistics: AccountStatistics) : StatisticsOutcome
    data class Failure(val reason: StatisticsFailure) : StatisticsOutcome
}
fun interface StatisticsGateway {
    suspend fun getStatistics(session: SessionSnapshot): StatisticsOutcome
}
