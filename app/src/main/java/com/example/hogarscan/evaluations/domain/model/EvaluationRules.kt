package com.example.hogarscan.evaluations.domain.model

/** Metadatos publicados por el servidor; no constituyen un motor de evaluación local. */
data class EvaluationRules(
    val rulesetVersion: String,
    val publishedAt: String,
    val categories: Set<FindingCategory>,
    val maxProbesPerEvaluation: Int,
    val caveat: String,
    val maxAddressesPerScan: Int,
    val allowedPorts: Set<Int>
)
