package com.example.hogarscan.auth.data.http

import com.example.hogarscan.shared.data.http.ApiMethod
import com.example.hogarscan.shared.data.http.ApiRequest
import com.example.hogarscan.shared.data.http.ApiResult
import com.example.hogarscan.shared.data.http.ApiTransport
import com.squareup.moshi.JsonReader
import java.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAuthDataSourceTest {
    private val transport = FakeTransport()
    private val source = HttpAuthDataSource(transport)

    @Test
    fun `registro envia solo email y password conservando su contenido`() = runTest {
        transport.response = ApiResult.Success(201, userJson())
        val password = "  Una clave con \"comillas\", \\ y salto\n🔐  "

        source.register("Persona@Example.com", password)

        val request = transport.requests.single()
        val body = JsonReader.of(
            Buffer().writeUtf8(requireNotNull(request.bodyJson))
        ).use {
            it.readJsonValue() as Map<*, *>
        }

        assertEquals(ApiMethod.POST, request.method)
        assertEquals("/auth/register", request.path)
        assertEquals(setOf("email", "password"), body.keys)
        assertEquals("Persona@Example.com", body["email"])
        assertEquals(password, body["password"])
        assertNull(request.bearerToken)
    }

    @Test
    fun `registro devuelve la cuenta sin efectuar otro login`() = runTest {
        transport.response = ApiResult.Success(201, userJson())

        val result = source.register(EMAIL, PASSWORD)

        assertTrue(result is AuthRemoteResult.Success)
        val user = (result as AuthRemoteResult.Success).value

        assertEquals(ID, user.id)
        assertEquals(EMAIL, user.email)
        assertEquals(
            Instant.parse(DATE).toEpochMilli(),
            user.createdAtMillis
        )
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `login interpreta credenciales y vigencia entregadas por el servidor`() = runTest {
        transport.response = ApiResult.Success(200, loginJson())

        val result = source.login(EMAIL, PASSWORD)

        assertTrue(result is AuthRemoteResult.Success)
        val credentials = (result as AuthRemoteResult.Success).value

        assertEquals(ID, credentials.userId)
        assertEquals(EMAIL, credentials.email)
        assertEquals(TOKEN, credentials.accessToken)
        assertEquals(
            Instant.parse(EXPIRATION).toEpochMilli(),
            credentials.expiresAtMillis
        )
        assertEquals(0, credentials.summaryGeneration)
        assertEquals("/auth/login", transport.requests.single().path)
        assertNull(transport.requests.single().bearerToken)
    }

    @Test
    fun `rechaza otro tipo de token y tokens con formato incorrecto`() = runTest {
        listOf(
            loginJson().replace("\"Bearer\"", "\"Basic\""),
            loginJson().replace("\"Bearer\"", "\"bearer\""),
            loginJson().replace(TOKEN, "short-token"),
            loginJson().replace(TOKEN, "!".repeat(43))
        ).forEach { body ->
            transport.response = ApiResult.Success(200, body)
            assertInvalid(source.login(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `rechaza generaciones invalidas sin truncarlas`() = runTest {
        listOf(
            "-1",
            "2147483648",
            "0.00000000000000000001",
            "\"0\"",
            "null"
        ).forEach { value ->
            transport.response = ApiResult.Success(
                200,
                loginJson().replace(
                    "\"summary_generation\":0",
                    "\"summary_generation\":$value"
                )
            )

            assertInvalid(source.login(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `rechaza campos obligatorios ausentes o de otro tipo`() = runTest {
        listOf(
            loginJson().replace("\"token_type\":\"Bearer\",", ""),
            loginJson().replace("\"access_token\":\"$TOKEN\",", ""),
            loginJson().replace(
                "\"user\":${userJson()}",
                "\"user\":null"
            ),
            loginJson().replace(
                "\"created_at\":\"$DATE\"",
                "\"other\":true"
            ),
            loginJson().replace(
                "\"email\":\"$EMAIL\"",
                "\"email\":12"
            ),
            loginJson().replace(ID, "1-1-1-1-1")
        ).forEach { body ->
            transport.response = ApiResult.Success(200, body)
            assertInvalid(source.login(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `rechaza duplicados y contenido posterior al documento`() = runTest {
        listOf(
            loginJson().replace(
                "\"summary_generation\":0",
                "\"summary_generation\":0,\"summary_generation\":1"
            ),
            loginJson().replace(
                "\"id\":\"$ID\"",
                "\"id\":\"$ID\",\"id\":\"$ID\""
            ),
            loginJson() + " {}"
        ).forEach { body ->
            transport.response = ApiResult.Success(200, body)
            assertInvalid(source.login(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `tolera campos adicionales sin cambiar los datos requeridos`() = runTest {
        transport.response = ApiResult.Success(
            200,
            loginJson().dropLast(1) + ",\"future\":true}"
        )

        val result = source.login(EMAIL, PASSWORD)

        assertTrue(result is AuthRemoteResult.Success)
        assertEquals(
            TOKEN,
            (result as AuthRemoteResult.Success).value.accessToken
        )
    }

    @Test
    fun `propaga los fallos HTTP y de transporte sin reinterpretarlos`() = runTest {
        listOf(
            ApiResult.HttpFailure(401, "{}", null),
            ApiResult.HttpFailure(409, "{}", null),
            ApiResult.HttpFailure(429, "{}", 60),
            ApiResult.NetworkFailure,
            ApiResult.TimedOut,
            ApiResult.TlsFailure
        ).forEach { failure ->
            transport.response = failure

            val result = source.login(EMAIL, PASSWORD)

            assertTrue(result is AuthRemoteResult.Failure)
            assertSame(
                failure,
                (result as AuthRemoteResult.Failure).error
            )
        }
    }

    @Test
    fun `un estado exitoso inesperado no se considera autenticacion correcta`() = runTest {
        transport.response = ApiResult.Success(200, userJson())
        assertInvalid(source.register(EMAIL, PASSWORD))

        transport.response = ApiResult.Success(201, loginJson())
        assertInvalid(source.login(EMAIL, PASSWORD))
    }

    @Test
    fun `rechaza un cuerpo exitoso ausente o con JSON invalido`() = runTest {
        listOf(
            null,
            "",
            "{}",
            "[]",
            "null",
            "{invalid}"
        ).forEach { body ->
            transport.response = ApiResult.Success(200, body)
            assertInvalid(source.login(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `interpreta las precisiones UTC admitidas por OpenAPI`() = runTest {
        val base = "2026-11-16T01:02:03"
        val epoch = Instant.parse(base + "Z").toEpochMilli()

        listOf(
            "Z" to 0L,
            ".1Z" to 100L,
            ".12Z" to 120L,
            ".123Z" to 123L
        ).forEach { (suffix, millis) ->
            transport.response = ApiResult.Success(
                201,
                userJson(base + suffix)
            )

            val result = source.register(
                EMAIL,
                PASSWORD
            ) as AuthRemoteResult.Success

            assertEquals(
                epoch + millis,
                result.value.createdAtMillis
            )
        }
    }

    @Test
    fun `rechaza fechas imposibles y formatos fuera del contrato`() = runTest {
        listOf(
            "2026-02-29T01:02:03Z",
            "2026-11-16T24:00:00Z",
            "2026-11-16T23:59:60Z",
            "2026-11-16T01:02:03.1234Z",
            "2026-11-16T01:02:03+00:00",
            "2026-11-16T01:02:03"
        ).forEach { date ->
            transport.response = ApiResult.Success(
                201,
                userJson(date)
            )

            assertInvalid(source.register(EMAIL, PASSWORD))
        }
    }

    @Test
    fun `Unicode mal formado no se sustituye silenciosamente en la clave`() = runTest {
        val failure = runCatching {
            source.login(EMAIL, "password\uD800")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `cancelar no se transforma en un error de autenticacion`() = runTest {
        transport.handler = { awaitCancellation() }
        var returned = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            source.login(EMAIL, PASSWORD)
            returned = true
        }

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    private fun assertInvalid(result: AuthRemoteResult<*>) {
        assertTrue(result is AuthRemoteResult.Failure)
        assertSame(
            ApiResult.InvalidResponse,
            (result as AuthRemoteResult.Failure).error
        )
    }

    private fun userJson(date: String = DATE) =
        """{"id":"$ID","email":"$EMAIL","created_at":"$date"}"""

    private fun loginJson() =
        """{"user":${userJson()},"access_token":"$TOKEN","token_type":"Bearer","expires_at":"$EXPIRATION","summary_generation":0}"""

    private class FakeTransport : ApiTransport {
        val requests = mutableListOf<ApiRequest>()
        var response: ApiResult = ApiResult.InvalidResponse
        var handler: (suspend () -> ApiResult)? = null

        override suspend fun execute(request: ApiRequest): ApiResult {
            requests += request
            return handler?.invoke() ?: response
        }
    }

    companion object {
        private const val ID =
            "00000000-0000-4000-8000-000000000001"
        private const val EMAIL = "fixture@example.com"
        private const val PASSWORD = "UnaFraseLargaDePrueba2026"
        private const val DATE = "2026-11-16T01:02:03.123Z"
        private const val EXPIRATION = "2026-11-23T01:02:03Z"
        private val TOKEN = "fixture_a".repeat(5).take(43)
    }
}