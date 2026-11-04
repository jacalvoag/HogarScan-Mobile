package com.example.hogarscan.scan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanRecordTest {

    private val config = ScanConfig(
        networkScope = NetworkScope(Ipv4Address.parse("192.168.28.43"), 24),
        startAddress = Ipv4Address.parse("192.168.28.40"),
        endAddress = Ipv4Address.parse("192.168.28.44"),
        ports = setOf(80)
    )

    private val completeResults = config.targetAddresses.map {
        ProbeResult(it, 80, ProbeOutcome.TIMED_OUT, 1_000L)
    }

    private fun record(
        status: ScanRecordStatus = ScanRecordStatus.COMPLETED,
        results: List<ProbeResult> = completeResults,
        finishedAtMillis: Long? =
            if (status == ScanRecordStatus.RUNNING) null else 2_000L,
        id: String = "scan-1",
        ownerId: String = "user-1"
    ) = ScanRecord(
        id = id,
        ownerId = ownerId,
        config = config,
        startedAtMillis = 1_000L,
        status = status,
        results = results,
        finishedAtMillis = finishedAtMillis
    )

    @Test
    fun `una revision completada conserva todos los intentos`() {
        val record = record()

        assertEquals(config.plannedProbeCount, record.results.size)
    }

    @Test
    fun `conserva resultados parciales al cancelar interrumpir o fallar`() {
        listOf(
            ScanRecordStatus.CANCELLED,
            ScanRecordStatus.INTERRUPTED,
            ScanRecordStatus.FAILED
        ).forEach { status ->
            val record = record(status = status, results = completeResults.take(1))

            assertEquals(completeResults.take(1), record.results)
        }
    }

    @Test
    fun `rechaza marcar como completada una revision con intentos pendientes`() {
        assertThrows(IllegalArgumentException::class.java) {
            record(results = completeResults.dropLast(1))
        }
    }

    @Test
    fun `rechaza duplicados aunque el conteo coincida con el total previsto`() {
        val duplicated = completeResults.dropLast(1) + completeResults.first()

        assertThrows(IllegalArgumentException::class.java) {
            record(results = duplicated)
        }
    }

    @Test
    fun `rechaza resultados del telefono fuera del rango o de otro puerto`() {
        val invalid = listOf(
            ProbeResult(Ipv4Address.parse("192.168.28.43"), 80, ProbeOutcome.ERROR, 0L),
            ProbeResult(Ipv4Address.parse("192.168.28.45"), 80, ProbeOutcome.ERROR, 0L),
            ProbeResult(Ipv4Address.parse("192.168.28.40"), 443, ProbeOutcome.ERROR, 0L)
        )

        invalid.forEach { result ->
            assertThrows(IllegalArgumentException::class.java) {
                record(
                    status = ScanRecordStatus.INTERRUPTED,
                    results = listOf(result)
                )
            }
        }
    }

    @Test
    fun `una revision en curso conserva el cierre pendiente`() {
        val record = record(
            status = ScanRecordStatus.RUNNING,
            results = completeResults.take(1)
        )

        assertNull(record.finishedAtMillis)
    }

    @Test
    fun `exige que el cierre corresponda al estado de la revision`() {
        assertThrows(IllegalArgumentException::class.java) {
            record(status = ScanRecordStatus.RUNNING, finishedAtMillis = 2_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(finishedAtMillis = null)
        }
    }

    @Test
    fun `una interrupcion puede ocurrir despues de recibir todos los resultados`() {
        val record = record(status = ScanRecordStatus.INTERRUPTED)

        assertEquals(config.plannedProbeCount, record.results.size)
    }

    @Test
    fun `modificar la lista original no altera los resultados del registro`() {
        val original = completeResults.toMutableList()
        val record = record(results = original)

        original.clear()

        assertEquals(config.plannedProbeCount, record.results.size)
    }

    @Test
    fun `rechaza registros sin identificador o propietario`() {
        assertThrows(IllegalArgumentException::class.java) {
            record(id = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(ownerId = " ")
        }
    }
}