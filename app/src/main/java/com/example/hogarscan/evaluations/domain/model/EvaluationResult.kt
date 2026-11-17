package com.example.hogarscan.evaluations.domain.model

enum class SourceState { NOT_QUERIED, AVAILABLE, UNAVAILABLE }
enum class FindingCategory {
    TCP_SERVICE_ACCESSIBLE, PROTOCOL_IDENTIFIED, PREVENTIVE_REVIEW,
    POSSIBLE_CVE_MATCH, INSUFFICIENT_EVIDENCE
}
enum class FindingPriority { LOW, MEDIUM, HIGH, UNDETERMINED }
enum class FindingConfidence { OBSERVED, DECLARED, POSSIBLE, INCONCLUSIVE }

data class EvaluationSources(
    val nvd: SourceState,
    val kev: SourceState,
    val checkedAt: String?
)

data class Finding(
    val category: FindingCategory,
    val title: String,
    val priority: FindingPriority,
    val confidence: FindingConfidence,
    val evidence: String,
    val explanation: String,
    val action: String,
    val limitation: String,
    val ruleId: String,
    val cve: String?
)

data class PossibleCveMatch(
    val cve: String,
    val vendor: String,
    val product: String,
    val version: String,
    val identificationSource: IdentificationSource,
    val affectedCondition: String,
    val nvdUrl: String,
    val nvdCheckedAt: String,
    val inKev: Boolean?
)

data class EvaluationResult(
    val rulesetVersion: String,
    val evaluatedAt: String,
    val sources: EvaluationSources,
    val findings: List<Finding>,
    val possibleCveMatches: List<PossibleCveMatch>
)
