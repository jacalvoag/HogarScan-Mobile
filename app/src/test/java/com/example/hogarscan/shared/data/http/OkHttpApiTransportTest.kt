package com.example.hogarscan.shared.data.http

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpApiTransportTest {
    private val endpoint = ApiEndpoint.parse("https://api.example.test/")
    private val factory = FakeCallFactory()
    private val transport = OkHttpApiTransport(endpoint, factory)

    @Test
    fun `conserva el JSON UTF8 de una respuesta exitosa`() = runTest {
        factory.body = "{\"message\":\"Revisión disponible\"}"

        val result = transport.execute(
            ApiRequest(ApiMethod.GET, "/rules")
        )

        assertTrue(result is ApiResult.Success)
        result as ApiResult.Success
        assertEquals(200, result.statusCode)
        assertEquals(factory.body, result.bodyJson)
    }

    @Test
    fun `GET conserva ruta parametros y aceptacion JSON`() = runTest {
        transport.execute(
            ApiRequest(
                ApiMethod.GET,
                "/rules",
                queryParameters = mapOf("value" to "a&b")
            )
        )

        val request = factory.lastCall.request()

        assertEquals("GET", request.method)
        assertEquals("/rules", request.url.encodedPath)
        assertEquals("a&b", request.url.queryParameter("value"))
        assertEquals("application/json", request.header("Accept"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun `POST conserva JSON y la credencial proporcionada por datos`() = runTest {
        val json = "{\"label\":\"prueba\"}"

        transport.execute(
            ApiRequest(
                ApiMethod.POST,
                "/evaluations",
                bodyJson = json,
                bearerToken = "fixture-token"
            )
        )

        val request = factory.lastCall.request()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)

        assertEquals("POST", request.method)
        assertEquals(json, buffer.readUtf8())
        assertEquals(
            "Bearer fixture-token",
            request.header("Authorization")
        )
        assertEquals(
            "application/json; charset=utf-8",
            request.body!!.contentType().toString()
        )
    }

    @Test
    fun `DELETE envia la generacion sin cuerpo`() = runTest {
        transport.execute(
            ApiRequest(
                ApiMethod.DELETE,
                "/me/scans",
                queryParameters = mapOf("summary_generation" to "2")
            )
        )

        val request = factory.lastCall.request()

        assertEquals("DELETE", request.method)
        assertNull(request.body)
        assertEquals(
            "2",
            request.url.queryParameter("summary_generation")
        )
    }

    @Test
    fun `POST sin datos no inventa un documento JSON`() = runTest {
        transport.execute(
            ApiRequest(ApiMethod.POST, "/auth/logout")
        )

        val request = factory.lastCall.request()

        assertEquals(0L, request.body!!.contentLength())
        assertNull(request.body!!.contentType())
    }

    @Test
    fun `204 representa exito sin JSON`() = runTest {
        factory.status = 204
        factory.body = ""

        val result = transport.execute(
            ApiRequest(ApiMethod.POST, "/auth/logout")
        )

        assertTrue(result is ApiResult.Success)
        result as ApiResult.Success
        assertEquals(204, result.statusCode)
        assertNull(result.bodyJson)
    }

    @Test
    fun `conserva error HTTP JSON y espera indicada por servidor`() = runTest {
        factory.status = 429
        factory.body = "{\"code\":\"RATE_LIMITED\",\"message\":\"Espera.\"}"
        factory.retryAfter = "60"

        val result = transport.execute(
            ApiRequest(ApiMethod.GET, "/rules")
        )

        assertTrue(result is ApiResult.HttpFailure)
        result as ApiResult.HttpFailure
        assertEquals(429, result.statusCode)
        assertEquals(factory.body, result.bodyJson)
        assertEquals(60L, result.retryAfterSeconds)
    }

    @Test
    fun `un error HTML conserva el estado sin exponer HTML como JSON`() = runTest {
        factory.status = 502
        factory.contentType = "text/html"
        factory.body = "<html>Error</html>"

        val result = transport.execute(
            ApiRequest(ApiMethod.GET, "/rules")
        )

        assertTrue(result is ApiResult.HttpFailure)
        result as ApiResult.HttpFailure
        assertEquals(502, result.statusCode)
        assertNull(result.bodyJson)
    }

    @Test
    fun `un exito HTML no se acepta como respuesta de API`() = runTest {
        factory.contentType = "text/html"

        assertSame(
            ApiResult.InvalidResponse,
            transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
        )
    }

    @Test
    fun `rechaza una respuesta mayor al limite`() = runTest {
        factory.body = "x".repeat(1_048_577)

        assertSame(
            ApiResult.InvalidResponse,
            transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
        )
    }

    @Test
    fun `aplica el limite aunque no exista Content Length`() = runTest {
        factory.unknownLength = true
        factory.body = "x".repeat(1_048_577)

        assertSame(
            ApiResult.InvalidResponse,
            transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
        )
    }

    @Test
    fun `un exito vacio distinto de 204 es una respuesta invalida`() = runTest {
        factory.body = ""

        assertSame(
            ApiResult.InvalidResponse,
            transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
        )
    }

    @Test
    fun `distingue timeout TLS y fallo de conexion`() = runTest {
        listOf(
            SocketTimeoutException() to ApiResult.TimedOut,
            SSLHandshakeException("fixture") to ApiResult.TlsFailure,
            IOException("fixture") to ApiResult.NetworkFailure
        ).forEach { (error, expected) ->
            factory.error = error

            assertSame(
                expected,
                transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
            )
        }
    }

    @Test
    fun `cancelar la tarea cancela la llamada sin devolver error de red`() = runTest {
        factory.hold = true
        var returned = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.execute(ApiRequest(ApiMethod.GET, "/rules"))
            returned = true
        }

        job.cancelAndJoin()

        factory.lastCall.callback.onFailure(
            factory.lastCall,
            IOException("cancelled")
        )

        assertTrue(job.isCancelled)
        assertTrue(factory.lastCall.isCanceled())
        assertFalse(returned)
    }

    private class FakeCallFactory : Call.Factory {
        private val delegateFactory = OkHttpClient()

        var status = 200
        var body = "{}"
        var contentType = "application/json"
        var retryAfter: String? = null
        var error: IOException? = null
        var hold = false
        var unknownLength = false

        lateinit var lastCall: FakeCall

        override fun newCall(request: Request): Call =
            FakeCall(delegateFactory.newCall(request)) { call, callback ->
                if (!hold) {
                    val failure = error

                    if (failure != null) {
                        callback.onFailure(call, failure)
                    } else {
                        val responseBody = if (unknownLength) {
                            object : okhttp3.ResponseBody() {
                                private val buffer = Buffer().writeUtf8(body)

                                override fun contentType() =
                                    this@FakeCallFactory.contentType.toMediaType()

                                override fun contentLength(): Long = -1

                                override fun source(): okio.BufferedSource =
                                    buffer
                            }
                        } else {
                            body.toResponseBody(contentType.toMediaType())
                        }

                        val response = Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message("Fixture")
                            .body(responseBody)

                        retryAfter?.let {
                            response.header("Retry-After", it)
                        }

                        callback.onResponse(call, response.build())
                    }
                }
            }.also { lastCall = it }
    }

    private class FakeCall(
        private val delegate: Call,
        private val action: (FakeCall, Callback) -> Unit
    ) : Call by delegate {
        private var cancelled = false
        private var executed = false

        lateinit var callback: Callback

        override fun execute(): Response =
            error("La prueba utiliza llamadas asincronas.")

        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
            action(this, responseCallback)
        }

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = cancelled

        override fun timeout(): Timeout = Timeout()

        override fun clone(): Call =
            FakeCall(delegate.clone(), action)
    }
}