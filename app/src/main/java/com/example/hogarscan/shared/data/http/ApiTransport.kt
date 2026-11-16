package com.example.hogarscan.shared.data.http

enum class ApiMethod { GET, POST, DELETE }

class ApiRequest(
    val method: ApiMethod,
    val path: String,
    val bodyJson: String? = null,
    queryParameters: Map<String, String> = emptyMap(),
    val bearerToken: String? = null
) {
    val queryParameters: Map<String, String> = queryParameters.toMap()

    init {
        require(method == ApiMethod.POST || bodyJson == null) {
            "Solo las solicitudes POST admiten cuerpo en esta API."
        }
        require(bodyJson == null || bodyJson.toByteArray(Charsets.UTF_8).size <= 16_384) {
            "El cuerpo supera el límite de 16 KiB de la API."
        }
    }
}

sealed interface ApiResult {
    class Success(
        val statusCode: Int,
        val bodyJson: String?
    ) : ApiResult

    class HttpFailure(
        val statusCode: Int,
        val bodyJson: String?,
        val retryAfterSeconds: Long?
    ) : ApiResult

    data object NetworkFailure : ApiResult
    data object TimedOut : ApiResult
    data object TlsFailure : ApiResult
    data object InvalidResponse : ApiResult
    data object SessionUnavailable : ApiResult
    data object SessionChanged : ApiResult
}

fun interface ApiTransport {
    suspend fun execute(request: ApiRequest): ApiResult
}