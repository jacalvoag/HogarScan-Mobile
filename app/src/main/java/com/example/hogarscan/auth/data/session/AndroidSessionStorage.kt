package com.example.hogarscan.auth.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidSessionStorage internal constructor(
    context: Context,
    private val keyAlias: String,
    directoryName: String
) : SessionStorage {

    constructor(context: Context) : this(
        context,
        "hogarscan.session.v1",
        "hogarscan-session"
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val file by lazy {
        AtomicFile(
            File(
                appContext.noBackupFilesDir,
                "$directoryName/credentials.bin"
            )
        )
    }

    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    override suspend fun read(): SessionCredentials? = guarded {
        val input = try {
            file.openRead()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() ||
                File(file.baseFile.path + ".bak").exists()
            ) {
                throw error
            }

            return@guarded null
        }

        val record = input.use(::readBounded)

        require(record.size >= MAGIC.size + IV_BYTES + TAG_BYTES)
        require(
            record.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        )

        val key = keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw GeneralSecurityException(
                "La clave de sesión no está disponible."
            )

        val iv = record.copyOfRange(
            MAGIC.size,
            MAGIC.size + IV_BYTES
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(MAGIC)

        val plaintext = cipher.doFinal(
            record,
            MAGIC.size + IV_BYTES,
            record.size - MAGIC.size - IV_BYTES
        )

        try {
            SessionCredentialsCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun write(credentials: SessionCredentials) {
        guarded {
            val plaintext = SessionCredentialsCodec.encode(credentials)

            val record = try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                cipher.updateAAD(MAGIC)

                if (cipher.iv.size != IV_BYTES) {
                    throw GeneralSecurityException(
                        "El IV de sesión no tiene el tamaño esperado."
                    )
                }

                MAGIC + cipher.iv + cipher.doFinal(plaintext)
            } finally {
                plaintext.fill(0)
            }

            val output = file.startWrite()

            try {
                output.write(record)
                output.fd.sync()
                file.finishWrite(output)
            } catch (error: IOException) {
                file.failWrite(output)
                throw error
            }
        }
    }

    override suspend fun clear() {
        guarded {
            keyStore.deleteEntry(keyAlias)
            file.delete()

            val remains = listOf("", ".bak", ".new").any {
                File(file.baseFile.path + it).exists()
            }

            if (remains) {
                throw IOException(
                    "No se pudo eliminar el archivo de sesión."
                )
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(keyAlias, null)

        if (existing != null) {
            return existing as? SecretKey
                ?: throw GeneralSecurityException(
                    "La clave de sesión no es compatible."
                )
        }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or
                            KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                    )
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1_024)

        while (true) {
            val count = input.read(buffer)

            if (count == -1) break

            if (output.size() + count > MAX_FILE_BYTES) {
                throw IOException(
                    "El archivo de sesión supera el límite local."
                )
            }

            output.write(buffer, 0, count)
        }

        return output.toByteArray()
    }

    private suspend fun <T> guarded(
        block: () -> T
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
            } catch (error: IOException) {
                throw SessionStorageException(error)
            } catch (error: GeneralSecurityException) {
                throw SessionStorageException(error)
            } catch (error: ProviderException) {
                throw SessionStorageException(error)
            } catch (error: IllegalArgumentException) {
                throw SessionStorageException(error)
            }
        }
    }

    companion object {
        private val MAGIC = "HSC1".toByteArray(Charsets.US_ASCII)
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MAX_FILE_BYTES = 8_192
    }
}