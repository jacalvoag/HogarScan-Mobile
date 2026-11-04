package com.example.hogarscan.scan.data.local.mapper

import com.example.hogarscan.scan.data.local.entity.ProbeResultEntity
import com.example.hogarscan.scan.data.local.entity.ScanPortEntity
import com.example.hogarscan.scan.data.local.entity.ScanRecordEntity
import com.example.hogarscan.scan.data.local.relation.ScanRecordWithDetails
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig
import com.example.hogarscan.scan.domain.model.ScanRecord

fun ScanRecord.toLocal(): ScanRecordWithDetails {
    return ScanRecordWithDetails(
        record = ScanRecordEntity(
            id = id,
            ownerId = ownerId,
            localAddress = config.networkScope.localAddress.value,
            prefixLength = config.networkScope.prefixLength,
            startAddress = config.startAddress.value,
            endAddress = config.endAddress.value,
            status = status,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = finishedAtMillis
        ),
        ports = config.ports.map { port ->
            ScanPortEntity(scanId = id, port = port)
        },
        results = results.map { result ->
            ProbeResultEntity(
                scanId = id,
                address = result.address.value,
                port = result.port,
                outcome = result.outcome,
                durationMillis = result.durationMillis
            )
        }
    )
}

fun ScanRecordWithDetails.toDomain(): ScanRecord {
    require(ports.all { it.scanId == record.id }) {
        "Los puertos deben pertenecer a la revisión."
    }
    require(results.all { it.scanId == record.id }) {
        "Los resultados deben pertenecer a la revisión."
    }

    val config = ScanConfig(
        networkScope = NetworkScope(
            localAddress = Ipv4Address.fromValue(record.localAddress),
            prefixLength = record.prefixLength
        ),
        startAddress = Ipv4Address.fromValue(record.startAddress),
        endAddress = Ipv4Address.fromValue(record.endAddress),
        ports = ports.map { it.port }.toSet()
    )

    return ScanRecord(
        id = record.id,
        ownerId = record.ownerId,
        config = config,
        startedAtMillis = record.startedAtMillis,
        status = record.status,
        results = results.sortedWith(
            compareBy<ProbeResultEntity> { it.address }.thenBy { it.port }
        ).map { result ->
            ProbeResult(
                address = Ipv4Address.fromValue(result.address),
                port = result.port,
                outcome = result.outcome,
                durationMillis = result.durationMillis
            )
        },
        finishedAtMillis = record.finishedAtMillis
    )
}