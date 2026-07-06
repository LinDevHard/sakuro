package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles
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
import sakuro.composeapp.generated.resources.settings_custom_count
import sakuro.composeapp.generated.resources.settings_custom_presets
import sakuro.composeapp.generated.resources.settings_custom_presets_empty
import sakuro.composeapp.generated.resources.settings_debug_overlay
import sakuro.composeapp.generated.resources.settings_debug_overlay_desc
import sakuro.composeapp.generated.resources.settings_default_preset
import sakuro.composeapp.generated.resources.settings_engine
import sakuro.composeapp.generated.resources.settings_fixed_quality
import sakuro.composeapp.generated.resources.settings_player_gestures
import sakuro.composeapp.generated.resources.settings_player_gestures_desc
import sakuro.composeapp.generated.resources.settings_quality_mode
import sakuro.composeapp.generated.resources.settings_swipe_sensitivity
import sakuro.composeapp.generated.resources.settings_tab_advanced
import sakuro.composeapp.generated.resources.settings_tab_overview
import sakuro.composeapp.generated.resources.settings_tab_playback
import sakuro.composeapp.generated.resources.settings_tab_upscale
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

private val SakuraTeal = Color(0xFF63D7D2)

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
    var selectedTab by remember { mutableStateOf(SettingsTab.Overview) }
    val selectedPreset = presets.firstOrNull { it.id == presetId } ?: presets.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(SakuroColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsTopBar(onBack = component.onBack)
        SettingsStatusPanel(
            engineType = engineType,
            preset = selectedPreset,
            adaptiveEnabled = adaptiveEnabled,
            gesturesEnabled = gesturesEnabled,
            customPresetCount = userPresets.size,
        )
        SettingsTabBar(selected = selectedTab, onSelect = { selectedTab = it })

        when (selectedTab) {
            SettingsTab.Overview -> {
                OverviewContent(
                    engineType = engineType,
                    preset = selectedPreset,
                    adaptiveEnabled = adaptiveEnabled,
                    gesturesEnabled = gesturesEnabled,
                    gestureSensitivity = gestureSensitivity,
                    customPresetCount = userPresets.size,
                    onOpenPlayback = { selectedTab = SettingsTab.Playback },
                    onOpenUpscale = { selectedTab = SettingsTab.Upscale },
                    onOpenControls = { selectedTab = SettingsTab.Controls },
                    onOpenAdvanced = { selectedTab = SettingsTab.Advanced },
                )
            }

            SettingsTab.Playback -> {
                SettingsPanel(title = stringResource(Res.string.settings_engine), icon = Lucide.Film) {
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
                }
            }

            SettingsTab.Upscale -> {
                SettingsPanel(title = stringResource(Res.string.settings_default_preset), icon = Lucide.Sparkles) {
                    presets.forEach { preset ->
                        PresetChoiceRow(
                            preset = preset,
                            selected = preset.id == presetId,
                            onClick = { component.selectPreset(preset.id) },
                        )
                    }
                }
                CustomPresetsPanel(
                    userPresets = userPresets,
                    presetMessage = presetMessage,
                    onCreate = {
                        editorInitial = null
                        editorVisible = true
                    },
                    onImport = {
                        val raw = clipboard.getText()?.text.orEmpty()
                        presetMessage = component.importUserPreset(raw).fold(
                            onSuccess = { PresetMessage.Imported(it.name) },
                            onFailure = { PresetMessage.Invalid },
                        )
                    },
                    onEdit = { preset ->
                        editorInitial = preset
                        editorVisible = true
                    },
                    onExport = { preset ->
                        clipboard.setText(AnnotatedString(component.exportUserPreset(preset)))
                        presetMessage = PresetMessage.Copied(preset.name)
                    },
                    onDelete = { preset ->
                        component.deleteUserPreset(preset.id)
                        presetMessage = PresetMessage.Deleted(preset.name)
                    },
                )
            }

            SettingsTab.Controls -> {
                SettingsPanel(title = stringResource(Res.string.settings_controls), icon = Lucide.Activity) {
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_player_gestures),
                        subtitle = stringResource(Res.string.settings_player_gestures_desc),
                        checked = gesturesEnabled,
                        onCheckedChange = component::setGesturesEnabled,
                    )
                    SettingSliderRow(
                        title = stringResource(Res.string.settings_swipe_sensitivity),
                        valueText = "${formatMultiplier(gestureSensitivity)}×",
                        value = gestureSensitivity,
                        valueRange = SakuroSettings.SENSITIVITY_MIN..SakuroSettings.SENSITIVITY_MAX,
                        steps = SENSITIVITY_STEPS,
                        enabled = gesturesEnabled,
                        onValueChange = component::setGestureSensitivity,
                    )
                }
            }

            SettingsTab.Advanced -> {
                SettingsPanel(title = stringResource(Res.string.settings_quality_mode), icon = Lucide.Settings) {
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_adaptive_title),
                        subtitle = stringResource(Res.string.settings_adaptive_desc),
                        checked = adaptiveEnabled,
                        onCheckedChange = component::setAdaptiveEnabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_debug_overlay),
                        subtitle = stringResource(Res.string.settings_debug_overlay_desc),
                        checked = debugOverlay,
                        onCheckedChange = component::setDebugOverlay,
                    )
                }
                SettingsPanel(title = stringResource(Res.string.settings_about), icon = Lucide.Activity) {
                    Text(
                        stringResource(Res.string.settings_about_text),
                        color = SakuroColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
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

private enum class SettingsTab {
    Overview,
    Playback,
    Upscale,
    Controls,
    Advanced,
}

@Composable
private fun SettingsTab.label(): String = when (this) {
    SettingsTab.Overview -> stringResource(Res.string.settings_tab_overview)
    SettingsTab.Playback -> stringResource(Res.string.settings_tab_playback)
    SettingsTab.Upscale -> stringResource(Res.string.settings_tab_upscale)
    SettingsTab.Controls -> stringResource(Res.string.settings_controls)
    SettingsTab.Advanced -> stringResource(Res.string.settings_tab_advanced)
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ChevronLeft, stringResource(Res.string.action_back), tint = SakuroColors.TextPrimary)
        }
        Text(
            stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SakuroColors.TextPrimary,
        )
    }
}

