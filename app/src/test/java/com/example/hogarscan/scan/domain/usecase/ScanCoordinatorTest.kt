package com.example.hogarscan.scan.domain.usecase

import com.example.hogarscan.scan.domain.model.AuthorizedScan
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanAuthorization
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanConnectionChangedException
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCoordinatorTest {

    private val network = WifiNetworkResult.Available(
        NetworkScope(Ipv4Address.parse("192.168.28.43"), 24),
        "wifi-1:1"
    )

    private fun config(
        end: String = "192.168.28.60",
        ports: Set<Int> = setOf(80, 443)
    ) = ScanConfig(
        network.scope,
        Ipv4Address.parse("192.168.28.1"),
        Ipv4Address.parse(end),
        ports
    )

    private fun request(config: ScanConfig = config()) = AuthorizedScan(
        config,
        network.connectionId,
        ScanAuthorization.confirm(config, network)
    )

    private class FakeRepository(
        private val read: (Int) -> WifiNetworkResult
    ) : WifiNetworkRepository {
        var reads = 0

        override suspend fun getCurrentNetwork(): WifiNetworkResult {
            reads++
            return read(reads)
        }

        override fun observeNetworkChanges(): Flow<Unit> = emptyFlow()
    }

    private fun probe(
        action: suspend (Ipv4Address, Int) -> ProbeResult
    ) = object : TcpProbe {
        override suspend fun probe(
            connectionId: String,
            address: Ipv4Address,
            port: Int
        ): ProbeResult {
            assertEquals(network.connectionId, connectionId)
            return action(address, port)
        }
    }

    private fun result(address: Ipv4Address, port: Int) =
        ProbeResult(address, port, ProbeOutcome.TIMED_OUT, 10L)

    @Test(timeout = 10_000L)
    fun `revisa cada objetivo una vez con un maximo de veinte intentos activos`() =
        runBlocking {
            val repository = FakeRepository { network }
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val calls = Collections.synchronizedList(
                mutableListOf<Pair<Ipv4Address, Int>>()
            )
            val client = probe { address, port ->
                calls.add(address to port)
                val count = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, count) }
                try {
                    delay(10)
                    result(address, port)
                } finally {
                    active.decrementAndGet()
                }
            }
            val config = config()
            val results = mutableListOf<ProbeResult>()

            ScanCoordinator(repository, client).execute(request(config)) {
                results.add(it)
            }

            val expected = config.targetAddresses.flatMap { address ->
                config.ports.map { address to it }
            }.toSet()
            assertEquals(118, results.size)
            assertEquals(expected, calls.toSet())
            assertEquals(expected.size, calls.size)
            assertEquals(20, peak.get())
            assertEquals(0, active.get())
            assertEquals(2, repository.reads)
            assertFalse(results.any { it.address == network.scope.localAddress })
        }

    @Test
    fun `no inicia intentos cuando la conexion ya cambio`() {
        var calls = 0
        val repository = FakeRepository { network.copy(connectionId = "wifi-2:1") }
        val client = probe { address, port ->
            calls++
            result(address, port)
        }

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking {
                ScanCoordinator(repository, client).execute(request()) {}
            }
        }
        assertEquals(0, calls)
    }

    @Test
    fun `no inicia intentos con una configuracion diferente de la autorizada`() {
        var calls = 0
        val confirmed = config(ports = setOf(80))
        val changed = config(ports = setOf(443))
        val request = AuthorizedScan(
            changed,
            network.connectionId,
            ScanAuthorization.confirm(confirmed, network)
        )
        val client = probe { address, port ->
            calls++
            result(address, port)
        }

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking {
                ScanCoordinator(FakeRepository { network }, client)
                    .execute(request) {}
            }
        }
        assertEquals(0, calls)
    }

    @Test(timeout = 10_000L)
    fun `cancelar detiene los pendientes y conserva los resultados publicados`() =
        runBlocking {
            val received = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val first = Ipv4Address.parse("192.168.28.1")
            val second = Ipv4Address.parse("192.168.28.2")
            val client = probe { address, port ->
                active.incrementAndGet()
                try {
                    if (address == first || address == second) {
                        result(address, port)
                    } else {
                        awaitCancellation()
                    }
                } finally {
                    active.decrementAndGet()
                }
            }
            val request = request(config(ports = setOf(80)))
            val results = mutableListOf<ProbeResult>()
            val task = async {
                ScanCoordinator(FakeRepository { network }, client)
                    .execute(request) {
                        results.add(it)
                        if (results.size == 2) received.complete(Unit)
                    }
            }

            received.await()
            task.cancelAndJoin()

            assertEquals(2, results.size)
            assertTrue(task.isCancelled)
            assertEquals(0, active.get())
            assertFalse(request.validate(network))
        }

    @Test(timeout = 10_000L)
    fun `conserva un resultado devuelto justo al solicitar la cancelacion`() =
        runBlocking {
            lateinit var task: Job
            val results = mutableListOf<ProbeResult>()
            val client = probe { address, port ->
                task.cancel()
                result(address, port)
            }
            task = launch(start = CoroutineStart.LAZY) {
                ScanCoordinator(FakeRepository { network }, client)
                    .execute(request()) { results.add(it) }
            }

            task.start()
            task.join()

            assertEquals(1, results.size)
            assertTrue(task.isCancelled)
        }

    @Test(timeout = 10_000L)
    fun `un cambio de conexion durante un intento interrumpe a los demas`() {
        val published = CompletableDeferred<Unit>()
        val results = mutableListOf<ProbeResult>()
        val active = AtomicInteger()
        val first = Ipv4Address.parse("192.168.28.1")
        val client = probe { address, port ->
            active.incrementAndGet()
            try {
                if (address == first) {
                    result(address, port)
                } else {
                    published.await()
                    throw ScanConnectionChangedException()
                }
            } finally {
                active.decrementAndGet()
            }
        }

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking {
                ScanCoordinator(FakeRepository { network }, client)
                    .execute(request(config(ports = setOf(80)))) {
                        results.add(it)
                        published.complete(Unit)
                    }
            }
        }

        assertEquals(1, results.size)
        assertEquals(0, active.get())
    }

    @Test
    fun `comprueba la conexion al finalizar aunque todos los intentos hayan terminado`() {
        val repository = FakeRepository { read ->
            if (read == 1) network else {
                WifiNetworkResult.Unavailable(WifiNetworkResult.Reason.NO_NETWORK)
            }
        }
        val results = mutableListOf<ProbeResult>()
        val config = config(end = "192.168.28.2", ports = setOf(80))

        assertThrows(ScanConnectionChangedException::class.java) {
            runBlocking {
                ScanCoordinator(repository, probe(::result))
                    .execute(request(config)) { results.add(it) }
            }
        }

        assertEquals(2, results.size)
    }

    @Test
    fun `una confirmacion utilizada no permite ejecutar otra revision`() = runBlocking {
        var calls = 0
        val client = probe { address, port ->
            calls++
            result(address, port)
        }
        val coordinator = ScanCoordinator(FakeRepository { network }, client)
        val request = request(config(end = "192.168.28.2", ports = setOf(80)))

        coordinator.execute(request) {}

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.execute(request) {} }
        }
        assertEquals(2, calls)
    }

    @Test(timeout = 10_000L)
    fun `una segunda ejecucion de la misma solicitud no interrumpe la primera`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val client = probe { address, port ->
                calls++
                release.await()
                result(address, port)
            }
            val coordinator = ScanCoordinator(FakeRepository { network }, client)
            val request = request(config(end = "192.168.28.2", ports = setOf(80)))
            val first = async {
                coordinator.execute(
                    request,
                    onStarted = { started.complete(Unit) },
                    onResult = {}
                )
            }

            started.await()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { coordinator.execute(request) {} }
            }
            assertTrue(request.validate(network))

            release.complete(Unit)
            first.await()

            assertEquals(2, calls)
        }

    @Test(timeout = 10_000L)
    fun `rechaza otra revision mientras mantiene los trabajadores de la primera`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val client = probe { address, port ->
                calls++
                release.await()
                result(address, port)
            }
            val coordinator = ScanCoordinator(FakeRepository { network }, client)
            val config = config(end = "192.168.28.2", ports = setOf(80))
            val firstRequest = request(config)
            val secondRequest = request(config)
            val first = async {
                coordinator.execute(
                    firstRequest,
                    onStarted = { started.complete(Unit) },
                    onResult = {}
                )
            }

            started.await()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { coordinator.execute(secondRequest) {} }
            }
            assertTrue(firstRequest.validate(network))
            assertFalse(secondRequest.validate(network))

            release.complete(Unit)
            first.await()
            assertEquals(2, calls)
        }

    @Test
    fun `un error inesperado conserva los resultados ya publicados`() {
        val published = CompletableDeferred<Unit>()
        val results = mutableListOf<ProbeResult>()
        val first = Ipv4Address.parse("192.168.28.1")
        val client = probe { address, port ->
            if (address == first) {
                result(address, port)
            } else {
                published.await()
                error("Fallo inesperado")
            }
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                ScanCoordinator(FakeRepository { network }, client)
                    .execute(request(config(ports = setOf(80)))) {
                        results.add(it)
                        published.complete(Unit)
                    }
            }
        }

        assertEquals(1, results.size)
    }
}
