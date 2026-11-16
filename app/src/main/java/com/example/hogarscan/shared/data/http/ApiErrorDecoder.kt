package com.example.hogarscan.shared.data.http

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import java.io.IOException
import okio.Buffer

object ApiErrorDecoder {

    fun decode(failure: ApiResult.HttpFailure): ApiError {
        val parsed = failure.bodyJson?.let(::parse)

        return ApiError(
            statusCode = failure.statusCode,
            code = parsed?.code,
            message = parsed?.message ?: fallbackMessage(failure.statusCode),
            currentSummaryGeneration = parsed?.generation,
            retryAfterSeconds = failure.retryAfterSeconds
        )
    }

    private fun parse(json: String): ParsedError? = try {
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var code: String? = null
            var message: String? = null
            var generation: Int? = null
            val names = mutableSetOf<String>()

            reader.beginObject()

            while (reader.hasNext()) {
                val name = reader.nextName()
                require(names.add(name)) { "Campo duplicado." }

                when (name) {
                    "code" -> code = readString(reader)
                    "message" -> message = readString(reader)
                    "details" -> generation = readDetails(reader)
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            require(reader.peek() == JsonReader.Token.END_DOCUMENT)

            val validCode = requireNotNull(code).also {
                require(it.isNotBlank())
            }
            val validMessage = requireNotNull(message).also {
                require(it.isNotBlank())
            }

            if (validCode in GENERATION_ERRORS) {
                requireNotNull(generation)
            }

            ParsedError(
                code = validCode,
                message = validMessage,
                generation = generation.takeIf {
                    validCode in GENERATION_ERRORS
                }
            )
        }
    } catch (_: IOException) {
        null
    } catch (_: JsonDataException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private fun readString(reader: JsonReader): String {
        require(reader.peek() == JsonReader.Token.STRING)
        return reader.nextString()
    }

    private fun readDetails(reader: JsonReader): Int? {
        var generation: Int? = null
        val names = mutableSetOf<String>()

        reader.beginObject()

        while (reader.hasNext()) {
            val name = reader.nextName()
            require(names.add(name)) { "Campo duplicado." }

            if (name == "current_summary_generation") {
                require(reader.peek() == JsonReader.Token.NUMBER)

                generation = reader.nextString()
                    .toBigDecimal()
                    .intValueExact()

                require(generation >= 0)
            } else {
                reader.skipValue()
            }
        }

        reader.endObject()
        return generation
    }

    private fun fallbackMessage(statusCode: Int): String =
        when (statusCode) {
            429 -> "Se alcanzó el límite de solicitudes. Intenta de nuevo más tarde."
            503 -> "El servicio no está disponible. Intenta más tarde."
            else -> "No se pudo completar la solicitud."
        }

    private class ParsedError(
        val code: String,
        val message: String,
        val generation: Int?
    )

    private val GENERATION_ERRORS = setOf(
        "STALE_SUMMARY_GENERATION",
        "INVALID_SUMMARY_GENERATION"
    )
}