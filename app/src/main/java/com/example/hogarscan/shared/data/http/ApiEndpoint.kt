package com.example.hogarscan.shared.data.http

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ApiEndpoint private constructor(
    val baseUrl: HttpUrl
) {
    fun urlFor(
        path: String,
        queryParameters: Map<String, String> = emptyMap()
    ): HttpUrl {
        require(PATH_PATTERN.matches(path)) {
            "La ruta debe ser absoluta y contener segmentos simples."
        }

        val builder = baseUrl.newBuilder()
            .encodedPath(path)

        queryParameters.forEach { (name, value) ->
            builder.addQueryParameter(name, value)
        }

        return builder.build()
    }

    companion object {
        private val PATH_PATTERN =
            Regex("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")

        fun parse(value: String): ApiEndpoint {
            require(value.isNotBlank() && value == value.trim()) {
                "Configura una URL base sin espacios exteriores."
            }
            require(value.none { it.code < 32 || it.code == 127 }) {
                "La URL base contiene caracteres de control."
            }

            val url = requireNotNull(value.toHttpUrlOrNull()) {
                "La URL base no es válida."
            }

            require(url.isHttps) {
                "La API debe utilizar HTTPS."
            }
            require(
                url.encodedUsername.isEmpty() &&
                        url.encodedPassword.isEmpty()
            ) {
                "La URL base no debe contener credenciales."
            }
            require(url.encodedPath == "/") {
                "La URL base debe apuntar a la raíz del servidor."
            }
            require(
                url.encodedQuery == null &&
                        url.encodedFragment == null
            ) {
                "La URL base no debe contener parámetros ni fragmentos."
            }

            return ApiEndpoint(url)
        }
    }
}