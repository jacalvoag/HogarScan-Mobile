package com.example.hogarscan.scan.domain.model

data class FindingCounts(
    val tcpServiceAccessible: Int,
    val protocolIdentified: Int,
    val preventiveReview: Int,
    val possibleCveMatch: Int,
    val insufficientEvidence: Int
) {
    init {
        require(
            listOf(
                tcpServiceAccessible,
                protocolIdentified,
                preventiveReview,
                possibleCveMatch,
                insufficientEvidence
            ).all { it in 0..100_000 }
        ) {
            "Cada conteo de hallazgos debe estar entre 0 y 100000."
        }
    }
}