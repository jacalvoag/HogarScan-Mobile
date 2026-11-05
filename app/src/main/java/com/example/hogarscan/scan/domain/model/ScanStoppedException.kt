package com.example.hogarscan.scan.domain.model

import kotlinx.coroutines.CancellationException

class ScanStoppedException(
    val status: ScanRecordStatus
) : CancellationException("La revisión se detuvo.") {
    init {
        require(
            status == ScanRecordStatus.CANCELLED ||
                    status == ScanRecordStatus.INTERRUPTED
        )
    }
}