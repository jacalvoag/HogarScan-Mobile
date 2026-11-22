package com.example.hogarscan.auth.data.http

import com.example.hogarscan.auth.data.session.SessionCredentials
import com.example.hogarscan.auth.domain.model.AuthUser
import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.time.DateTimeException

class HttpAuthDataSource(
    private val transport: ApiTransport
) {

    suspend fun register(
        email: String,
        password: String
    ): AuthRemoteResult<AuthUser> =
        post(
            "/auth/register",
            201,
            email,
            password,
            AuthJsonCodec::decodeUser
        )

    suspend fun login(
        email: String,
        password: String
    ): AuthRemoteResult<SessionCredentials> =
        post(
            "/auth/login",
            200,
            email,
            password,
            AuthJsonCodec::decodeLogin
        )

    suspend fun revoke(accessToken: String): ApiResult =
        transport.execute(
            ApiRequest(
                method = ApiMethod.POST,
                path = "/auth/logout",
                bearerToken = accessToken
            )
        )

    private suspend fun <T> post(
        path: String,
        expectedStatus: Int,
        email: String,
        password: String,
        decode: (String) -> T
    ): AuthRemoteResult<T> {
        val result = transport.execute(
            ApiRequest(
                method = ApiMethod.POST,
                path = path,
                bodyJson = AuthJsonCodec.encodeCredentials(
                    email,
                    password
                )
            )
        )

        if (result !is ApiResult.Success) {
            return AuthRemoteResult.Failure(result)
        }

        val json = result.bodyJson

        if (result.statusCode != expectedStatus || json == null) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        }

        val value = try {
            decode(json)
        } catch (_: IOException) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        } catch (_: JsonDataException) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        } catch (_: IllegalArgumentException) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        } catch (_: DateTimeException) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        } catch (_: ArithmeticException) {
            return AuthRemoteResult.Failure(ApiResult.InvalidResponse)
        }

        return AuthRemoteResult.Success(value)
    }
}