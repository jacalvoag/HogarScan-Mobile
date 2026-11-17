package com.example.hogarscan.scan.data.http

import com.example.hogarscan.scan.domain.model.FrozenScanSummary
import com.example.hogarscan.scan.domain.model.ScanSummaryReceipt
import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.Instant
import java.util.UUID
import okio.Buffer

internal object ScanSummaryJsonCodec {

    fun encode(summary: FrozenScanSummary): String {
        val buffer = Buffer()

        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()

            writer.name("scan_id").value(summary.scanId)
            writer.name("summary_generation")
                .value(summary.summaryGeneration.toLong())

            writer.name("started_at").value(
                Instant.ofEpochMilli(summary.startedAtMillis).toString()
            )
            writer.name("finished_at").value(
                Instant.ofEpochMilli(summary.finishedAtMillis).toString()
            )

            writer.name("addresses_scanned")
                .value(summary.addressesScanned.toLong())
            writer.name("addresses_responded")
                .value(summary.addressesResponded.toLong())
            writer.name("accepted_services")
                .value(summary.acceptedServices.toLong())

            val counts = summary.findingsByCategory

            writer.name("findings_by_category").beginObject()

            writer.name("TCP_SERVICE_ACCESSIBLE")
                .value(counts.tcpServiceAccessible.toLong())
            writer.name("PROTOCOL_IDENTIFIED")
                .value(counts.protocolIdentified.toLong())
            writer.name("PREVENTIVE_REVIEW")
                .value(counts.preventiveReview.toLong())
            writer.name("POSSIBLE_CVE_MATCH")
                .value(counts.possibleCveMatch.toLong())
            writer.name("INSUFFICIENT_EVIDENCE")
                .value(counts.insufficientEvidence.toLong())

            writer.endObject()
            writer.endObject()
        }

        return buffer.readUtf8()
    }

    fun decodeReceipt(json: String): ScanSummaryReceipt =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var scanId: String? = null
            var generation: Int? = null
            var receivedAt: Long? = null
            var created: Boolean? = null
            val names = mutableSetOf<String>()

            reader.beginObject()

            while (reader.hasNext()) {
                val name = reader.nextName()

                require(names.add(name)) {
                    "El recibo contiene un campo duplicado."
                }

                when (name) {
                    "scan_id" -> {
                        val raw = readString(reader)
                        val normalized = UUID.fromString(raw).toString()

                        require(normalized.equals(raw, ignoreCase = true)) {
                            "El identificador del recibo no es válido."
                        }

                        scanId = normalized
                    }

                    "summary_generation" -> {
                        require(reader.peek() == JsonReader.Token.NUMBER)

                        generation = reader.nextString()
                            .toBigDecimal()
                            .intValueExact()
                    }

                    "received_at" -> {
                        receivedAt = UtcTimestamp.parseMillis(
                            readString(reader)
                        )
                    }

                    "created" -> {
                        require(reader.peek() == JsonReader.Token.BOOLEAN)
                        created = reader.nextBoolean()
                    }

                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            require(reader.peek() == JsonReader.Token.END_DOCUMENT) {
                "El recibo contiene datos después del documento JSON."
            }

            ScanSummaryReceipt(
                scanId = requireNotNull(scanId),
                summaryGeneration = requireNotNull(generation),
                receivedAtMillis = requireNotNull(receivedAt),
                created = requireNotNull(created)
            )
        }

    private fun readString(reader: JsonReader): String {
        require(reader.peek() == JsonReader.Token.STRING)
        return reader.nextString()
    }
}