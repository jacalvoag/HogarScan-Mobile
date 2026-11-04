package com.example.hogarscan.scan.data.local.mapper

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeOutcome
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord
import com.example.hogarscan.scan.domain.model.ScanRecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRecordMapperTest {

    @Test
    fun roundTrip_preservesCompletedRecordAndOrdersResults() {
        val original = completedRecord()
        val stored = original.toLocal()

        val restored = stored.copy(
            ports = stored.ports.reversed(),
            results = stored.results.reversed()
        ).toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.ownerId, restored.ownerId)
        assertEquals(original.status, restored.status)
        assertEquals(original.startedAtMillis, restored.startedAtMillis)
        assertEquals(original.finishedAtMillis, restored.finishedAtMillis)
        assertEquals(
            original.config.networkScope,
            restored.config.networkScope
        )
        assertEquals(original.config.startAddress, restored.config.startAddress)
        assertEquals(original.config.endAddress, restored.config.endAddress)
        assertEquals(original.config.ports, restored.config.ports)
        assertEquals(original.results, restored.results)
        assertEquals(
            listOf(Ipv4Address.parse("192.168.1.41")),
            restored.config.targetAddresses
        )
        assertEquals(2, restored.config.plannedProbeCount)
    }

    @Test
    fun runningRecord_preservesPortsWithoutResults() {
        val stored = completedRecord().toLocal()
        val restored = stored.copy(
            record = stored.record.copy(
                status = ScanRecordStatus.RUNNING,
                finishedAtMillis = null
            ),
            results = emptyList()
        ).toDomain()

        assertEquals(ScanRecordStatus.RUNNING, restored.status)
        assertNull(restored.finishedAtMillis)
        assertEquals(listOf(80, 443), restored.config.ports)
        assertEquals(2, restored.config.plannedProbeCount)
        assertTrue(restored.results.isEmpty())
    }

    @Test
    fun cancelledRecord_preservesPartialResults() {
        val stored = completedRecord().toLocal()
        val restored = stored.copy(
            record = stored.record.copy(
                status = ScanRecordStatus.CANCELLED
            ),
            results = stored.results.take(1)
        ).toDomain()

        assertEquals(ScanRecordStatus.CANCELLED, restored.status)
        assertEquals(stored.record.finishedAtMillis, restored.finishedAtMillis)
        assertEquals(completedRecord().results.take(1), restored.results)
        assertEquals(2, restored.config.plannedProbeCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun completedRecord_rejectsMissingResults() {
        val stored = completedRecord().toLocal()

        stored.copy(
            results = stored.results.dropLast(1)
        ).toDomain()
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomain_rejectsResultsFromAnotherRecord() {
        val stored = completedRecord().toLocal()

        stored.copy(
            results = stored.results.map {
                it.copy(scanId = "another-scan")
            }
        ).toDomain()
    }

    private fun completedRecord(): ScanRecord {
        val target = Ipv4Address.parse("192.168.1.41")
        val config = ScanConfig(
            networkScope = NetworkScope(
                localAddress = Ipv4Address.parse("192.168.1.40"),
                prefixLength = 24
            ),
            startAddress = Ipv4Address.parse("192.168.1.40"),
            endAddress = target,
            ports = setOf(80, 443)
        )

        return ScanRecord(
            id = "scan-1",
            ownerId = "owner-a",
            config = config,
            startedAtMillis = 1_000L,
            status = ScanRecordStatus.COMPLETED,
            results = listOf(
                ProbeResult(
                    address = target,
                    port = 80,
                    outcome = ProbeOutcome.ACCEPTED,
                    durationMillis = 26L
                ),
                ProbeResult(
                    address = target,
                    port = 443,
                    outcome = ProbeOutcome.TIMED_OUT,
                    durationMillis = 1_000L
                )
            ),
            finishedAtMillis = 2_000L
        )
    }
}