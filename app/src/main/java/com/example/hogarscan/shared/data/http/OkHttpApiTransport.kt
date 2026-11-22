package com.example.hogarscan.shared.data.http

import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.CharacterCodingException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OkHttpApiTransport(
    private val endpoint: ApiEndpoint,
    private val callFactory: Call.Factory
) : ApiTransport {

    override suspend fun execute(request: ApiRequest): ApiResult {
        val builder = Request.Builder()
            .url(endpoint.urlFor(request.path, request.queryParameters))
            .header("Accept", "application/json")

        request.bearerToken?.let {
            builder.header("Authorization", "Bearer $it")
        }

        val body = request.bodyJson?.toRequestBody(JSON_MEDIA_TYPE)

        when (request.method) {
            ApiMethod.GET -> builder.get()
            ApiMethod.POST -> builder.post(body ?: "".toRequestBody(null))
            ApiMethod.DELETE -> builder.method("DELETE", null)
        }

        return suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(e.toApiResult())
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { readResponse(it) }
                    } catch (_: InvalidApiResponseException) {
                        ApiResult.InvalidResponse
                    } catch (_: CharacterCodingException) {
                        ApiResult.InvalidResponse
                    } catch (error: IOException) {
                        error.toApiResult()
                    }

                    continuation.resume(result)
                }
            })
        }
    }

    private fun readResponse(response: Response): ApiResult {
        if (response.code == 204) {
            return ApiResult.Success(response.code, null)
        }

        val body = response.body
        val contentType = body.contentType()
        val isJson = contentType?.type == "application" &&
                contentType.subtype == "json" &&
                contentType.charset(Charsets.UTF_8) == Charsets.UTF_8

        val bodyJson = if (isJson) {
            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                throw InvalidApiResponseException()
            }

            val source = body.source()
            source.request(MAX_RESPONSE_BYTES + 1)

            if (source.buffer.size > MAX_RESPONSE_BYTES) {
                throw InvalidApiResponseException()
            }

            source.readByteArray()
                .decodeToString(throwOnInvalidSequence = true)
                .takeIf { it.isNotBlank() }
        } else {
            null
        }

        if (response.isSuccessful) {
            return if (bodyJson != null) {
                ApiResult.Success(response.code, bodyJson)
            } else {
                ApiResult.InvalidResponse
            }
        }

        return ApiResult.HttpFailure(
            statusCode = response.code,
            bodyJson = bodyJson,
            retryAfterSeconds = response.header("Retry-After")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it >= 0 }
        )
    }

    private fun IOException.toApiResult(): ApiResult = when (this) {
        is SSLException -> ApiResult.TlsFailure
        is InterruptedIOException -> ApiResult.TimedOut
        else -> ApiResult.NetworkFailure
    }

    private class InvalidApiResponseException : IOException()

    companion object {
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()

        private const val MAX_RESPONSE_BYTES = 1_048_576L
    }
}