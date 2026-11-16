package com.example.hogarscan.auth.data.http

import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult

fun interface SessionApiClient {
    suspend fun execute(
        request: ApiRequest,
        expectedSession: SessionSnapshot
    ): ApiResult
}