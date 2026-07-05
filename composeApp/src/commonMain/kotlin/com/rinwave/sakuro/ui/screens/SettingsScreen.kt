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
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserPresetStore
import com.rinwave.sakuro.navigation.SettingsComponent
import com.rinwave.sakuro.ui.displayDescription
import com.rinwave.sakuro.ui.displayName
import com.rinwave.sakuro.ui.formatMultiplier
import com.rinwave.sakuro.ui.formatPercent
import com.rinwave.sakuro.ui.hint
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_back
import sakuro.composeapp.generated.resources.action_cancel
import sakuro.composeapp.generated.resources.action_create
import sakuro.composeapp.generated.resources.action_delete
import sakuro.composeapp.generated.resources.action_edit
import sakuro.composeapp.generated.resources.action_export_clipboard
import sakuro.composeapp.generated.resources.action_from_clipboard
import sakuro.composeapp.generated.resources.action_save
import sakuro.composeapp.generated.resources.msg_copied
import sakuro.composeapp.generated.resources.msg_deleted
import sakuro.composeapp.generated.resources.msg_imported
import sakuro.composeapp.generated.resources.msg_invalid_preset
import sakuro.composeapp.generated.resources.msg_saved
import sakuro.composeapp.generated.resources.preset_content_class
import sakuro.composeapp.generated.resources.preset_content_class_hint
import sakuro.composeapp.generated.resources.preset_denoise
import sakuro.composeapp.generated.resources.preset_edit
import sakuro.composeapp.generated.resources.preset_name
import sakuro.composeapp.generated.resources.preset_new
import sakuro.composeapp.generated.resources.preset_sharpness
import sakuro.composeapp.generated.resources.preset_upscale
import sakuro.composeapp.generated.resources.settings_about
import sakuro.composeapp.generated.resources.settings_about_text
import sakuro.composeapp.generated.resources.settings_adaptive_desc
import sakuro.composeapp.generated.resources.settings_adaptive_title
import sakuro.composeapp.generated.resources.settings_controls
import sakuro.composeapp.generated.resources.settings_custom_presets
import sakuro.composeapp.generated.resources.settings_custom_presets_empty
import sakuro.composeapp.generated.resources.settings_debug
import sakuro.composeapp.generated.resources.settings_debug_overlay
import sakuro.composeapp.generated.resources.settings_debug_overlay_desc
import sakuro.composeapp.generated.resources.settings_default_preset
import sakuro.composeapp.generated.resources.settings_engine
import sakuro.composeapp.generated.resources.settings_player_gestures
import sakuro.composeapp.generated.resources.settings_player_gestures_desc
import sakuro.composeapp.generated.resources.settings_swipe_sensitivity
import sakuro.composeapp.generated.resources.settings_title
import sakuro.composeapp.generated.resources.value_off

/** A pending status message shown under the custom-presets section, resolved to text in composition. */
private sealed interface PresetMessage {
    data class Imported(val name: String) : PresetMessage
    data class Copied(val name: String) : PresetMessage
    data class Deleted(val name: String) : PresetMessage
    data class Saved(val name: String) : PresetMessage
    data object Invalid : PresetMessage
}

