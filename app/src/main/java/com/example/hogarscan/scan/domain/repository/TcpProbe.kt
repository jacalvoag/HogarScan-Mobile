package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeResult

interface TcpProbe {

    /**
     * Un único intento TCP en la conexión indicada.
     * La cancelación de la corrutina debe propagarse.
     */
    suspend fun probe(
        connectionId: String,
        address: Ipv4Address,
        port: Int
    ): ProbeResult
}