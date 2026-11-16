package com.example.hogarscan.shared.data.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointTest {

    private val endpoint =
        ApiEndpoint.parse("https://api.example.test/")

    @Test
    fun `acepta una base HTTPS y normaliza la barra final`() {
        val result = ApiEndpoint.parse("https://api.example.test")

        assertEquals(
            "https://api.example.test/",
            result.baseUrl.toString()
        )
    }

    @Test
    fun `rechaza bases vacias invalidas o sin HTTPS`() {
        listOf(
            "",
            " ",
            "no-es-una-url",
            "http://api.example.test/",
            " https://api.example.test/",
            "https://api.example.test/\n"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ApiEndpoint.parse(value)
            }
        }
    }

    @Test
    fun `rechaza credenciales rutas parametros y fragmentos en la base`() {
        listOf(
            "https://user:password@api.example.test/",
            "https://api.example.test/auth/",
            "https://api.example.test/?key=value",
            "https://api.example.test/#section"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ApiEndpoint.parse(value)
            }
        }
    }

    @Test
    fun `construye rutas conservando el servidor configurado`() {
        val result = endpoint.urlFor("/health/live")

        assertEquals(
            "https://api.example.test/health/live",
            result.toString()
        )
    }

    @Test
    fun `rechaza destinos externos y rutas con contenido ambiguo`() {
        listOf(
            "https://other.example.test/rules",
            "//other.example.test/rules",
            "rules",
            "/../rules",
            "/auth/../rules",
            "/rules?extra=true",
            "/rules#section",
            "/%2e%2e/rules"
        ).forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                endpoint.urlFor(path)
            }
        }
    }

    @Test
    fun `codifica los valores sin introducir parametros adicionales`() {
        val result = endpoint.urlFor(
            path = "/rules",
            queryParameters = mapOf("value" to "a&extra=true")
        )

        assertEquals(
            "a&extra=true",
            result.queryParameter("value")
        )
        assertEquals(setOf("value"), result.queryParameterNames)
    }
}