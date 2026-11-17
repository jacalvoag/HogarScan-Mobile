package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.EvaluationResult
import com.squareup.moshi.JsonWriter
import okio.Buffer

/** Formato local compatible con la respuesta validada de la API. */
internal object EvaluationResultJson {
    fun encode(result: EvaluationResult): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.serializeNulls = true
            writer.beginObject()
            writer.name("ruleset_version").value(result.rulesetVersion)
            writer.name("evaluated_at").value(result.evaluatedAt)
            writer.name("sources").beginObject()
            writer.name("nvd").value(result.sources.nvd.name)
            writer.name("kev").value(result.sources.kev.name)
            writer.name("checked_at").value(result.sources.checkedAt)
            writer.endObject()
            writer.name("findings").beginArray()
            result.findings.forEach { finding ->
                writer.beginObject()
                writer.name("category").value(finding.category.name)
                writer.name("title").value(finding.title)
                writer.name("priority").value(finding.priority.name)
                writer.name("confidence").value(finding.confidence.name)
                writer.name("evidence").value(finding.evidence)
                writer.name("explanation").value(finding.explanation)
                writer.name("action").value(finding.action)
                writer.name("limitation").value(finding.limitation)
                writer.name("rule_id").value(finding.ruleId)
                writer.name("cve").value(finding.cve)
                writer.endObject()
            }
            writer.endArray()
            writer.name("possible_cve_matches").beginArray()
            result.possibleCveMatches.forEach { match ->
                writer.beginObject()
                writer.name("cve").value(match.cve)
                writer.name("vendor").value(match.vendor)
                writer.name("product").value(match.product)
                writer.name("version").value(match.version)
                writer.name("identification_source").value(match.identificationSource.name)
                writer.name("affected_condition").value(match.affectedCondition)
                writer.name("nvd_url").value(match.nvdUrl)
                writer.name("nvd_checked_at").value(match.nvdCheckedAt)
                writer.name("in_kev").value(match.inKev)
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
        return buffer.readUtf8()
    }
    fun decode(json: String): EvaluationResult = EvaluationResponseJson.decode(json)
}
