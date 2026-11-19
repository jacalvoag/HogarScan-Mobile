package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.squareup.moshi.JsonWriter
import okio.Buffer

/** Serializa solo los campos admitidos por POST /evaluations. */
internal object EvaluationRequestJson {
    fun encode(input: EvaluationInput): String {
        val request = input.toEvaluationRequest()
        request.identification?.let { identification ->
            val encoder = Charsets.UTF_8.newEncoder()
            require(encoder.canEncode(identification.vendor))
            require(encoder.canEncode(identification.product))
            require(encoder.canEncode(identification.version))
        }
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name("probes").beginArray()
            request.probes.forEach { probe ->
                writer.beginObject()
                writer.name("port").value(probe.port.toLong())
                writer.name("outcome").value(probe.outcome.name)
                probe.service?.let {
                    writer.name("protocol").value(it.protocol.name)
                    writer.name("evidence_kind").value(it.protocol.evidenceKind)
                }
                writer.endObject()
            }
            writer.endArray()
            request.identification?.let { identification ->
                writer.name("identification").beginObject()
                writer.name("vendor").value(identification.vendor)
                writer.name("product").value(identification.product)
                writer.name("version").value(identification.version)
                writer.name("source").value(identification.source.name)
                identification.servicePort?.let { writer.name("service_port").value(it.toLong()) }
                writer.endObject()
            }
            writer.endObject()
        }
        return buffer.readUtf8()
    }
}