@Composable
private fun PresetMessage.text(): String = when (this) {
    is PresetMessage.Imported -> stringResource(Res.string.msg_imported, name)
    is PresetMessage.Copied -> stringResource(Res.string.msg_copied, name)
    is PresetMessage.Deleted -> stringResource(Res.string.msg_deleted, name)
    is PresetMessage.Saved -> stringResource(Res.string.msg_saved, name)
    PresetMessage.Invalid -> stringResource(Res.string.msg_invalid_preset)
}

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
    var presetMessage by remember { mutableStateOf<PresetMessage?>(null) }

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
                Icon(Lucide.ChevronLeft, stringResource(Res.string.action_back), tint = SakuroColors.TextPrimary)
            }
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = SakuroColors.TextPrimary,
            )
        }

        SectionTitle(stringResource(Res.string.settings_engine))
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

        SectionTitle(stringResource(Res.string.settings_default_preset))
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
                    Text(
                        preset.displayName(),
                        color = SakuroColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        preset.displayDescription(),
                        color = SakuroColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle(stringResource(Res.string.settings_custom_presets))
        if (userPresets.isEmpty()) {
            Text(
                stringResource(Res.string.settings_custom_presets_empty),
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
                    presetMessage = PresetMessage.Copied(preset.name)
                },
                onDelete = {
                    component.deleteUserPreset(preset.id)
                    presetMessage = PresetMessage.Deleted(preset.name)
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
                Text(stringResource(Res.string.action_create), color = SakuroColors.AccentSakura)
            }
            TextButton(
                onClick = {
                    val raw = clipboard.getText()?.text.orEmpty()
                    presetMessage = component.importUserPreset(raw).fold(
                        onSuccess = { PresetMessage.Imported(it.name) },
                        onFailure = { PresetMessage.Invalid },
                    )
                },
            ) {
                Icon(Lucide.ClipboardPaste, null, Modifier.size(16.dp), tint = SakuroColors.AccentSakura)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.action_from_clipboard), color = SakuroColors.AccentSakura)
            }
        }
        presetMessage?.let { message ->
            Text(
                message.text(),
                color = SakuroColors.AccentLavender,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle(stringResource(Res.string.settings_controls))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setGesturesEnabled(!gesturesEnabled) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_player_gestures),
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.settings_player_gestures_desc),
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
                    stringResource(Res.string.settings_swipe_sensitivity),
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

        SectionTitle(stringResource(Res.string.settings_debug))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setAdaptiveEnabled(!adaptiveEnabled) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_adaptive_title),
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.settings_adaptive_desc),
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
                Text(
                    stringResource(Res.string.settings_debug_overlay),
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.settings_debug_overlay_desc),
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

        SectionTitle(stringResource(Res.string.settings_about))
        Text(
            stringResource(Res.string.settings_about_text),
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
                presetMessage = PresetMessage.Saved(saved.name)
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
            Text(preset.displayName(), color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                preset.displayDescription(),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Lucide.Pencil, stringResource(Res.string.action_edit), Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
        IconButton(onClick = onExport) {
            Icon(
                Lucide.Copy,
                stringResource(Res.string.action_export_clipboard),
                Modifier.size(18.dp),
                tint = SakuroColors.TextMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Lucide.Trash2, stringResource(Res.string.action_delete), Modifier.size(18.dp), tint = SakuroColors.TextMuted)
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
    val offLabel = stringResource(Res.string.value_off)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SakuroColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (initial == null) {
                        stringResource(Res.string.preset_new)
                    } else {
                        stringResource(Res.string.preset_edit)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = SakuroColors.TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.preset_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SakuroColors.AccentSakura,
                        cursorColor = SakuroColors.AccentSakura,
                        focusedLabelColor = SakuroColors.AccentSakura,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                Text(
                    stringResource(Res.string.preset_content_class),
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
                            label = { Text(candidate.label()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.4f),
                                selectedLabelColor = SakuroColors.TextPrimary,
                                labelColor = SakuroColors.TextMuted,
                            ),
                        )
                    }
                }
                Text(
                    stringResource(Res.string.preset_content_class_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SakuroColors.TextMuted,
                )

                EditorSlider(
                    title = stringResource(Res.string.preset_upscale),
                    valueText = if (upscale > UserPresetStore.UPSCALE_MIN) "×${formatMultiplier(upscale)}" else offLabel,
                    value = upscale,
                    range = UserPresetStore.UPSCALE_MIN..UserPresetStore.UPSCALE_MAX,
                    steps = UPSCALE_SLIDER_STEPS,
                    onChange = { upscale = it },
                )
                EditorSlider(
                    title = stringResource(Res.string.preset_sharpness),
                    valueText = if (sharpen > 0f) formatPercent(sharpen) else offLabel,
                    value = sharpen,
                    range = 0f..1f,
                    onChange = { sharpen = it },
                )
                EditorSlider(
                    title = stringResource(Res.string.preset_denoise),
                    valueText = if (denoise > 0f) formatPercent(denoise) else offLabel,
                    value = denoise,
                    range = 0f..1f,
                    onChange = { denoise = it },
                )

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel), color = SakuroColors.TextMuted)
                    }
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
                        Text(stringResource(Res.string.action_save), color = SakuroColors.AccentSakura)
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

/** Stored (non-localized) technical summary used for export; the UI shows [displayDescription]. */
private fun chainSummary(passes: List<UpscalePass>): String =
    if (passes.isEmpty()) {
        "none"
    } else {
        passes.joinToString(" · ") { pass ->
            when (pass) {
                is UpscalePass.Upscale -> "upscale ×${formatMultiplier(pass.factor)}"
                is UpscalePass.Sharpen -> "sharpness ${formatPercent(pass.strength)}"
                is UpscalePass.Denoise -> "denoise ${formatPercent(pass.strength)}"
            }
        }
    }

// 1x..4x with a 0.25 step gives 11 intermediate slider ticks.
private const val UPSCALE_SLIDER_STEPS = 11

// 0.5x..2x with a 0.25 step gives 5 intermediate slider ticks.
private const val SENSITIVITY_STEPS = 5

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
                type.label(),
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(type.hint(), color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
