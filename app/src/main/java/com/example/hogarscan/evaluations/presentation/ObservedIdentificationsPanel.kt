package com.example.hogarscan.evaluations.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.observedIdentifications
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.ScanRecord

@Composable
internal fun ObservedIdentificationsPanel(record: ScanRecord, onSelect: (Ipv4Address, DeclaredIdentification) -> Unit) {
    val candidates = remember(record) { record.results.groupBy { it.address }.flatMap { (ip, probes) ->
        val observed = observedIdentifications(probes)
        observed.map { Triple(ip, it, observed.size > 1) }
    } }
    if (candidates.isEmpty()) return
    Text("Identidades anunciadas por los servicios. Si un objetivo anuncia varios productos, selecciona cuál consultar; no se elige el primero automáticamente.")
    LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
        items(candidates, key = { "${it.first}:${it.second.servicePort}" }) { (ip, identity, ambiguous) ->
            Column {
                if (ambiguous) Text("$ip: varios productos observados")
                OutlinedButton(onClick = { onSelect(ip, identity) }) {
                    Text("Usar ${identity.product} ${identity.version} de $ip:${identity.servicePort}")
                }
            }
        }
    }
}
