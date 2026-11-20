package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.hogarscan.ui.components.*
import com.example.hogarscan.ui.theme.SpaceMono
import java.net.URI

@Composable
fun FindingDetailScreen(detail: FindingDetail, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var evidenceOpen by rememberSaveable(detail.ruleId, detail.evidence) { mutableStateOf(false) }
    var sourcesOpen by rememberSaveable(detail.ruleId, detail.evidence) { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HogarBackHeader("Detalle de hallazgo", onBack)
        HogarCard {
            Text(detail.category, style = MaterialTheme.typography.bodySmall)
            Text(detail.title, style = MaterialTheme.typography.headlineMedium)
            Text("Prioridad: ${detail.priority} · Certeza: ${detail.confidence}",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("Qué significa", style = MaterialTheme.typography.titleLarge)
        Text(detail.explanation)
        HogarCard {
            Text("Qué puedes revisar", style = MaterialTheme.typography.titleLarge)
            Text(detail.action)
        }
        HogarNotice("Límite de esta observación", detail.limitation, Modifier.fillMaxWidth())
        HogarButton(if (evidenceOpen) "Ocultar evidencia y regla aplicada" else "Ver evidencia y regla aplicada",
            { evidenceOpen = !evidenceOpen }, Modifier.fillMaxWidth(), secondary = true)
        if (evidenceOpen) HogarCard {
            Text(detail.evidence, fontFamily = SpaceMono)
            Text("Regla ${detail.ruleId} · Versión ${detail.rulesetVersion}", style = MaterialTheme.typography.bodySmall)
        }
        HogarButton(if (sourcesOpen) "Ocultar fuentes públicas" else "Ver fuentes públicas",
            { sourcesOpen = !sourcesOpen }, Modifier.fillMaxWidth(), secondary = true)
        if (sourcesOpen) HogarCard {
            Text("NVD: ${detail.nvdStatus} · KEV: ${detail.kevStatus}",
                style = MaterialTheme.typography.titleMedium)
            detail.checkedAt?.let { Text("Consulta: $it", style = MaterialTheme.typography.bodySmall) }
            val match = detail.possibleMatch
            if (match == null) {
                Text("Este hallazgo no contiene una coincidencia CVE.")
            } else {
                Text("Coincidencia posible: ${match.cve}. No confirma una vulnerabilidad.")
                Text("Identificación: ${match.declaredProduct} (${match.declaredSource})")
                Text("Condición: ${match.affectedCondition}")
                Text(match.kevMembership)
                val uriHandler = LocalUriHandler.current
                if (safeNvdUrl(match.nvdUrl, match.cve)) {
                    TextButton(onClick = { uriHandler.openUri(match.nvdUrl) }) { Text("Abrir referencia NVD") }
                }
            }
        }
    }
}

internal fun safeNvdUrl(url: String, cve: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "nvd.nist.gov" && uri.userInfo == null && uri.port == -1 &&
        uri.rawQuery == null && uri.rawFragment == null &&
        Regex("CVE-[0-9]{4}-[0-9]{4,}").matches(cve) && uri.path == "/vuln/detail/$cve"
}.getOrDefault(false)
