package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.displayName
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserPresetStore
import com.rinwave.sakuro.navigation.SettingsComponent
import com.rinwave.sakuro.ui.theme.SakuroColors

@Composable
fun SettingsScreen(component: SettingsComponent) {
    val engineType by component.engineType.collectAsState()
    val presetId by component.presetId.collectAsState()
    val debugOverlay by component.debugOverlay.collectAsState()
    val adaptiveEnabled by component.adaptiveEnabled.collectAsState()
    val gesturesEnabled by component.gesturesEnabled.collectAsState()
    val gestureSensitivity by component.gestureSensitivity.collectAsState()
    val presets by component.presets.collectAsState()
    val userPresets by component.userPresetList.collectAsState()

    val clipboard = LocalClipboardManager.current
    var editorInitial by remember { mutableStateOf<UpscaleProfile?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var presetMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = component.onBack) {
                Icon(Lucide.ChevronLeft, "Back", tint = SakuroColors.TextPrimary)
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = SakuroColors.TextPrimary)
        }

        SectionTitle("Playback engine")
        component.availableEngines.forEach { type ->
            EngineRow(
                type = type,
                selected = type == engineType,
                enabled = true,
                onClick = { component.selectEngine(type) },
            )
        }
        if (EngineType.MPV !in component.availableEngines) {
            EngineRow(type = EngineType.MPV, selected = false, enabled = false, onClick = {})
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Default upscale preset")
        presets.forEach { preset ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { component.selectPreset(preset.id) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preset.id == presetId,
                    onClick = { component.selectPreset(preset.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = SakuroColors.AccentSakura),
                )
                Column(Modifier.padding(start = 4.dp)) {
                    Text(preset.name, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(preset.description, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Custom presets")
        if (userPresets.isEmpty()) {
            Text(
                "No custom presets yet - create one or paste one from the clipboard.",
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        userPresets.forEach { preset ->
            UserPresetRow(
                preset = preset,
                onEdit = {
                    editorInitial = preset
                    editorVisible = true
                },
                onExport = {
                    clipboard.setText(AnnotatedString(component.exportUserPreset(preset)))
                    presetMessage = "${preset.name} copied to clipboard"
                },
                onDelete = {
                    component.deleteUserPreset(preset.id)
                    presetMessage = "${preset.name} deleted"
                },
            )
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(
                onClick = {
                    editorInitial = null
                    editorVisible = true
                },
            ) {
                Icon(Lucide.Plus, null, Modifier.size(16.dp), tint = SakuroColors.AccentSakura)
                Spacer(Modifier.size(6.dp))
                Text("Create", color = SakuroColors.AccentSakura)
            }
            TextButton(
                onClick = {
                    val raw = clipboard.getText()?.text.orEmpty()
                    presetMessage = component.importUserPreset(raw).fold(
                        onSuccess = { "Imported ${it.name}" },
                        onFailure = { "Clipboard does not contain a valid preset" },
                    )
                },
            ) {
                Icon(Lucide.ClipboardPaste, null, Modifier.size(16.dp), tint = SakuroColors.AccentSakura)
                Spacer(Modifier.size(6.dp))
                Text("From clipboard", color = SakuroColors.AccentSakura)
            }
        }
        presetMessage?.let { message ->
            Text(
                message,
                color = SakuroColors.AccentLavender,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Controls")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setGesturesEnabled(!gesturesEnabled) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Player gestures", color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Swipes: brightness/volume/seek; pinch: frame mode",
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = gesturesEnabled,
                onCheckedChange = component::setGesturesEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SakuroColors.AccentSakura,
                    checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
                ),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Swipe sensitivity",
                    color = if (gesturesEnabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatMultiplier(gestureSensitivity)}×",
                    color = SakuroColors.AccentSakura,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = gestureSensitivity,
                onValueChange = component::setGestureSensitivity,
                valueRange = SakuroSettings.SENSITIVITY_MIN..SakuroSettings.SENSITIVITY_MAX,
                steps = SENSITIVITY_STEPS,
                enabled = gesturesEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = SakuroColors.AccentSakura,
                    activeTrackColor = SakuroColors.GlowMagenta,
                    inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
                ),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Debug")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setAdaptiveEnabled(!adaptiveEnabled) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Adaptive shader switching",
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Simplifies the chain under heat or FPS drops. " +
                        "Turn off for clean tests - the preset is applied as selected.",
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = adaptiveEnabled,
                onCheckedChange = component::setAdaptiveEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SakuroColors.AccentSakura,
                    checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
                ),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setDebugOverlay(!debugOverlay) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Debug overlay", color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Stats for nerds over the player",
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = debugOverlay,
                onCheckedChange = component::setDebugOverlay,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SakuroColors.AccentSakura,
                    checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
                ),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("About")
        Text(
            "Sakuro 0.1.0 — video player with real-time upscaling.\nOpen source (GPLv3), by Rinwave.",
            color = SakuroColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.size(24.dp))
    }

    if (editorVisible) {
        PresetEditorDialog(
            initial = editorInitial,
            onSave = { profile ->
                val saved = component.saveUserPreset(profile)
                presetMessage = "Saved ${saved.name}"
                editorVisible = false
            },
            onDismiss = { editorVisible = false },
        )
    }
}

@Composable
private fun UserPresetRow(
    preset: UpscaleProfile,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(preset.name, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(preset.description, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onEdit) {
            Icon(Lucide.Pencil, "Edit", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
        IconButton(onClick = onExport) {
            Icon(Lucide.Copy, "Export to clipboard", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
        IconButton(onClick = onDelete) {
            Icon(Lucide.Trash2, "Delete", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
    }
}

/** Preset editor: name, content class, and a three-pass chain. */
@Composable
private fun PresetEditorDialog(
    initial: UpscaleProfile?,
    onSave: (UpscaleProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var contentClass by remember { mutableStateOf(initial?.contentClass ?: ContentClass.UNKNOWN) }
    var upscale by remember {
        mutableStateOf(
            initial?.passes?.filterIsInstance<UpscalePass.Upscale>()?.firstOrNull()?.factor
                ?: UserPresetStore.UPSCALE_MIN,
        )
    }
    var sharpen by remember {
        mutableStateOf(initial?.passes?.filterIsInstance<UpscalePass.Sharpen>()?.firstOrNull()?.strength ?: 0f)
    }
    var denoise by remember {
        mutableStateOf(initial?.passes?.filterIsInstance<UpscalePass.Denoise>()?.firstOrNull()?.strength ?: 0f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SakuroColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (initial == null) "New preset" else "Edit preset",
                    style = MaterialTheme.typography.titleMedium,
                    color = SakuroColors.TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SakuroColors.AccentSakura,
                        cursorColor = SakuroColors.AccentSakura,
                        focusedLabelColor = SakuroColors.AccentSakura,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                Text(
                    "Content class",
                    style = MaterialTheme.typography.labelLarge,
                    color = SakuroColors.AccentLavender,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContentClass.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == contentClass,
                            onClick = { contentClass = candidate },
                            label = { Text(candidate.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.4f),
                                selectedLabelColor = SakuroColors.TextPrimary,
                                labelColor = SakuroColors.TextMuted,
                            ),
                        )
                    }
                }
                Text(
                    "mpv uses Anime4K shaders for Anime and Cartoon",
                    style = MaterialTheme.typography.bodySmall,
                    color = SakuroColors.TextMuted,
                )

                EditorSlider(
                    title = "Upscale",
                    valueText = if (upscale > UserPresetStore.UPSCALE_MIN) "×${formatMultiplier(upscale)}" else "off",
                    value = upscale,
                    range = UserPresetStore.UPSCALE_MIN..UserPresetStore.UPSCALE_MAX,
                    steps = UPSCALE_SLIDER_STEPS,
                    onChange = { upscale = it },
                )
                EditorSlider(
                    title = "Sharpness",
                    valueText = if (sharpen > 0f) formatPercent(sharpen) else "off",
                    value = sharpen,
                    range = 0f..1f,
                    onChange = { sharpen = it },
                )
                EditorSlider(
                    title = "Denoise",
                    valueText = if (denoise > 0f) formatPercent(denoise) else "off",
                    value = denoise,
                    range = 0f..1f,
                    onChange = { denoise = it },
                )

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = SakuroColors.TextMuted) }
                    TextButton(
                        onClick = {
                            val passes = buildList {
                                if (upscale > UserPresetStore.UPSCALE_MIN) add(UpscalePass.Upscale(upscale))
                                if (sharpen > 0f) add(UpscalePass.Sharpen(sharpen))
                                if (denoise > 0f) add(UpscalePass.Denoise(denoise))
                            }
                            onSave(
                                UpscaleProfile(
                                    id = initial?.id.orEmpty(),
                                    name = name,
                                    description = chainSummary(passes),
                                    contentClass = contentClass,
                                    passes = passes,
                                ),
                            )
                        },
                    ) {
                        Text("Save", color = SakuroColors.AccentSakura)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = SakuroColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(valueText, color = SakuroColors.AccentSakura, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = SakuroColors.AccentSakura,
                activeTrackColor = SakuroColors.GlowMagenta,
                inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
            ),
        )
    }
}

private val ContentClass.label: String
    get() = when (this) {
        ContentClass.ANIME -> "Anime"
        ContentClass.CARTOON -> "Cartoon"
        ContentClass.LIVE_ACTION -> "Live-action"
        ContentClass.UNKNOWN -> "Any"
    }

/** Human-readable chain description for preset lists. */
private fun chainSummary(passes: List<UpscalePass>): String =
    if (passes.isEmpty()) {
        "No processing"
    } else {
        passes.joinToString(" · ") { pass ->
            when (pass) {
                is UpscalePass.Upscale -> "upscale ×${formatMultiplier(pass.factor)}"
                is UpscalePass.Sharpen -> "sharpness ${formatPercent(pass.strength)}"
                is UpscalePass.Denoise -> "denoise ${formatPercent(pass.strength)}"
            }
        }
    }

private fun formatPercent(value: Float): String = "${(value * PERCENT).toInt()}%"

private const val PERCENT = 100

// 1x..4x with a 0.25 step gives 11 intermediate slider ticks.
private const val UPSCALE_SLIDER_STEPS = 11

// 0.5x..2x with a 0.25 step gives 5 intermediate slider ticks.
private const val SENSITIVITY_STEPS = 5

/** "1", "1.25" - multiplier without trailing zeroes for sensitivity and upscale factor. */
private fun formatMultiplier(value: Float): String {
    val rounded = (value * PERCENT).toInt()
    return if (rounded % PERCENT == 0) "${rounded / PERCENT}" else (rounded / PERCENT.toFloat()).toString()
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = SakuroColors.AccentLavender,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun EngineRow(type: EngineType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = SakuroColors.AccentSakura),
        )
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                type.displayName,
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            val hint = when (type) {
                EngineType.MEDIA3 -> "Native Android engine, upscale through a GlEffect chain"
                EngineType.MPV -> "libmpv: broad decoding, live presets without re-prepare"
                EngineType.FAKE -> "Mock for UI debugging without playback"
            }
            Text(hint, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
