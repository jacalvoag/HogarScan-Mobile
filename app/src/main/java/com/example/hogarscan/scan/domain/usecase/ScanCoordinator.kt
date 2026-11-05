package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.scan.domain.model.AuthorizedScan
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import com.example.hogarscan.scan.domain.model.ScanStoppedException
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.policy.ScanPolicy
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScanCoordinator(
    private val networkRepository: WifiNetworkRepository,
    private val tcpProbe: TcpProbe,
    private val historyRecorder: ScanHistoryRecorder? = null
) {
    private val executionGate = Mutex()

    suspend fun execute(
        request: AuthorizedScan,
        onStarted: () -> Unit = {},
        onResult: (ProbeResult) -> Unit
    ) {
        request.claim()
        if (!executionGate.tryLock()) {
            request.revoke()
            error("Ya hay una revisión en ejecución.")
        }
        var historyRecord: ScanRecord? = null
        var finalStatus = ScanRecordStatus.FAILED
        var failure: Throwable? = null

        try {
            checkAuthorization(request)
            historyRecorder?.let { recorder ->
                withContext(NonCancellable) {
                    historyRecord = recorder.start(request.config)
                }
                // El guardado pudo suspender: comprueba de nuevo la conexión.
                checkAuthorization(request)
            }
            onStarted()

            val targets = request.config.targetAddresses.flatMap { address ->
                request.config.ports.map { port -> address to port }
            }
            val next = AtomicInteger(0)
            val resultMutex = Mutex()

            coroutineScope {
                repeat(minOf(ScanPolicy.MAX_CONCURRENT_PROBES, targets.size)) {
                    launch {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val index = next.getAndIncrement()
                            if (index >= targets.size) break

                            val (address, port) = targets[index]
                            val result = tcpProbe.probe(
                                connectionId = request.connectionId,
                                address = address,
                                port = port
                            )

                            // Conserva un resultado ya recibido aunque se cancele.
                            // Guarda y publica datos; no abre conexiones.
                            withContext(NonCancellable) {
                                resultMutex.withLock {
                                    val record = historyRecord
                                    if (record != null) {
                                        historyRecorder?.append(record, result)
                                    }
                                    onResult(result)
                                }
                            }
                        }
                    }
                }
            }

            checkAuthorization(request)
            finalStatus = ScanRecordStatus.COMPLETED
        } catch (exception: Throwable) {
            failure = exception
            finalStatus = when (exception) {
                is ScanStoppedException -> exception.status
                is CancellationException,
                is ScanConnectionChangedException -> ScanRecordStatus.INTERRUPTED
                else -> ScanRecordStatus.FAILED
            }
            throw exception
        } finally {
            try {
                val record = historyRecord
                if (record != null) {
                    withContext(NonCancellable) {
                        try {
                            historyRecorder?.finish(record, finalStatus)
                        } catch (exception: Exception) {
                            val original = failure
                            if (original != null) {
                                original.addSuppressed(exception)
                            } else {
                                throw exception
                            }
                        }
                    }
                }
            } finally {
                request.revoke()
                executionGate.unlock()
            }
        }
    }

    private suspend fun checkAuthorization(request: AuthorizedScan) {
        val network = networkRepository.getCurrentNetwork()
        currentCoroutineContext().ensureActive()

        if (network !is WifiNetworkResult.Available ||
            network.connectionId != request.connectionId ||
            !request.validate(network)
        ) {
            throw ScanConnectionChangedException()
        }
    }
}