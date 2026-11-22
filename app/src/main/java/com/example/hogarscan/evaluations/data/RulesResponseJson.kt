package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.EvaluationRules
import com.example.hogarscan.evaluations.domain.model.FindingCategory
import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.squareup.moshi.JsonReader
import okio.Buffer

internal object RulesResponseJson {
    fun decode(json: String): EvaluationRules = JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
        val root = EvaluationResponseJson.readValue(reader, 0) as? Map<*, *>
            ?: throw IllegalArgumentException("Se esperaba un objeto de reglas")
        require(reader.peek() == JsonReader.Token.END_DOCUMENT)
        fun string(name: String): String = root[name] as? String
            ?: throw IllegalArgumentException("Falta $name")
        fun number(value: Any?): Int = (value as? EvaluationResponseJson.JsonNumber)?.value
            ?.toBigDecimal()?.intValueExact() ?: throw IllegalArgumentException("Número no válido")
        fun array(name: String): List<*> = root[name] as? List<*>
            ?: throw IllegalArgumentException("Falta $name")
        val version = string("ruleset_version")
        val published = string("published_at")
        UtcTimestamp.parseMillis(published)
        val caveat = string("caveat")
        val categories = array("categories").map {
            enumValueOf<FindingCategory>(it as? String ?: throw IllegalArgumentException("Categoría no válida"))
        }
        val ports = array("allowed_ports").map(::number)
        val maxProbes = number(root["max_probes_per_evaluation"])
        val maxAddresses = number(root["max_addresses_per_scan"])
        require(categories.size == 5 && categories.toSet() == FindingCategory.entries.toSet())
        require(ports.size == ports.toSet().size && ports.toSet() in listOf(setOf(22, 23, 80, 443, 445, 3389), setOf(21, 22, 23, 80, 443, 445, 3389)))
        require(maxProbes == 6 && maxAddresses == 254)
        EvaluationRules(version, published, categories.toSet(), maxProbes, caveat, maxAddresses, ports.toSet())
    }
}
