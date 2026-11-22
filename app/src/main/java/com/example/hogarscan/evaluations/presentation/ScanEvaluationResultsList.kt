package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.hogarscan.evaluations.domain.TargetEvaluation

/** Altura acotada porque el detalle de revisión ya contiene una lista desplazable. */
@Composable
fun ScanEvaluationResultsList(
    evaluations: List<TargetEvaluation>,
    onFindingSelected: (FindingDetail) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().height(480.dp).testTag("scan-evaluation-results"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(evaluations, key = { it.input.address.value }) { evaluation ->
            Column(Modifier.fillMaxWidth().testTag("evaluation-target")) {
                Text("Objetivo: ${evaluation.input.address}")
                EvaluationResultPanel(evaluation.result, onFindingClick = { index ->
                    evaluation.result.findingDetail(index)?.let(onFindingSelected)
                })
            }
        }
    }
}
