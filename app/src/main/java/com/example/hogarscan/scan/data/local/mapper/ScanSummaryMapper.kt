package com.example.hogarscan.scan.data.local.mapper

import com.example.hogarscan.scan.data.local.entity.ScanSummaryEntity
import com.example.hogarscan.scan.domain.model.FrozenScanSummary

fun FrozenScanSummary.toLocal(): ScanSummaryEntity =
    ScanSummaryEntity(
        scanId = scanId,
        userId = userId,
        summaryGeneration = summaryGeneration,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        addressesScanned = addressesScanned,
        addressesResponded = addressesResponded,
        acceptedServices = acceptedServices,
        tcpServiceAccessible = findingsByCategory.tcpServiceAccessible,
        protocolIdentified = findingsByCategory.protocolIdentified,
        preventiveReview = findingsByCategory.preventiveReview,
        possibleCveMatch = findingsByCategory.possibleCveMatch,
        insufficientEvidence = findingsByCategory.insufficientEvidence
    )