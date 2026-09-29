package com.optisuite.optiplay.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.ui.PlayerViewModel

@Composable
fun EqualizerScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val fx = vm.effects
    val enabled by fx.enabled.collectAsStateWithLifecycle()
    val bands by fx.bands.collectAsStateWithLifecycle()
    val bass by fx.bass.collectAsStateWithLifecycle()
    val virtual by fx.virtual.collectAsStateWithLifecycle()
    val preset by fx.preset.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ecualizador", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (fx.available) "Se aplica a la música y los vídeos de OptiPlay · ${fx.centerFreqs.size} bandas (las que ofrece tu móvil)" else "No disponible en este dispositivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { fx.setEnabled(it) }, enabled = fx.available)
        }

        if (!fx.available) return

        Spacer(Modifier.height(8.dp))
        // Presets
        var menu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menu = true }, enabled = enabled) {
                Text("Preset: " + (fx.presetNames.getOrNull(preset) ?: "Personalizado"))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fx.presetNames.forEachIndexed { i, name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { fx.usePreset(i); menu = false })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Bandas (sliders verticales)
        val min = fx.minLevel.toFloat()
        val max = fx.maxLevel.toFloat()
        Row(
            Modifier.fillMaxWidth().height(240.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            bands.forEachIndexed { i, level ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                    Text((level / 100).let { if (it > 0) "+$it dB" else "$it dB" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VerticalSlider(
                        value = level.toFloat(),
                        onValueChange = { fx.setBand(i, it.toInt().toShort()) },
                        valueRange = min..max,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).width(56.dp)
                    )
                    val freq = fx.centerFreqs.getOrNull(i)?.let { it / 1000 } ?: 0
                    Text(
                        if (freq >= 1000) "${freq / 1000}k" else "$freq",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Refuerzo de graves (Bass Boost)", style = MaterialTheme.typography.labelLarge)
        Slider(value = bass.toFloat(), onValueChange = { fx.setBass(it.toInt()) }, valueRange = 0f..1000f, enabled = enabled)

        Spacer(Modifier.height(8.dp))
        Text("Sonido envolvente (Virtualizer)", style = MaterialTheme.typography.labelLarge)
        Slider(value = virtual.toFloat(), onValueChange = { fx.setVirtualizer(it.toInt()) }, valueRange = 0f..1000f, enabled = enabled)
    }
}

/** Slider vertical reutilizando el Slider horizontal rotado 90°. */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0f, 0f) }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}
