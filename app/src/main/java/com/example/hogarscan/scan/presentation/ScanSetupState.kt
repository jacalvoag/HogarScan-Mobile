package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.ScanConfig

data class ScanSetupState(
    val startAddress: String = "",
    val endAddress: String = "",
    val selectedPorts: Set<Int> = emptySet(),
    val networkAvailable: Boolean = false,
    val checkingNetwork: Boolean = false,
    val confirmation: ScanConfig? = null,
    val authorizedConfig: ScanConfig? = null,
    val error: String? = null,
    val notice: String? = null
)
