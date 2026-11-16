package com.example.hogarscan.auth.data.http

import com.example.hogarscan.shared.data.http.ApiResult

sealed interface AuthRemoteResult<out T> {
    class Success<T>(val value: T) : AuthRemoteResult<T>

    class Failure(
        val error: ApiResult
    ) : AuthRemoteResult<Nothing>
}