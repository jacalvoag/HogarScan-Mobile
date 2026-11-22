package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrozenScanSummaryTest {

    private val findings = FindingCounts(
        tcpServiceAccessible = 1,
        protocolIdentified = 0,
        preventiveReview = 0,
        possibleCveMatch = 0,
        insufficientEvidence = 0
    )

    private fun summary(
        userId: String = "0F136C4E-447B-4970-8189-A38C29CB3421",
        scanId: String = "6CE5E6B1-9509-48A7-BC9D-F0F872364726",
        generation: Int = 0,
        startedAt: Long = 1_000L,
        finishedAt: Long = 2_000L,
        scanned: Int = 2,
        responded: Int = 1,
        services: Int = 1,
        counts: FindingCounts = findings
    ) = FrozenScanSummary(
        userId = userId,
        scanId = scanId,
        summaryGeneration = generation,
        startedAtMillis = startedAt,
        finishedAtMillis = finishedAt,
        addressesScanned = scanned,
        addressesResponded = responded,
        acceptedServices = services,
        findingsByCategory = counts
    )

    @Test
    fun normalizesIdentifiersWithoutChangingGeneration() {
        val result = summary(generation = 7)

        assertEquals(
            "0f136c4e-447b-4970-8189-a38c29cb3421",
            result.userId
        )
        assertEquals(
            "6ce5e6b1-9509-48a7-bc9d-f0f872364726",
            result.scanId
        )
        assertEquals(7, result.summaryGeneration)
    }

    @Test
    fun rejectsLocalProfilesAndMalformedScanIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(userId = "local:existing-profile")
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(scanId = "1-1-1-1-1")
        }
    }

    @Test
    fun rejectsCountsThatContradictTheAuthorizedScope() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(scanned = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(scanned = 255)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(scanned = 2, responded = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(responded = 1, services = 7)
        }
    }

    @Test
    fun acceptsTheMaximumPilotScope() {
        val result = summary(
            scanned = 254,
            responded = 254,
            services = 1_524
        )

        assertEquals(254, result.addressesScanned)
        assertEquals(1_524, result.acceptedServices)
    }

    @Test
    fun preservesDatesWhenTheDeviceClockMovesBackwards() {
        val result = summary(
            startedAt = 2_000L,
            finishedAt = 1_000L
        )

        assertEquals(2_000L, result.startedAtMillis)
        assertEquals(1_000L, result.finishedAtMillis)
    }

    @Test
    fun rejectsInvalidGenerationAndDates() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(generation = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(startedAt = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            summary(finishedAt = Long.MAX_VALUE)
        }
    }

    @Test
    fun validatesFindingLimitsWithoutEquatingFindingsToServices() {
        val counts = findings.copy(
            preventiveReview = 100_000,
            insufficientEvidence = 2
        )

        val result = summary(counts = counts)

        assertEquals(counts, result.findingsByCategory)
        assertEquals(1, result.acceptedServices)

        assertThrows(IllegalArgumentException::class.java) {
            findings.copy(possibleCveMatch = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            findings.copy(protocolIdentified = 100_001)
        }
    }
}