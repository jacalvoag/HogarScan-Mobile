package com.example.hogarscan.evaluations.presentation

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVGImageView
import java.net.URI

private val Page = Color(0xFFF5FCE4)
private val Ink = Color(0xFF171E0F)
private val Deep = Color(0xFF2C3323)
private val Lime = Color(0xFFD6E7A7)
private val Pale = Color(0xFFE4EBD3)
private val Light = Color(0xFFF0F7DF)

@Composable
fun FindingDetailScreen(detail: FindingDetail, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Page).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Deep, CircleShape).clickable(onClickLabel = "Volver al equipo", onClick = onBack), contentAlignment = Alignment.Center) {
                FigmaIcon("back.svg", 13.3333.dp, 13.3333.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DETALLE DE HALLAZGO", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                Text("DIAGNÓSTICO TÉCNICO LOCAL", color = Color(0xFF566431), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
            }
            Box(Modifier.size(40.dp).background(Pale, CircleShape), contentAlignment = Alignment.Center) {
                FigmaIcon("info.svg", 15.8333.dp, 15.8333.dp)
            }
        }
        Column(Modifier.fillMaxWidth().background(Deep, RoundedCornerShape(28.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("HALLAZGO IDENTIFICADO", color = Color(0xFFD1EE75), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                    Text(detail.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
                }
                Row(Modifier.padding(start = 6.dp).background(Color(0xFFF2B233), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Deep, CircleShape))
                    Text(detail.priority.uppercase(), color = Deep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0x1ADEE5CE), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaIcon("priority.svg", 12.dp, 15.dp)
                Text("La prioridad corresponde a este hallazgo; no es un índice global de vulnerabilidad.", color = Light, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(20.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailMetric("Categoría analítica", detail.category)
                HorizontalDivider(color = Color(0x1AEAF1D9))
                DetailMetric("Prioridad", detail.priority)
                HorizontalDivider(color = Color(0x1AEAF1D9))
                DetailMetric("Nivel de certeza", detail.confidence)
                Row(Modifier.fillMaxWidth().background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    FigmaIcon("certainty.svg", 13.3333.dp, 16.dp)
                    Text(detail.evidence, color = Page, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Lime, RoundedCornerShape(28.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FigmaIcon("guidance.svg", 16.6667.dp, 15.dp)
                    Text("SUSTENTO Y\nORIENTACIÓN", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
                }
                Text("4 puntos\nclave", modifier = Modifier.background(Light, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), color = Color(0xFF566431), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
            GuidanceBlock("1. QUÉ SE OBSERVÓ", detail.evidence, Color(0xFF516600))
            GuidanceBlock("2. QUÉ SIGNIFICA", detail.explanation, Color(0xFF566431))
            GuidanceBlock("3. QUÉ PUEDES REVISAR", detail.action, Color(0xFFF2B233))
            GuidanceBlock("4. QUÉ NO SE PUEDE CONCLUIR", detail.limitation, Color(0xFF5F5E5E))
            Text("Regla ${detail.ruleId} · Versión ${detail.rulesetVersion}", modifier = Modifier.padding(horizontal = 4.dp), color = Color(0xFF5A6835), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Column(Modifier.fillMaxWidth().background(Pale, RoundedCornerShape(28.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FigmaIcon("sources.svg", 13.3333.dp, 16.6667.dp)
                Text("FUENTES PÚBLICAS Y CVE", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(28.dp).background(Color(0xFFEAF1D9), CircleShape), contentAlignment = Alignment.Center) {
                    FigmaIcon("source_state.svg", 15.dp, 12.0562.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NVD: ${detail.nvdStatus} · KEV: ${detail.kevStatus}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
                    detail.checkedAt?.let { Text("Consulta: $it", color = Color(0xFF5F5E5E), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    val match = detail.possibleMatch
                    if (match == null) {
                        Text("Este hallazgo no contiene una coincidencia CVE. Las posibles coincidencias se presentan como hallazgos independientes en la evaluación.", color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
                    } else {
                        Text("Coincidencia posible: ${match.cve}. No confirma una vulnerabilidad.", color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
                        Text("Identificación: ${match.declaredProduct} (${match.declaredSource})", color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
                        Text("Condición: ${match.affectedCondition}", color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
                        Text(match.kevMembership, color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
                        val uriHandler = LocalUriHandler.current
                        if (safeNvdUrl(match.nvdUrl, match.cve)) {
                            Text("Abrir referencia NVD", modifier = Modifier.clickable { uriHandler.openUri(match.nvdUrl) }.padding(vertical = 8.dp), color = Color(0xFF516600), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(Deep, CircleShape).clickable(onClickLabel = "Volver al equipo", onClick = onBack).padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FigmaIcon("return.svg", 16.6667.dp, 13.3333.dp)
            Text("VOLVER AL EQUIPO", modifier = Modifier.padding(start = 10.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFD6DDC6), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Page, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GuidanceBlock(title: String, body: String, dot: Color) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
        }
        Text(body, modifier = Modifier.padding(start = 16.dp), color = Color(0xFF454838), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun FigmaIcon(name: String, width: Dp, height: Dp) {
    AndroidView(factory = { context -> SVGImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageAsset("figma/finding_detail/$name")
        contentDescription = null
    } }, modifier = Modifier.size(width, height).testTag("finding-icon-$name").semantics { contentDescription = "" })
}

internal fun safeNvdUrl(url: String, cve: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "nvd.nist.gov" && uri.userInfo == null && uri.port == -1 &&
        uri.rawQuery == null && uri.rawFragment == null &&
        Regex("CVE-[0-9]{4}-[0-9]{4,}").matches(cve) && uri.path == "/vuln/detail/$cve"
}.getOrDefault(false)
