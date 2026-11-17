package com.example.hogarscan.evaluations.data

import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.EvaluationInput
import com.example.hogarscan.scan.domain.model.ProbeOutcome

/** Datos del contrato remoto: no contiene propietario, revisión ni dirección. */
data class EvaluationRequest(
    val probes: List<RemoteProbe>,
    val identification: DeclaredIdentification?
)

data class RemoteProbe(val port: Int, val outcome: RemoteOutcome)
enum class RemoteOutcome { ACCEPTED, REFUSED, TIMEOUT, ERROR }

fun EvaluationInput.toEvaluationRequest(): EvaluationRequest = EvaluationRequest(
    probes = probes.map { probe ->
        RemoteProbe(probe.port, when (probe.outcome) {
            ProbeOutcome.ACCEPTED -> RemoteOutcome.ACCEPTED
            ProbeOutcome.REJECTED -> RemoteOutcome.REFUSED
            ProbeOutcome.TIMED_OUT -> RemoteOutcome.TIMEOUT
            ProbeOutcome.ERROR -> RemoteOutcome.ERROR
        })
    },
    identification = identification
)
