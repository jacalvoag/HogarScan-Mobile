package com.example.hogarscan.scan.data.network

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTcpProbeTest {

    private val address = Ipv4Address.parse("192.168.28.40")

    private class FakeSocket(
        private val connectAction: () -> Unit = {},
        private val closeAction: () -> Unit = {}
    ) : Socket() {
        val closed = AtomicBoolean(false)
        var connectCalls = 0
        var timeoutMillis = 0
        var endpoint: InetSocketAddress? = null

        override fun connect(endpoint: SocketAddress, timeout: Int) {
            connectCalls++
            timeoutMillis = timeout
            this.endpoint = endpoint as InetSocketAddress
            connectAction()
        }

        override fun close() {
            closed.set(true)
            closeAction()
        }
    }

    private fun client(
        socket: Socket,
        current: (String) -> Boolean = { true },
        classify: (IOException) -> ProbeOutcome = { ProbeOutcome.ERROR },
        clock: () -> Long = System::nanoTime
    ) = SocketTcpProbe(
        openSocket = { _, _ -> socket },
        connectionIsCurrent = current,
        classifyIOException = classify,
        ioDispatcher = Dispatchers.IO,
        nanoTime = clock
    )

    @Test
    fun `una conexion aceptada usa el destino y el timeout del piloto`() = runBlocking {
        val socket = FakeSocket()
        val probe = client(socket)

        val result = probe.probe("wifi-1:1", address, 443)

        assertEquals(ProbeOutcome.ACCEPTED, result.outcome)
        assertEquals(address, result.address)
        assertEquals(443, result.port)
        assertEquals("192.168.28.40", socket.endpoint?.address?.hostAddress)
        assertEquals(443, socket.endpoint?.port)
        assertEquals(1_000, socket.timeoutMillis)
        assertEquals(1, socket.connectCalls)
        assertTrue(socket.closed.get())
    }

    @Test
    fun `un timeout se registra una sola vez y cierra el socket`() = runBlocking {
        val socket = FakeSocket(connectAction = { throw SocketTimeoutException() })

        val result = client(socket).probe("wifi-1:1", address, 80)

        assertEquals(ProbeOutcome.TIMED_OUT, result.outcome)
        assertEquals(1, socket.connectCalls)
        assertTrue(socket.closed.get())
    }

    @Test
    fun `un rechazo identificado por el adaptador se conserva`() = runBlocking {
        val socket = FakeSocket(connectAction = { throw ConnectException() })
        val probe = client(socket, classify = { ProbeOutcome.REJECTED })

        val result = probe.probe("wifi-1:1", address, 80)

        assertEquals(ProbeOutcome.REJECTED, result.outcome)
        assertTrue(socket.closed.get())
    }

    @Test
    fun `un error de conexion sin rechazo identificado se conserva como error`() =
        runBlocking {
            val socket = FakeSocket(connectAction = { throw ConnectException() })

            val result = client(socket).probe("wifi-1:1", address, 80)

            assertEquals(ProbeOutcome.ERROR, result.outcome)
            assertTrue(socket.closed.get())
        }

    @Test
    fun `un error de permisos cierra el socket`() = runBlocking {
        val socket = FakeSocket(connectAction = { throw SecurityException() })

        val result = client(socket).probe("wifi-1:1", address, 80)

        assertEquals(ProbeOutcome.ERROR, result.outcome)
        assertTrue(socket.closed.get())
    }

    @Test
    fun `un fallo al preparar el socket no realiza un intento de conexion`() =
        runBlocking {
            var preparations = 0
            val probe = SocketTcpProbe(
                openSocket = { _, _ ->
                    preparations++
                    throw IOException("Fallo al preparar el socket")
                },
                connectionIsCurrent = { true },
                classifyIOException = { ProbeOutcome.REJECTED }
            )

            val result = probe.probe("wifi-1:1", address, 80)

            assertEquals(1, preparations)
            assertEquals(ProbeOutcome.ERROR, result.outcome)
        }

    @Test
    fun `rechaza una conexion invalidada antes de conectar`() {
        val socket = FakeSocket()
        val probe = client(socket, current = { false })

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking { probe.probe("wifi-1:1", address, 80) }
        }

        assertEquals(0, socket.connectCalls)
        assertTrue(socket.closed.get())
    }

    @Test
    fun `descarta el resultado si la conexion cambio durante el intento`() {
        val socket = FakeSocket()
        var checks = 0
        val probe = client(socket, current = {
            checks++
            checks == 1
        })

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking { probe.probe("wifi-1:1", address, 80) }
        }

        assertEquals(1, socket.connectCalls)
        assertTrue(socket.closed.get())
    }

    @Test(timeout = 10_000L)
    fun `cancelar cierra un socket bloqueado y no devuelve un error TCP`() = runBlocking {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val socket = FakeSocket(
            connectAction = {
                entered.countDown()
                check(closed.await(5, TimeUnit.SECONDS))
                throw SocketException("Socket cerrado")
            },
            closeAction = { closed.countDown() }
        )
        val probe = client(socket)

        val task = async { probe.probe("wifi-1:1", address, 80) }
        try {
            // Inicia la corrutina antes de esperar la operación bloqueante.
            kotlinx.coroutines.yield()
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            task.cancel()
            withTimeout(3_000L) { task.join() }

            assertTrue(task.isCancelled)
            assertTrue(socket.closed.get())
        } finally {
            closed.countDown()
            task.cancel()
        }
    }

    @Test
    fun `calcula la duracion con el reloj monotono`() = runBlocking {
        val clock = AtomicLong(7_000_000L)
        val socket = FakeSocket(
            connectAction = { clock.addAndGet(23_000_000L) }
        )
        val probe = client(socket, clock = clock::get)

        val result = probe.probe("wifi-1:1", address, 80)

        assertEquals(23L, result.durationMillis)
    }

    @Test
    fun `rechaza un puerto fuera del piloto antes de crear sockets`() {
        var preparations = 0
        val probe = SocketTcpProbe(
            openSocket = { _, _ ->
                preparations++
                FakeSocket()
            },
            connectionIsCurrent = { true },
            classifyIOException = { ProbeOutcome.ERROR }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { probe.probe("wifi-1:1", address, 8080) }
        }
        assertEquals(0, preparations)
    }

    @Test
    fun `rechaza un identificador vacio antes de crear sockets`() {
        var preparations = 0
        val probe = SocketTcpProbe(
            openSocket = { _, _ ->
                preparations++
                FakeSocket()
            },
            connectionIsCurrent = { true },
            classifyIOException = { ProbeOutcome.ERROR }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { probe.probe(" ", address, 80) }
        }
        assertEquals(0, preparations)
    }
}
