package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.*
import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.squareup.moshi.JsonReader
import okio.Buffer

/** Lee la respuesta sin convertir una fuente desconocida en un resultado negativo. */
internal object EvaluationResponseJson {
    fun decode(json: String): EvaluationResult = JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
        val root = readValue(reader, 0).obj()
        require(reader.peek() == JsonReader.Token.END_DOCUMENT)
        val sources = root.required("sources").obj()
        val nvd = enumValueOf<SourceState>(sources.string("nvd"))
        val kev = enumValueOf<SourceState>(sources.string("kev"))
        val checkedAt = sources.nullableString("checked_at")?.timestamp()
        require((nvd == SourceState.NOT_QUERIED && kev == SourceState.NOT_QUERIED) == (checkedAt == null))

        val findings = root.array("findings").map { value ->
            val f = value.obj()
            val category = enumValueOf<FindingCategory>(f.string("category"))
            val confidence = enumValueOf<FindingConfidence>(f.string("confidence"))
            val cve = f.nullableString("cve")
            require(if (category == FindingCategory.POSSIBLE_CVE_MATCH) {
                confidence == FindingConfidence.POSSIBLE && cve != null && CVE.matches(cve)
            } else cve == null)
            Finding(category, f.string("title"), enumValueOf<FindingPriority>(f.string("priority")),
                confidence, f.string("evidence"), f.string("explanation"), f.string("action"),
                f.string("limitation"), f.string("rule_id"), cve)
        }
        val matches = root.array("possible_cve_matches").map { value ->
            val m = value.obj()
            val cve = m.string("cve")
            require(CVE.matches(cve))
            val vendor = m.string("vendor")
            val product = m.string("product")
            val version = m.string("version")
            val source = enumValueOf<IdentificationSource>(m.string("identification_source"))
            DeclaredIdentification(vendor, product, version, source)
            val condition = m.string("affected_condition")
            require(condition.isNotEmpty())
            val url = m.string("nvd_url")
            require(url == "https://nvd.nist.gov/vuln/detail/$cve")
            val inKev = m.nullableBoolean("in_kev")
            PossibleCveMatch(cve, vendor, product, version, source, condition, url,
                m.string("nvd_checked_at").timestamp(), inKev)
        }
        val findingCves = findings.mapNotNull { it.cve }
        val matchCves = matches.map { it.cve }
        require(findingCves.size == findingCves.toSet().size && matchCves.size == matchCves.toSet().size)
        require(findingCves.toSet() == matchCves.toSet())
        if (matches.isEmpty()) {
            require(kev == SourceState.NOT_QUERIED)
        } else {
            require(nvd == SourceState.AVAILABLE)
            require(matches.all { if (kev == SourceState.AVAILABLE) it.inKev != null else it.inKev == null })
        }
        EvaluationResult(root.string("ruleset_version"), root.string("evaluated_at").timestamp(),
            EvaluationSources(nvd, kev, checkedAt), findings, matches)
    }

    private val CVE = Regex("CVE-[0-9]{4}-[0-9]{4,}")

    internal fun readValue(reader: JsonReader, depth: Int): Any? {
        require(depth <= 32)
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_OBJECT -> {
                val fields = mutableMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!fields.containsKey(name))
                    fields[name] = readValue(reader, depth + 1)
                }
                reader.endObject()
                fields
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                val values = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) values.add(readValue(reader, depth + 1))
                reader.endArray()
                values
            }
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NULL -> reader.nextNull<Any>()
            JsonReader.Token.NUMBER -> JsonNumber(reader.nextString())
            else -> throw IllegalArgumentException("JSON inválido")
        }
    }

    internal data class JsonNumber(val value: String)

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?>
        ?: throw IllegalArgumentException("Se esperaba un objeto")
    private fun Map<String, Any?>.required(key: String): Any? {
        require(containsKey(key)) { "Falta $key" }
        return this[key]
    }
    private fun Map<String, Any?>.string(key: String): String = required(key) as? String
        ?: throw IllegalArgumentException("Se esperaba texto en $key")
    private fun Map<String, Any?>.nullableString(key: String): String? = required(key).let {
        require(it == null || it is String)
        it as String?
    }
    private fun Map<String, Any?>.nullableBoolean(key: String): Boolean? = required(key).let {
        require(it == null || it is Boolean)
        it as Boolean?
    }
    private fun Map<String, Any?>.array(key: String): List<Any?> = required(key) as? List<*>
        ?: throw IllegalArgumentException("Se esperaba una lista en $key")
    private fun String.timestamp(): String { UtcTimestamp.parseMillis(this); return this }
}
