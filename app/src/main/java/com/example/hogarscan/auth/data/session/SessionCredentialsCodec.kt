package com.example.hogarscan.auth.data.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object SessionCredentialsCodec {
    private const val MAX_BYTES = 4_096

    fun encode(credentials: SessionCredentials): ByteArray {
        val buffer = ByteArrayOutputStream()

        DataOutputStream(buffer).use { output ->
            output.writeUTF(credentials.userId)
            output.writeUTF(credentials.email)
            output.writeLong(credentials.expiresAtMillis)
            output.writeInt(credentials.summaryGeneration)
            output.writeUTF(credentials.accessToken)
        }

        return buffer.toByteArray().also {
            require(it.size <= MAX_BYTES) {
                "Las credenciales superan el límite local."
            }
        }
    }

    fun decode(bytes: ByteArray): SessionCredentials {
        require(bytes.size <= MAX_BYTES) {
            "El registro supera el límite local."
        }

        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val credentials = SessionCredentials(
                userId = input.readUTF(),
                email = input.readUTF(),
                expiresAtMillis = input.readLong(),
                summaryGeneration = input.readInt(),
                accessToken = input.readUTF()
            )

            require(input.read() == -1) {
                "El registro contiene datos adicionales."
            }

            credentials
        }
    }
}