package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.WifiNetworkResult

sealed interface WifiUiState {
    data object Idle : WifiUiState
    data object Loading : WifiUiState

    data class Loaded(
        val result: WifiNetworkResult
    ) : WifiUiState

    data object Error : WifiUiState
}