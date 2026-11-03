package com.example.hogarscan.scan.domain.repository

import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import kotlinx.coroutines.flow.Flow

interface WifiNetworkRepository {
    suspend fun getCurrentNetwork(): WifiNetworkResult

    fun observeNetworkChanges(): Flow<Unit>
}