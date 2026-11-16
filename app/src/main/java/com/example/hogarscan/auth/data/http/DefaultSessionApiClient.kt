package com.example.hogarscan.auth.data.http

import com.example.hogarscan.auth.data.session.SessionCredentialSource
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.shared.data.http.ApiErrorDecoder
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport

class DefaultSessionApiClient(
    private val transport: ApiTransport,
    private val sessions: SessionCredentialSource,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SessionApiClient {

    override suspend fun execute(
        request: ApiRequest,
        expectedSession: SessionSnapshot
    ): ApiResult {
        require(request.bearerToken == null) {
            "La credencial debe proporcionarla la infraestructura de sesión."
        }

        sessionProblem(expectedSession)?.let { return it }

        val token = sessions.tokenFor(expectedSession, nowMillis())

        sessionProblem(expectedSession)?.let { return it }

        if (token.isNullOrBlank()) {
            return invalidateExpected(expectedSession)
        }

        val result = transport.execute(
            ApiRequest(
                method = request.method,
                path = request.path,
                bodyJson = request.bodyJson,
                queryParameters = request.queryParameters,
                bearerToken = token
            )
        )

        sessionProblem(expectedSession)?.let { return it }

        if (result is ApiResult.HttpFailure &&
            result.statusCode == 401 &&
            ApiErrorDecoder.decode(result).code == "UNAUTHENTICATED"
        ) {
            return invalidateExpected(expectedSession)
        }

        return result
    }

    private suspend fun sessionProblem(
        expected: SessionSnapshot
    ): ApiResult? {
        val current = (sessions.state.value as? SessionState.Authenticated)
            ?.session
            ?: return ApiResult.SessionUnavailable

        if (current.contextId != expected.contextId ||
            current.userId != expected.userId
        ) {
            return ApiResult.SessionChanged
        }

        if (current.expiresAtMillis <= nowMillis()) {
            return invalidateExpected(expected)
        }

        return null
    }

    private suspend fun invalidateExpected(
        expected: SessionSnapshot
    ): ApiResult =
        if (sessions.invalidate(expected)) {
            ApiResult.SessionUnavailable
        } else {
            ApiResult.SessionChanged
        }
}