package com.example.hogarscan.scan.domain.model

data class ScanSummaryReceipt(
    val scanId: String,
    val summaryGeneration: Int,
    val receivedAtMillis: Long,
    val created: Boolean
) {
    init {
        require(UUID_PATTERN.matches(scanId)) {
            "El recibo debe identificar una revisión válida."
        }
        require(summaryGeneration >= 0) {
            "La generación del recibo no puede ser negativa."
        }
        require(receivedAtMillis in 0L..253_402_300_799_999L) {
            "La fecha de recepción no es válida."
        }
    }

    private companion object {
        val UUID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-" +
                    "[0-9a-f]{4}-[0-9a-f]{12}"
        )
    }
}