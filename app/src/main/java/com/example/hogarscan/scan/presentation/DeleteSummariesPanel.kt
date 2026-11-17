package com.example.hogarscan.scan.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.SessionReader
import com.example.hogarscan.scan.data.sync.DeleteScanSummaries
import com.example.hogarscan.scan.data.sync.DeleteSummariesResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DeleteSummariesPanel(sessions: SessionReader, action: DeleteScanSummaries) {
    val state by sessions.state.collectAsStateWithLifecycle()
    val session = (state as? SessionState.Authenticated)?.session ?: return
    key(session.contextId) {
        var confirmation by remember { mutableStateOf<SessionSnapshot?>(null) }
        var pending by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        OutlinedButton(enabled = !pending, onClick = { confirmation = session; message = null }) {
            Text("Borrar resúmenes del servidor")
        }
        message?.let { Text(it) }
        confirmation?.let { expected ->
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text("¿Borrar resúmenes del servidor?") },
                text = { Text("Se eliminarán las estadísticas sincronizadas de esta cuenta. El historial y las evaluaciones locales permanecerán en el teléfono.") },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
                confirmButton = {
                    TextButton(onClick = {
                        confirmation = null
                        pending = true
                        scope.launch {
                            message = try {
                                when (action.delete(expected)) {
                                    is DeleteSummariesResult.Deleted -> "Resúmenes borrados."
                                    is DeleteSummariesResult.AlreadyAdvanced -> "La generación ya cambió. Actualiza antes de confirmar otra operación."
                                    DeleteSummariesResult.NewConfirmationRequired -> "La generación no coincide. Actualiza la sesión antes de volver a confirmar."
                                    DeleteSummariesResult.SessionChanged -> "La sesión cambió; comprueba el estado antes de volver a intentar."
                                    is DeleteSummariesResult.Failure -> "No se pudo confirmar el borrado. Comprueba el estado antes de volver a intentar."
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { "No se pudo confirmar el borrado. Comprueba el estado antes de volver a intentar." }
                            pending = false
                        }
                    }) { Text("Borrar resúmenes") }
                }
            )
        }
    }
}
