package com.example.hogarscan.scan.domain.model

class ScanRecord(
    val id: String,
    val ownerId: String,
    val config: ScanConfig,
    val startedAtMillis: Long,
    val status: ScanRecordStatus,
    results: List<ProbeResult> = emptyList(),
    val finishedAtMillis: Long? = null
) {
    val results: List<ProbeResult> = results.toList()

    init {
        require(id.isNotBlank()) {
            "La revisión debe tener un identificador."
        }
        require(ownerId.isNotBlank()) {
            "La revisión debe pertenecer a un propietario."
        }
        require(startedAtMillis >= 0L) {
            "La fecha de inicio no puede ser negativa."
        }
        require(finishedAtMillis == null || finishedAtMillis >= 0L) {
            "La fecha de cierre no puede ser negativa."
        }

        if (status == ScanRecordStatus.RUNNING) {
            require(finishedAtMillis == null) {
                "Una revisión en curso debe tener el cierre pendiente."
            }
        } else {
            require(finishedAtMillis != null) {
                "Una revisión finalizada debe tener fecha de cierre."
            }
        }

        val targets = config.targetAddresses.toSet()
        require(this.results.all {
            it.address in targets && it.port in config.ports
        }) {
            "Los resultados deben pertenecer al alcance configurado."
        }
        require(
            this.results.map { it.address to it.port }.toSet().size ==
                    this.results.size
        ) {
            "No puede repetirse una combinación de IP y puerto."
        }

        if (status == ScanRecordStatus.COMPLETED) {
            require(this.results.size == config.plannedProbeCount) {
                "Una revisión completada debe contener todos los intentos."
            }
        }
    }
}