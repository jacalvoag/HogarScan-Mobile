package com.example.hogarscan.auth.data.session

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSessionStorageTest {
    private lateinit var context: Context
    private lateinit var storage: AndroidSessionStorage
    private lateinit var alias: String
    private lateinit var directoryName: String

    private val credentials = SessionCredentials(
        userId = "account-a",
        email = "fixture@example.com",
        expiresAtMillis = 1_800_000_000_000L,
        summaryGeneration = 2,
        accessToken = "fixture_a".repeat(5).take(43)
    )

    private val recordFile: File
        get() = File(
            context.noBackupFilesDir,
            "$directoryName/credentials.bin"
        )

    @Before
    fun setUp() {
        context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val id = UUID.randomUUID().toString()
        alias = "hogarscan.test.$id"
        directoryName = "hogarscan-test-$id"

        storage = AndroidSessionStorage(
            context,
            alias,
            directoryName
        )
    }

    @After
    fun tearDown() = runBlocking {
        storage.clear()

        File(
            context.noBackupFilesDir,
            directoryName
        ).deleteRecursively()

        Unit
    }

    @Test
    fun un_archivo_ausente_no_restaura_credenciales() = runBlocking {
        assertNull(storage.read())
    }

    @Test
    fun otra_instancia_recupera_las_credenciales_cifradas() = runBlocking {
        storage.write(credentials)

        val reopened = AndroidSessionStorage(
            context,
            alias,
            directoryName
        )
        val restored = requireNotNull(reopened.read())

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
    fun el_archivo_no_contiene_el_token_y_cada_cifrado_utiliza_otro_iv() = runBlocking {
        storage.write(credentials)
        val first = recordFile.readBytes()

        storage.write(credentials)
        val second = recordFile.readBytes()

        assertFalse(first.contentEquals(second))
        assertFalse(
            second.toString(Charsets.UTF_8)
                .contains(credentials.accessToken)
        )
        assertFalse(
            second.toString(Charsets.UTF_8)
                .contains(credentials.email)
        )
    }

    @Test
    fun alterar_el_archivo_provoca_un_error_controlado() = runBlocking {
        storage.write(credentials)

        val bytes = recordFile.readBytes()
        bytes[bytes.lastIndex] =
            (bytes.last().toInt() xor 1).toByte()
        recordFile.writeBytes(bytes)

        assertTrue(
            runCatching { storage.read() }.exceptionOrNull()
                    is SessionStorageException
        )
    }

    @Test
    fun leer_sin_clave_no_genera_otra_automaticamente() = runBlocking {
        storage.write(credentials)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        keyStore.deleteEntry(alias)

        assertTrue(
            runCatching { storage.read() }.exceptionOrNull()
                    is SessionStorageException
        )
        assertFalse(keyStore.containsAlias(alias))
    }

    @Test
    fun borrar_elimina_archivo_y_clave_y_permite_guardar_otra_vez() = runBlocking {
        storage.write(credentials)
        storage.clear()

        assertNull(storage.read())
        assertFalse(recordFile.exists())

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        assertFalse(keyStore.containsAlias(alias))

        storage.write(credentials)

        assertEquals(
            credentials.accessToken,
            storage.read()?.accessToken
        )
    }

    @Test
    fun rechazar_un_registro_grande_conserva_el_anterior() = runBlocking {
        storage.write(credentials)

        val oversized = SessionCredentials(
            userId = "x".repeat(5_000),
            email = credentials.email,
            expiresAtMillis = credentials.expiresAtMillis,
            summaryGeneration = 0,
            accessToken = credentials.accessToken
        )

        assertTrue(
            runCatching {
                storage.write(oversized)
            }.exceptionOrNull() is SessionStorageException
        )
        assertEquals(credentials.userId, storage.read()?.userId)
        assertEquals(
            credentials.accessToken,
            storage.read()?.accessToken
        )
    }
}