package com.example.hogarscan.scan.presentation

import com.example.hogarscan.scan.domain.model.ProbeResult
import com.example.hogarscan.scan.domain.model.ScanConfig

enum class ScanStatus {
    IDLE,
    STARTING,
    RUNNING,
    CANCELLING,
    COMPLETED,
    CANCELLED,
    INTERRUPTED,
    FAILED
}

data class ScanUiState(
    val status: ScanStatus = ScanStatus.IDLE,
    val config: ScanConfig? = null,
    val results: List<ProbeResult> = emptyList(),
    val message: String? = null
) {
    val isActive: Boolean
        get() = status == ScanStatus.STARTING ||
            status == ScanStatus.RUNNING ||
            status == ScanStatus.CANCELLING

    val plannedProbeCount: Int
        get() = config?.plannedProbeCount ?: 0
}
