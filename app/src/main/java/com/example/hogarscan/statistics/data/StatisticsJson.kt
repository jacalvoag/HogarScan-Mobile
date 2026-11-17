package com.example.hogarscan.statistics.data

import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.example.hogarscan.statistics.domain.AccountStatistics
import com.example.hogarscan.statistics.domain.RecentScan
import com.example.hogarscan.statistics.domain.StatisticsFindingCounts
import com.squareup.moshi.JsonReader
import java.util.UUID
import okio.Buffer

internal object StatisticsJson {
    fun decode(json: String): AccountStatistics = JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
        var generation: Int? = null
        var generated: String? = null
        var scans: Long? = null
        var scanned: Long? = null
        var responded: Long? = null
        var accepted: Long? = null
        var findings: StatisticsFindingCounts? = null
        var recent: List<RecentScan>? = null
        readObject(reader) { name ->
            when (name) {
                "summary_generation" -> generation = number(reader, Int.MAX_VALUE.toLong()).toInt()
                "generated_at" -> generated = timestamp(reader)
                "total_scans" -> scans = number(reader)
                "total_addresses_scanned" -> scanned = number(reader)
                "total_addresses_responded" -> responded = number(reader)
                "total_accepted_services" -> accepted = number(reader)
                "findings_by_category" -> findings = counts(reader, Long.MAX_VALUE)
                "recent_scans" -> {
                    val items = mutableListOf<RecentScan>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(items.size < 10)
                        items.add(recentScan(reader))
                    }
                    reader.endArray()
                    recent = items
                }
                else -> reader.skipValue()
            }
        }
        require(reader.peek() == JsonReader.Token.END_DOCUMENT)
        AccountStatistics(requireNotNull(generation), requireNotNull(generated), requireNotNull(scans),
            requireNotNull(scanned), requireNotNull(responded), requireNotNull(accepted),
            requireNotNull(findings), requireNotNull(recent))
    }

    private fun recentScan(reader: JsonReader): RecentScan {
        var id: String? = null
        var started: String? = null
        var accepted: Int? = null
        var findings: StatisticsFindingCounts? = null
        readObject(reader) { name ->
            when (name) {
                "scan_id" -> {
                    val raw = string(reader)
                    val normalized = UUID.fromString(raw).toString()
                    require(normalized.equals(raw, ignoreCase = true))
                    id = normalized
                }
                "started_at" -> started = timestamp(reader)
                "accepted_services" -> accepted = number(reader, 1524).toInt()
                "findings_by_category" -> findings = counts(reader, 100_000)
                else -> reader.skipValue()
            }
        }
        return RecentScan(requireNotNull(id), requireNotNull(started), requireNotNull(accepted), requireNotNull(findings))
    }

    private fun counts(reader: JsonReader, max: Long): StatisticsFindingCounts {
        val values = mutableMapOf<String, Long>()
        readObject(reader) { name ->
            when (name) {
                "TCP_SERVICE_ACCESSIBLE", "PROTOCOL_IDENTIFIED", "PREVENTIVE_REVIEW",
                "POSSIBLE_CVE_MATCH", "INSUFFICIENT_EVIDENCE" -> values[name] = number(reader, max)
                else -> reader.skipValue()
            }
        }
        return StatisticsFindingCounts(requireNotNull(values["TCP_SERVICE_ACCESSIBLE"]),
            requireNotNull(values["PROTOCOL_IDENTIFIED"]), requireNotNull(values["PREVENTIVE_REVIEW"]),
            requireNotNull(values["POSSIBLE_CVE_MATCH"]), requireNotNull(values["INSUFFICIENT_EVIDENCE"]))
    }

    private fun number(reader: JsonReader, max: Long = Long.MAX_VALUE): Long {
        require(reader.peek() == JsonReader.Token.NUMBER)
        val value = reader.nextString().toBigDecimal().longValueExact()
        require(value in 0..max)
        return value
    }
    private fun string(reader: JsonReader): String {
        require(reader.peek() == JsonReader.Token.STRING)
        return reader.nextString()
    }
    private fun timestamp(reader: JsonReader): String = string(reader).also { UtcTimestamp.parseMillis(it) }
    private fun readObject(reader: JsonReader, field: (String) -> Unit) {
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(seen.add(name))
            field(name)
        }
        reader.endObject()
    }
}
