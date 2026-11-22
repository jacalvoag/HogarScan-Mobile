package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.ui.components.HogarButton
import com.example.hogarscan.ui.components.HogarLoading
import com.example.hogarscan.ui.components.HogarNotice

@Composable
fun TargetEvaluationPanel(
    scanId: String,
    ownerId: String,
    address: String,
    state: ScanEvaluationUiState?,
    onFindingSelected: (FindingDetail) -> Unit,
    modifier: Modifier = Modifier,
    onOpenEvaluation: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            ScanEvaluationUiState.Loading ->
                HogarLoading("Cargando hallazgos guardados…")

            ScanEvaluationUiState.Ready ->
                HogarNotice(
                    "Evaluación pendiente",
                    "Esta dirección todavía no tiene una evaluación guardada. " +
                            "Puedes preparar la evaluación de la revisión completa."
                )

            ScanEvaluationUiState.Preparing,
            is ScanEvaluationUiState.Running ->
                HogarNotice(
                    "Evaluación en curso",
                    "Los hallazgos aparecerán cuando termine y se guarde " +
                            "la evaluación de la revisión."
                )

            is ScanEvaluationUiState.Error ->
                HogarNotice(
                    "Evaluación no disponible",
                    state.message,
                    error = true
                )

            is ScanEvaluationUiState.Saved -> {
                val target = state.evaluations.singleOrNull { evaluation ->
                    evaluation.input.scanId == scanId &&
                            evaluation.input.ownerId == ownerId &&
                            evaluation.input.address.toString() == address
                }

                if (target == null) {
                    HogarNotice(
                        "Hallazgos no disponibles",
                        "No se encontró una evaluación guardada para esta " +
                                "dirección en la revisión seleccionada."
                    )
                } else {
                    EvaluationResultPanel(
                        result = target.result,
                        onFindingClick = { index ->
                            target.result.findingDetail(index)
                                ?.let(onFindingSelected)
                        }
                    )
                }
            }

            null ->
                HogarNotice(
                    "Evaluación no disponible",
                    "No hay una evaluación disponible para esta revisión. " +
                            "Las observaciones TCP siguen visibles."
                )
        }

        val actionLabel = when (state) {
            ScanEvaluationUiState.Ready ->
                "Preparar evaluación de la revisión"

            ScanEvaluationUiState.Preparing,
            is ScanEvaluationUiState.Running ->
                "Ver progreso de la evaluación"

            is ScanEvaluationUiState.Saved ->
                "Ver evaluación completa"

            is ScanEvaluationUiState.Error ->
                "Ver opciones de evaluación"

            else -> null
        }

        if (actionLabel != null && onOpenEvaluation != null) {
            HogarButton(
                text = actionLabel,
                onClick = onOpenEvaluation,
                modifier = Modifier.fillMaxWidth(),
                secondary = true
            )
        }
    }
}