package com.example.hogarscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.hogarscan.ui.theme.HogarScanTheme
import com.example.hogarscan.ui.theme.HogarColors
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.caverock.androidsvg.SVG
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.VisualTransformation

enum class HogarIcon(val asset: String) {
    Home("home"), History("history"), Statistics("stats"), Account("account"), Logo("logo"), Back("back")
}

/** Iconos originales del Figma revisado; el contenedor conserva su geometría de 24 dp. */
@Composable
fun HogarIconImage(
    icon: HogarIcon,
    modifier: Modifier = Modifier,
    tint: Boolean = true,
    size: Dp = 24.dp,
    assetName: String = icon.asset,
    tintColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val assets = LocalContext.current.assets
    val sizePx = with(LocalDensity.current) {
        size.roundToPx().coerceAtLeast(1)
    }

    val image = remember(assets, assetName, sizePx) {
        val svg = SVG.getFromAsset(
            assets,
            "figma/revised/$assetName.svg"
        )

        svg.setDocumentWidth(sizePx.toFloat())
        svg.setDocumentHeight(sizePx.toFloat())

        val bitmap = Bitmap.createBitmap(
            sizePx,
            sizePx,
            Bitmap.Config.ARGB_8888
        )

        svg.renderToCanvas(Canvas(bitmap))
        bitmap.asImageBitmap()
    }

    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = if (tint && icon != HogarIcon.Logo) {
            ColorFilter.tint(tintColor)
        } else {
            null
        }
    )
}

@Composable
fun HogarBrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HogarIconImage(HogarIcon.Logo, tint = false, size = 32.dp)
        Text("HogarScan", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun HogarCard(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun HogarProgress(completed: Int, total: Int, label: String) {
    val fraction = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
    HogarCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun HogarButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                enabled: Boolean = true, secondary: Boolean = false, destructive: Boolean = false) {
    val colors = when {
        destructive -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer)
        secondary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        else -> ButtonDefaults.buttonColors()
    }
    Button(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), colors = colors) { Text(text) }
}

@Composable
fun HogarField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fieldModifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> colors.onSurface.copy(alpha = 0.38f)
                isError -> colors.error
                else -> colors.onSurface
            }
        )

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                },
            enabled = enabled,
            isError = isError,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                disabledContainerColor =
                    colors.surfaceVariant.copy(alpha = 0.5f),
                errorContainerColor = colors.errorContainer,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                errorTextColor = colors.onErrorContainer,
                focusedIndicatorColor = colors.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = colors.error
            )
        )

        if (help.isNotBlank()) {
            Text(
                text = help,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    colors.error
                } else {
                    colors.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun HogarNotice(title: String, body: String, modifier: Modifier = Modifier, error: Boolean = false,
                warning: Boolean = false) {
    val background = when {
        error -> MaterialTheme.colorScheme.errorContainer
        warning -> HogarColors.Warning
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        error -> MaterialTheme.colorScheme.onErrorContainer
        warning -> HogarColors.WarningInk
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier.background(background, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = foreground)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = foreground)
    }
}

@Composable
fun HogarStatusTag(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
        .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun HogarMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HogarEmptyState(message: String, modifier: Modifier = Modifier,
                    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun HogarBackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) {
            HogarIconImage(HogarIcon.Back)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun HogarComponentsPreview() {
    HogarScanTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HogarBackHeader("Nueva revisión", {})
            HogarNotice("Estado", "Explicación breve.")
            HogarButton("Continuar", {}, Modifier.fillMaxWidth())
            HogarButton("Otra acción", {}, Modifier.fillMaxWidth(), secondary = true)
            HogarStatusTag("Completada")
            HogarMetric("0", "Revisiones")
            HogarButton("Borrar", {}, Modifier.fillMaxWidth(), destructive = true)
        }
    }
}