@Composable
private fun SettingsStatusPanel(
    engineType: EngineType,
    preset: UpscaleProfile?,
    adaptiveEnabled: Boolean,
    gesturesEnabled: Boolean,
    customPresetCount: Int,
) {
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = SakuraTeal.copy(alpha = 0.18f), shape = CircleShape) {
                    Icon(
                        Lucide.Sparkles,
                        contentDescription = null,
                        tint = SakuraTeal,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        preset?.displayName() ?: stringResource(Res.string.value_off),
                        color = SakuroColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        preset?.displayDescription().orEmpty(),
                        color = SakuroColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = engineType.label(), accent = SakuroColors.AccentSakura, modifier = Modifier.weight(1f))
                StatusPill(
                    text = if (adaptiveEnabled) {
                        stringResource(Res.string.settings_quality_mode)
                    } else {
                        stringResource(Res.string.settings_fixed_quality)
                    },
                    accent = SakuraTeal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = stringResource(Res.string.settings_custom_count, customPresetCount),
                    accent = SakuroColors.AccentLavender,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = stringResource(Res.string.settings_player_gestures),
                    accent = if (gesturesEnabled) SakuroColors.GlowMagenta else SakuroColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier.height(38.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text,
                color = SakuroColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsTabBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsTab.entries.forEach { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = { Text(tab.label()) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SakuroColors.Surface,
                    selectedContainerColor = SakuroColors.AccentSakura.copy(alpha = 0.18f),
                    labelColor = SakuroColors.TextMuted,
                    selectedLabelColor = SakuroColors.TextPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = tab == selected,
                    borderColor = SakuroColors.Twilight.copy(alpha = 0.65f),
                    selectedBorderColor = SakuroColors.AccentSakura.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

@Composable
private fun OverviewContent(
    engineType: EngineType,
    preset: UpscaleProfile?,
    adaptiveEnabled: Boolean,
    gesturesEnabled: Boolean,
    gestureSensitivity: Float,
    customPresetCount: Int,
    onOpenPlayback: () -> Unit,
    onOpenUpscale: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewRow(
            icon = Lucide.Film,
            title = stringResource(Res.string.settings_tab_playback),
            value = engineType.label(),
            onClick = onOpenPlayback,
        )
        OverviewRow(
            icon = Lucide.Sparkles,
            title = stringResource(Res.string.settings_tab_upscale),
            value = preset?.displayName() ?: stringResource(Res.string.value_off),
            onClick = onOpenUpscale,
        )
        OverviewRow(
            icon = Lucide.Activity,
            title = stringResource(Res.string.settings_controls),
            value = if (gesturesEnabled) "${formatMultiplier(gestureSensitivity)}×" else stringResource(Res.string.value_off),
            onClick = onOpenControls,
        )
        OverviewRow(
            icon = Lucide.Settings,
            title = stringResource(Res.string.settings_tab_advanced),
            value = if (adaptiveEnabled) {
                stringResource(Res.string.settings_quality_mode)
            } else {
                stringResource(Res.string.settings_fixed_quality)
            },
            onClick = onOpenAdvanced,
        )
        OverviewRow(
            icon = Lucide.Folder,
            title = stringResource(Res.string.settings_custom_presets),
            value = stringResource(Res.string.settings_custom_count, customPresetCount),
            onClick = onOpenUpscale,
        )
    }
}

@Composable
private fun OverviewRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = SakuroColors.SurfaceElevated, shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = SakuroColors.AccentSakura, modifier = Modifier.padding(10.dp).size(18.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(value, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
                Text(
                    title,
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SakuroColors.AccentSakura,
                checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(valueText, color = SakuroColors.AccentSakura, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SakuroColors.AccentSakura,
                activeTrackColor = SakuroColors.GlowMagenta,
                inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun PresetChoiceRow(preset: UpscaleProfile, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (selected) SakuroColors.AccentSakura.copy(alpha = 0.18f) else SakuroColors.SurfaceElevated,
            shape = CircleShape,
            border = BorderStroke(1.dp, if (selected) SakuroColors.AccentSakura else SakuroColors.Twilight.copy(alpha = 0.6f)),
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Lucide.Check, contentDescription = null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Lucide.Sparkles, contentDescription = null, tint = SakuroColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(preset.displayName(), color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                preset.displayDescription(),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CustomPresetsPanel(
    userPresets: List<UpscaleProfile>,
    presetMessage: PresetMessage?,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onEdit: (UpscaleProfile) -> Unit,
    onExport: (UpscaleProfile) -> Unit,
    onDelete: (UpscaleProfile) -> Unit,
) {
    SettingsPanel(title = stringResource(Res.string.settings_custom_presets), icon = Lucide.Folder) {
        if (userPresets.isEmpty()) {
            Text(
                stringResource(Res.string.settings_custom_presets_empty),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        userPresets.forEach { preset ->
            UserPresetRow(
                preset = preset,
                onEdit = { onEdit(preset) },
                onExport = { onExport(preset) },
                onDelete = { onDelete(preset) },
            )
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onCreate,
                colors = ButtonDefaults.textButtonColors(contentColor = SakuroColors.AccentSakura),
            ) {
                Icon(Lucide.Plus, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.action_create))
            }
            TextButton(
                onClick = onImport,
                colors = ButtonDefaults.textButtonColors(contentColor = SakuroColors.AccentSakura),
            ) {
                Icon(Lucide.ClipboardPaste, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.action_from_clipboard))
            }
        }
        presetMessage?.let { message ->
            Text(
                message.text(),
                color = SakuroColors.AccentLavender,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
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
