package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.WifiNetworkResult

interface WifiNetworkRepository {
    suspend fun getCurrentNetwork(): WifiNetworkResult
}