package com.example.hogarscan.scan.data.http

import com.example.hogarscan.scan.domain.model.FindingCounts
import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.squareup.moshi.JsonReader
import java.time.Instant
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanSummaryJsonCodecTest {

    @Test
    fun encodesOnlyContractFieldsAndPreservesDatesAndCounts() {
        val json = ScanSummaryJsonCodec.encode(summary())

        val body = JsonReader.of(Buffer().writeUtf8(json)).use {
            it.readJsonValue() as Map<*, *>
        }

        assertEquals(
            setOf(
                "scan_id",
                "summary_generation",
                "started_at",
                "finished_at",
                "addresses_scanned",
                "addresses_responded",
                "accepted_services",
                "findings_by_category"
            ),
            body.keys
        )

        assertEquals(SCAN_ID, body["scan_id"])
        assertEquals(7, (body["summary_generation"] as Number).toInt())

        // Conserva las fechas aunque el reloj haya retrocedido.
        assertEquals("1970-01-01T00:00:02Z", body["started_at"])
        assertEquals("1970-01-01T00:00:01Z", body["finished_at"])

        assertEquals(2, (body["addresses_scanned"] as Number).toInt())
        assertEquals(1, (body["addresses_responded"] as Number).toInt())
        assertEquals(1, (body["accepted_services"] as Number).toInt())

        val counts = body["findings_by_category"] as Map<*, *>

        assertEquals(
            mapOf(
                "TCP_SERVICE_ACCESSIBLE" to 1,
                "PROTOCOL_IDENTIFIED" to 2,
                "PREVENTIVE_REVIEW" to 3,
                "POSSIBLE_CVE_MATCH" to 4,
                "INSUFFICIENT_EVIDENCE" to 5
            ),
            counts.mapValues { (_, value) ->
                (value as Number).toInt()
            }
        )
    }

    @Test
    fun decodesNewAndPreviouslyCreatedReceipts() {
        listOf(true, false).forEach { created ->
            val receipt = ScanSummaryJsonCodec.decodeReceipt(
                receiptJson(
                    id = SCAN_ID.uppercase(),
                    created = created.toString()
                )
            )

            assertEquals(SCAN_ID, receipt.scanId)
            assertEquals(7, receipt.summaryGeneration)
            assertEquals(
                Instant.parse(DATE).toEpochMilli(),
                receipt.receivedAtMillis
            )
            assertEquals(created, receipt.created)
        }
    }

    @Test
    fun rejectsMissingFieldsWrongTypesDuplicatesAndTrailingJson() {
        val invalidBodies = listOf(
            "{}",
            "[]",
            "null",
            receiptJson().replace(",\"created\":true", ""),
            receiptJson(created = "\"true\""),
            receiptJson(created = "null"),
            receiptJson(id = "1-1-1-1-1"),
            receiptJson(
                generation = "7,\"summary_generation\":8"
            ),
            receiptJson() + " {}"
        )

        invalidBodies.forEach { body ->
            assertThrows(Exception::class.java) {
                ScanSummaryJsonCodec.decodeReceipt(body)
            }
        }
    }

    @Test
    fun rejectsInvalidGenerationsWithoutTruncatingThem() {
        listOf(
            "-1",
            "2147483648",
            "7.00000000000000000001",
            "\"7\"",
            "null"
        ).forEach { generation ->
            assertThrows(Exception::class.java) {
                ScanSummaryJsonCodec.decodeReceipt(
                    receiptJson(generation = generation)
                )
            }
        }
    }

    @Test
    fun rejectsImpossibleDatesAndFormatsOutsideTheContract() {
        listOf(
            "2026-02-29T01:02:03Z",
            "2026-11-16T24:00:00Z",
            "2026-11-16T23:59:60Z",
            "2026-11-16T01:02:03.1234Z",
            "2026-11-16T01:02:03+00:00",
            "2026-11-16T01:02:03"
        ).forEach { date ->
            assertThrows(Exception::class.java) {
                ScanSummaryJsonCodec.decodeReceipt(
                    receiptJson(date = date)
                )
            }
        }
    }

    private fun summary() = FrozenScanSummary(
        userId = "0f136c4e-447b-4970-8189-a38c29cb3421",
        scanId = SCAN_ID,
        summaryGeneration = 7,
        startedAtMillis = 2_000L,
        finishedAtMillis = 1_000L,
        addressesScanned = 2,
        addressesResponded = 1,
        acceptedServices = 1,
        findingsByCategory = FindingCounts(
            tcpServiceAccessible = 1,
            protocolIdentified = 2,
            preventiveReview = 3,
            possibleCveMatch = 4,
            insufficientEvidence = 5
        )
    )

    private fun receiptJson(
        id: String = SCAN_ID,
        generation: String = "7",
        date: String = DATE,
        created: String = "true"
    ): String =
        """{"scan_id":"$id","summary_generation":$generation,"received_at":"$date","created":$created}"""

    private companion object {
        const val SCAN_ID = "6ce5e6b1-9509-48a7-bc9d-f0f872364726"
        const val DATE = "2026-11-16T01:02:03.123Z"
    }
}