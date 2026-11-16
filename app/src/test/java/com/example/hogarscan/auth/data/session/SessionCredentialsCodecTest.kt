package com.example.hogarscan.auth.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionCredentialsCodecTest {
    private val credentials = SessionCredentials(
        userId = "account-a",
        email = "josé@example.com",
        expiresAtMillis = 1_800_000_000_000L,
        summaryGeneration = 2,
        accessToken = "fixture_a".repeat(5).take(43)
    )

    @Test
    fun `conserva los campos sin perder caracteres ni precision`() {
        val restored = SessionCredentialsCodec.decode(
            SessionCredentialsCodec.encode(credentials)
        )

        assertEquals(credentials.userId, restored.userId)
        assertEquals(credentials.email, restored.email)
        assertEquals(
            credentials.expiresAtMillis,
            restored.expiresAtMillis
        )
        assertEquals(
            credentials.summaryGeneration,
            restored.summaryGeneration
        )
        assertEquals(credentials.accessToken, restored.accessToken)
    }

    @Test
    fun `rechaza registros truncados o con datos adicionales`() {
        val encoded = SessionCredentialsCodec.encode(credentials)

        listOf(
            byteArrayOf(),
            encoded.copyOf(encoded.size - 1),
            encoded + byteArrayOf(0)
        ).forEach { invalid ->
            assertNotNull(
                runCatching {
                    SessionCredentialsCodec.decode(invalid)
                }.exceptionOrNull()
            )
        }
    }

    @Test
    fun `limita el tamano del registro local`() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCredentialsCodec.decode(ByteArray(4_097))
        }

        assertThrows(IllegalArgumentException::class.java) {
            SessionCredentialsCodec.encode(
                SessionCredentials(
                    userId = "x".repeat(5_000),
                    email = credentials.email,
                    expiresAtMillis = credentials.expiresAtMillis,
                    summaryGeneration = 0,
                    accessToken = credentials.accessToken
                )
            )
        }
    }
}