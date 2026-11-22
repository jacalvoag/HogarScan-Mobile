package com.example.hogarscan.auth.data.http

import com.example.hogarscan.auth.data.session.SessionCredentials
import com.example.hogarscan.auth.domain.model.AuthUser
import com.example.hogarscan.shared.data.http.UtcTimestamp
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.util.UUID
import okio.Buffer

internal object AuthJsonCodec {

    fun encodeCredentials(
        email: String,
        password: String
    ): String {
        val encoder = Charsets.UTF_8.newEncoder()

        require(
            encoder.canEncode(email) &&
                    encoder.canEncode(password)
        ) {
            "Los datos contienen Unicode no válido."
        }

        val buffer = Buffer()

        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name("email").value(email)
            writer.name("password").value(password)
            writer.endObject()
        }

        return buffer.readUtf8()
    }

    fun decodeUser(json: String): AuthUser =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            val user = readUser(reader)

            require(reader.peek() == JsonReader.Token.END_DOCUMENT)

            user
        }

    fun decodeLogin(json: String): SessionCredentials =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            var user: AuthUser? = null
            var token: String? = null
            var tokenType: String? = null
            var expiresAt: Long? = null
            var generation: Int? = null

            readObject(reader) { name ->
                when (name) {
                    "user" -> user = readUser(reader)
                    "access_token" -> token = readString(reader)
                    "token_type" -> tokenType = readString(reader)

                    "expires_at" -> {
                        expiresAt = UtcTimestamp.parseMillis(
                            readString(reader)
                        )
                    }

                    "summary_generation" -> {
                        require(
                            reader.peek() == JsonReader.Token.NUMBER
                        )

                        generation = reader.nextString()
                            .toBigDecimal()
                            .intValueExact()
                    }

                    else -> reader.skipValue()
                }
            }

            require(reader.peek() == JsonReader.Token.END_DOCUMENT)
            require(tokenType == "Bearer")

            val account = requireNotNull(user)

            SessionCredentials(
                userId = account.id,
                email = account.email,
                expiresAtMillis = requireNotNull(expiresAt),
                summaryGeneration = requireNotNull(generation),
                accessToken = requireNotNull(token)
            )
        }

    private fun readUser(reader: JsonReader): AuthUser {
        var id: String? = null
        var email: String? = null
        var createdAt: Long? = null

        readObject(reader) { name ->
            when (name) {
                "id" -> {
                    val raw = readString(reader)
                    val normalized = UUID.fromString(raw).toString()

                    require(normalized.equals(raw, ignoreCase = true))

                    id = normalized
                }

                "email" -> {
                    email = readString(reader).also {
                        require(
                            it.length in 3..254 &&
                                    it.none { char -> char.isWhitespace() }
                        )
                    }
                }

                "created_at" -> {
                    createdAt = UtcTimestamp.parseMillis(
                        readString(reader)
                    )
                }

                else -> reader.skipValue()
            }
        }

        return AuthUser(
            id = requireNotNull(id),
            email = requireNotNull(email),
            createdAtMillis = requireNotNull(createdAt)
        )
    }

    private fun readString(reader: JsonReader): String {
        require(reader.peek() == JsonReader.Token.STRING)
        return reader.nextString()
    }

    private fun readObject(
        reader: JsonReader,
        field: (String) -> Unit
    ) {
        val names = mutableSetOf<String>()

        reader.beginObject()

        while (reader.hasNext()) {
            val name = reader.nextName()

            require(names.add(name)) {
                "Campo duplicado."
            }

            field(name)
        }

        reader.endObject()
    }
}