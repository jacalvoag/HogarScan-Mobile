package com.example.hogarscan.scan.data.network

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import com.example.hogarscan.scan.domain.policy.ScanPolicy
import com.example.hogarscan.scan.domain.repository.TcpProbe
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class SocketTcpProbe(
    private val openSocket: (String, Ipv4Address) -> Socket,
    private val connectionIsCurrent: (String) -> Boolean,
    private val classifyIOException: (IOException) -> ProbeOutcome,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime
) : TcpProbe {

    override suspend fun probe(
        connectionId: String,
        address: Ipv4Address,
        port: Int
    ): ProbeResult {
        require(connectionId.isNotBlank()) {
            "La conexión debe tener un identificador."
        }
        require(port in ScanPolicy.allowedPorts) {
            "El puerto no pertenece a los permitidos."
        }

        return withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            val startedAt = nanoTime()

            val socket = try {
                openSocket(connectionId, address)
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                checkConnection(connectionId)
                return@withContext result(address, port, ProbeOutcome.ERROR, startedAt)
            } catch (_: SecurityException) {
                currentCoroutineContext().ensureActive()
                checkConnection(connectionId)
                return@withContext result(address, port, ProbeOutcome.ERROR, startedAt)
            }

            try {
                suspendCancellableCoroutine<ProbeResult> { continuation ->
                    continuation.invokeOnCancellation {
                        closeQuietly(socket)
                    }
                    if (!continuation.isActive) {
                        return@suspendCancellableCoroutine
                    }

                    checkConnection(connectionId)

                    val outcome = try {
                        socket.connect(
                            endpoint(address, port),
                            ScanPolicy.CONNECT_TIMEOUT_MILLIS
                        )
                        ProbeOutcome.ACCEPTED
                    } catch (_: SocketTimeoutException) {
                        ProbeOutcome.TIMED_OUT
                    } catch (exception: IOException) {
                        classifyIOException(exception)
                    } catch (_: SecurityException) {
                        ProbeOutcome.ERROR
                    } finally {
                        closeQuietly(socket)
                    }

                    if (continuation.isActive) {
                        checkConnection(connectionId)
                        continuation.resume(
                            result(address, port, outcome, startedAt)
                        )
                    }
                }
            } finally {
                closeQuietly(socket)
            }
        }
    }

    private fun checkConnection(connectionId: String) {
        if (!connectionIsCurrent(connectionId)) {
            throw ScanConnectionChangedException()
        }
    }

    private fun endpoint(address: Ipv4Address, port: Int): InetSocketAddress {
        val octets = byteArrayOf(
            (address.value shr 24).toByte(),
            (address.value shr 16).toByte(),
            (address.value shr 8).toByte(),
            address.value.toByte()
        )
        return InetSocketAddress(InetAddress.getByAddress(octets), port)
    }

    private fun result(
        address: Ipv4Address,
        port: Int,
        outcome: ProbeOutcome,
        startedAt: Long
    ) = ProbeResult(
        address = address,
        port = port,
        outcome = outcome,
        durationMillis = (nanoTime() - startedAt) / 1_000_000L
    )

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
            // El cierre no debe sustituir el resultado ni la cancelación.
        }
    }
}
