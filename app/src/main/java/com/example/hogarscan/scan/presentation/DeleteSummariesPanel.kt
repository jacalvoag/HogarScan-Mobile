package com.example.hogarscan.scan.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.example.hogarscan.ui.components.HogarButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DeleteSummariesPanel(viewModel: DeleteSummariesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.session == null) return
    HogarButton("Borrar resúmenes del servidor", viewModel::requestConfirmation, Modifier.fillMaxWidth(),
        enabled = !state.pending, destructive = true)
    state.message?.let { Text(it) }
    if (state.confirmation != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirmation,
            title = { Text("¿Borrar resúmenes del servidor?") },
            text = { Text("Se eliminarán las estadísticas sincronizadas de esta cuenta. El historial y las evaluaciones locales permanecerán en el teléfono.") },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirmation) { Text("Cancelar") } },
            confirmButton = { TextButton(onClick = viewModel::confirm) { Text("Borrar resúmenes") } }
        )
    }
}
