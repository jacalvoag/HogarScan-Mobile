package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.repository.TcpProbe
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import com.example.hogarscan.scan.domain.usecase.ScanCoordinator

internal fun networkTestViewModel(repository: WifiNetworkRepository): WifiViewModel {
    val unusedProbe = object : TcpProbe {
        override suspend fun probe(
            connectionId: String,
            address: Ipv4Address,
            port: Int
        ): ProbeResult = error("Esta prueba de red no debe ejecutar intentos TCP.")
    }
    return WifiViewModel(repository, ScanCoordinator(repository, unusedProbe))
}
