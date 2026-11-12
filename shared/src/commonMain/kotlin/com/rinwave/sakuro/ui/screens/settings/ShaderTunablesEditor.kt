package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.ShaderTunable
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.preset_shader_reset
import sakuro.composeapp.generated.resources.preset_shader_tunables
import sakuro.composeapp.generated.resources.preset_shader_tunables_hint
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live `//!PARAM` controls for every shader of a chain: one slider per tunable,
 * grouped by shader. Only changed values are reported back — [values] holds
 * overrides by file name then param name, exactly what a profile stores.
 */
@Composable
internal fun ShaderTunablesEditor(
    chain: List<String>,
    inspector: ShaderInspector,
    values: Map<String, Map<String, Float>>,
    onChange: (file: String, param: String, value: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Parsing touches the file system; keep it off recomposition.
    val tunables = remember(chain, inspector) { chain.associateWith { inspector.tunables(it) } }
    if (tunables.values.all { it.isEmpty() }) return

    Column(modifier) {
        Text(
            stringResource(Res.string.preset_shader_tunables),
            style = MaterialTheme.typography.labelLarge,
            color = SakuroColors.AccentLavender,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(Res.string.preset_shader_tunables_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SakuroColors.TextMuted,
        )
        for (file in chain) {
            val params = tunables[file].orEmpty()
            if (params.isEmpty()) continue
            Text(
                BundledShaders.byFileName(file)?.displayName ?: file,
                style = MaterialTheme.typography.labelMedium,
                color = SakuroColors.TextPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            for (param in params) {
                TunableSlider(
                    tunable = param,
                    value = values[file]?.get(param.name) ?: param.default,
                    onChange = { onChange(file, param.name, it) },
                )
            }
        }
    }
}

@Composable
private fun TunableSlider(tunable: ShaderTunable, value: Float, onChange: (Float?) -> Unit) {
    val changed = abs(value - tunable.default) > EPSILON
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tunable.name,
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (tunable.description.isNotEmpty()) {
                    Text(
                        tunable.description,
                        color = SakuroColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                tunable.format(value),
                color = if (changed) SakuroColors.AccentSakura else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = { onChange(tunable.clamp(it)) },
            valueRange = tunable.range,
            steps = tunable.steps,
            colors = SliderDefaults.colors(
                thumbColor = SakuroColors.AccentSakura,
                activeTrackColor = SakuroColors.GlowMagenta,
                inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
            ),
        )
        if (changed) {
            // Clearing an override is how a param returns to the shader's default.
            TextButton(onClick = { onChange(null) }) {
                Text(
                    stringResource(Res.string.preset_shader_reset),
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun ShaderTunable.format(value: Float): String =
    if (integral) value.roundToInt().toString() else ((value * ROUND).roundToInt() / ROUND).toString()

private const val ROUND = 100f
private const val EPSILON = 0.0001f
