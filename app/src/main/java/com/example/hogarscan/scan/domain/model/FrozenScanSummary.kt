package com.example.hogarscan.scan.domain.model

/**
 * Contenido congelado para sincronizar una revisión.
 *
 * userId identifica al propietario local del pendiente.
 * El adaptador HTTP lo excluye del cuerpo: la API obtiene
 * la cuenta mediante la sesión autenticada.
 *
 * summaryGeneration conserva su valor durante los reintentos.
 */
class FrozenScanSummary(
    userId: String,
    scanId: String,
    val summaryGeneration: Int,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val addressesScanned: Int,
    val addressesResponded: Int,
    val acceptedServices: Int,
    val findingsByCategory: FindingCounts
) {
    val userId: String = userId.lowercase()
    val scanId: String = scanId.lowercase()

    init {
        require(UUID_PATTERN.matches(this.userId)) {
            "La cuenta debe tener un UUID válido."
        }
        require(UUID_PATTERN.matches(this.scanId)) {
            "La revisión debe tener un UUID válido."
        }
        require(summaryGeneration >= 0) {
            "La generación no puede ser negativa."
        }
        require(startedAtMillis in 0L..MAX_TIMESTAMP_MILLIS) {
            "La fecha de inicio no es válida."
        }
        require(finishedAtMillis in 0L..MAX_TIMESTAMP_MILLIS) {
            "La fecha de cierre no es válida."
        }
        require(addressesScanned in 1..254) {
            "La revisión debe incluir entre 1 y 254 direcciones."
        }
        require(addressesResponded in 0..addressesScanned) {
            "Las direcciones que respondieron no pueden superar las revisadas."
        }
        require(acceptedServices in 0..addressesResponded * 6) {
            "Los servicios aceptados exceden el límite del piloto."
        }

        // El reloj del dispositivo puede retroceder.
        // Se conservan ambas fechas sin exigir cierre >= inicio.
    }

    private companion object {
        val UUID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-" +
                    "[0-9a-f]{4}-[0-9a-f]{12}"
        )

        // Último milisegundo del año 9999, compatible con el contrato UTC.
        const val MAX_TIMESTAMP_MILLIS = 253_402_300_799_999L
    }
}