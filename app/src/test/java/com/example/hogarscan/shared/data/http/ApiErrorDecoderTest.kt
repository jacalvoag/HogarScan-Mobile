package com.example.hogarscan.shared.data.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorDecoderTest {

    @Test
    fun `conserva estado codigo mensaje y espera del servidor`() {
        val error = decode(
            """{"code":"RATE_LIMITED","message":"Espera un momento."}""",
            status = 429,
            retryAfter = 60L
        )

        assertEquals(429, error.statusCode)
        assertEquals("RATE_LIMITED", error.code)
        assertEquals("Espera un momento.", error.message)
        assertEquals(60L, error.retryAfterSeconds)
        assertNull(error.currentSummaryGeneration)
    }

    @Test
    fun `acepta ambas causas de conflicto y los limites de generacion`() {
        listOf(
            "STALE_SUMMARY_GENERATION",
            "INVALID_SUMMARY_GENERATION"
        ).forEach { code ->
            listOf(0, Int.MAX_VALUE).forEach { generation ->
                val error = decode(
                    """
                    {
                        "code":"$code",
                        "message":"Conflicto.",
                        "details":{
                            "current_summary_generation":$generation
                        }
                    }
                    """.trimIndent()
                )

                assertEquals(code, error.code)
                assertEquals(
                    generation,
                    error.currentSummaryGeneration
                )
            }
        }
    }

    @Test
    fun `un conflicto incompleto no proporciona una generacion utilizable`() {
        listOf(
            """
            {
                "code":"STALE_SUMMARY_GENERATION",
                "message":"Conflicto."
            }
            """,
            """
            {
                "code":"INVALID_SUMMARY_GENERATION",
                "message":"Conflicto.",
                "details":{}
            }
            """
        ).forEach { body ->
            val error = decode(body)

            assertNull(error.code)
            assertNull(error.currentSummaryGeneration)
            assertEquals(409, error.statusCode)
        }
    }

    @Test
    fun `rechaza generaciones negativas fraccionarias fuera de rango o con otro tipo`() {
        listOf(
            "-1",
            "1.5",
            "2147483648",
            "\"1\"",
            "null",
            "true"
        ).forEach { generation ->
            val error = decode(
                """
                {
                    "code":"STALE_SUMMARY_GENERATION",
                    "message":"Conflicto.",
                    "details":{
                        "current_summary_generation":$generation
                    }
                }
                """.trimIndent()
            )

            assertNull(error.code)
            assertNull(error.currentSummaryGeneration)
        }
    }

    @Test
    fun `acepta un numero entero sin truncar decimales de precision alta`() {
        val valid = decode(
            """
            {
                "code":"STALE_SUMMARY_GENERATION",
                "message":"Conflicto.",
                "details":{"current_summary_generation":1.0}
            }
            """.trimIndent()
        )
        val invalid = decode(
            """
            {
                "code":"STALE_SUMMARY_GENERATION",
                "message":"Conflicto.",
                "details":{
                    "current_summary_generation":1.00000000000000000001
                }
            }
            """.trimIndent()
        )

        assertEquals(1, valid.currentSummaryGeneration)
        assertNull(invalid.code)
    }

    @Test
    fun `conserva codigos futuros sin inventar acciones de sincronizacion`() {
        val error = decode(
            """
            {
                "code":"FUTURE_ERROR",
                "message":"Otra causa.",
                "details":{"current_summary_generation":3},
                "extra":{"value":true}
            }
            """.trimIndent()
        )

        assertEquals("FUTURE_ERROR", error.code)
        assertEquals("Otra causa.", error.message)
        assertNull(error.currentSummaryGeneration)
    }

    @Test
    fun `el mensaje no determina la causa del error`() {
        val error = decode(
            """
            {
                "code":"INVALID_CREDENTIALS",
                "message":"UNAUTHENTICATED"
            }
            """.trimIndent(),
            status = 401
        )

        assertEquals("INVALID_CREDENTIALS", error.code)
        assertEquals("UNAUTHENTICATED", error.message)
    }

    @Test
    fun `rechaza campos duplicados en el error y en sus detalles`() {
        listOf(
            """
            {
                "code":"INVALID_REQUEST",
                "code":"UNAUTHENTICATED",
                "message":"Error."
            }
            """,
            """
            {
                "code":"INVALID_REQUEST",
                "message":"Uno.",
                "message":"Otro."
            }
            """,
            """
            {
                "code":"STALE_SUMMARY_GENERATION",
                "message":"Error.",
                "details":{
                    "current_summary_generation":0,
                    "current_summary_generation":1
                }
            }
            """
        ).forEach { body ->
            assertNull(decode(body).code)
        }
    }

    @Test
    fun `una respuesta ausente invalida o incompleta conserva el estado HTTP`() {
        listOf(
            null,
            "",
            "<html>Error</html>",
            "null",
            "[]",
            "{}",
            """{"code":1,"message":"Error."}""",
            """{"code":"INVALID_REQUEST","message":false}""",
            """{"code":"INVALID_REQUEST","message":" "}""",
            """{"code":"INVALID_REQUEST","message":"Error.","details":null}""",
            """{"code":"INVALID_REQUEST","message":"Error."} {}"""
        ).forEach { body ->
            val error = decode(
                body,
                status = 502,
                retryAfter = 10L
            )

            assertEquals(502, error.statusCode)
            assertEquals(10L, error.retryAfterSeconds)
            assertEquals(
                "No se pudo completar la solicitud.",
                error.message
            )
            assertNull(error.code)
            assertNull(error.currentSummaryGeneration)
        }
    }

    private fun decode(
        body: String?,
        status: Int = 409,
        retryAfter: Long? = null
    ): ApiError = ApiErrorDecoder.decode(
        ApiResult.HttpFailure(status, body, retryAfter)
    )
}