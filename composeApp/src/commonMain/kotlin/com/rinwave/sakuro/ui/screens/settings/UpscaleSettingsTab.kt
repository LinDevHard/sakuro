package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.rinwave.sakuro.core.upscale.BundledShader
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserPresetStore
import com.rinwave.sakuro.navigation.UpscaleSettingsComponent
import com.rinwave.sakuro.ui.displayDescription
import com.rinwave.sakuro.ui.displayName
import com.rinwave.sakuro.ui.formatMultiplier
import com.rinwave.sakuro.ui.formatPercent
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.rememberShaderFilePicker
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_cancel
import sakuro.composeapp.generated.resources.action_create
import sakuro.composeapp.generated.resources.action_delete
import sakuro.composeapp.generated.resources.action_edit
import sakuro.composeapp.generated.resources.action_export_clipboard
import sakuro.composeapp.generated.resources.action_from_clipboard
import sakuro.composeapp.generated.resources.action_import_file
import sakuro.composeapp.generated.resources.action_save
import sakuro.composeapp.generated.resources.msg_copied
import sakuro.composeapp.generated.resources.msg_deleted
import sakuro.composeapp.generated.resources.msg_imported
import sakuro.composeapp.generated.resources.msg_invalid_preset
import sakuro.composeapp.generated.resources.msg_saved
import sakuro.composeapp.generated.resources.msg_shader_imported
import sakuro.composeapp.generated.resources.msg_shader_rejected
import sakuro.composeapp.generated.resources.preset_content_class
import sakuro.composeapp.generated.resources.preset_content_class_hint
import sakuro.composeapp.generated.resources.preset_denoise
import sakuro.composeapp.generated.resources.preset_edit
import sakuro.composeapp.generated.resources.preset_name
import sakuro.composeapp.generated.resources.preset_new
import sakuro.composeapp.generated.resources.preset_shader_chain
import sakuro.composeapp.generated.resources.preset_shader_chain_hint
import sakuro.composeapp.generated.resources.preset_sharpness
import sakuro.composeapp.generated.resources.preset_upscale
import sakuro.composeapp.generated.resources.settings_custom_presets
import sakuro.composeapp.generated.resources.settings_custom_presets_empty
import sakuro.composeapp.generated.resources.settings_default_preset
import sakuro.composeapp.generated.resources.settings_shaders
import sakuro.composeapp.generated.resources.settings_shaders_empty
import sakuro.composeapp.generated.resources.shader_imported
import sakuro.composeapp.generated.resources.value_off

/** A pending status message shown under the custom-presets section, resolved to text in composition. */
private sealed interface PresetMessage {
    data class Imported(val name: String) : PresetMessage
    data class Copied(val name: String) : PresetMessage
    data class Deleted(val name: String) : PresetMessage
    data class Saved(val name: String) : PresetMessage
    data object Invalid : PresetMessage
    data class ShaderImported(val name: String) : PresetMessage
    data class ShaderRejected(val reason: String) : PresetMessage
}

@Composable
private fun PresetMessage.text(): String = when (this) {
    is PresetMessage.Imported -> stringResource(Res.string.msg_imported, name)
    is PresetMessage.Copied -> stringResource(Res.string.msg_copied, name)
    is PresetMessage.Deleted -> stringResource(Res.string.msg_deleted, name)
    is PresetMessage.Saved -> stringResource(Res.string.msg_saved, name)
    PresetMessage.Invalid -> stringResource(Res.string.msg_invalid_preset)
    is PresetMessage.ShaderImported -> stringResource(Res.string.msg_shader_imported, name)
    is PresetMessage.ShaderRejected -> stringResource(Res.string.msg_shader_rejected, reason)
}

@Composable
internal fun UpscaleSettingsTab(component: UpscaleSettingsComponent) {
    val presetId by component.presetId.collectAsState()
    val presets by component.presets.collectAsState()
    val userPresets by component.userPresetList.collectAsState()
    val userShaders by component.userShaders.collectAsState()

    val clipboard = LocalClipboardManager.current
    var editorInitial by remember { mutableStateOf<UpscaleProfile?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var presetMessage by remember { mutableStateOf<PresetMessage?>(null) }

    SettingsPanel(title = stringResource(Res.string.settings_default_preset), icon = Lucide.Sparkles) {
        presets.forEachIndexed { index, preset ->
            if (index > 0) PanelDivider()
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
    var shaderMessage by remember { mutableStateOf<PresetMessage?>(null) }
    UserShadersPanel(
        shaders = userShaders,
        message = shaderMessage,
        onImport = { name, content ->
            shaderMessage = component.importShader(name, content).fold(
                onSuccess = { PresetMessage.ShaderImported(it) },
                onFailure = { PresetMessage.ShaderRejected(it.message.orEmpty()) },
            )
        },
        onDelete = { component.deleteShader(it) },
    )

    if (editorVisible) {
        PresetEditorDialog(
            initial = editorInitial,
            importedShaders = userShaders,
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
            border = BorderStroke(
                1.dp,
                if (selected) SakuroColors.AccentSakura else SakuroColors.Twilight.copy(alpha = 0.6f),
            ),
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (selected) Lucide.Check else Lucide.Sparkles,
                    contentDescription = null,
                    tint = if (selected) SakuroColors.AccentSakura else SakuroColors.TextMuted,
                    modifier = Modifier.size(if (selected) 18.dp else 16.dp),
                )
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
    SettingsPanel(
        title = stringResource(Res.string.settings_custom_presets),
        icon = Lucide.Folder,
        accent = SakuroColors.AccentLavender,
    ) {
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

/** Imported mpv user-shader files: list, delete, and SAF import. */
@Composable
private fun UserShadersPanel(
    shaders: List<String>,
    message: PresetMessage?,
    onImport: (name: String, content: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val pickShader = rememberShaderFilePicker(onImport)
    SettingsPanel(
        title = stringResource(Res.string.settings_shaders),
        icon = Lucide.Layers,
        accent = SakuroColors.AccentLavender,
    ) {
        if (shaders.isEmpty()) {
            Text(
                stringResource(Res.string.settings_shaders_empty),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        shaders.forEach { name ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(name) }) {
                    Icon(
                        Lucide.Trash2,
                        stringResource(Res.string.action_delete),
                        Modifier.size(18.dp),
                        tint = SakuroColors.TextMuted,
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            TextButton(
                onClick = pickShader,
                colors = ButtonDefaults.textButtonColors(contentColor = SakuroColors.AccentSakura),
            ) {
                Icon(Lucide.Plus, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.action_import_file))
            }
        }
        message?.let {
            Text(
                it.text(),
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
            Icon(
                Lucide.Pencil,
                stringResource(Res.string.action_edit),
                Modifier.size(18.dp),
                tint = SakuroColors.TextMuted,
            )
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
            Icon(
                Lucide.Trash2,
                stringResource(Res.string.action_delete),
                Modifier.size(18.dp),
                tint = SakuroColors.TextMuted,
            )
        }
    }
}

/** Preset editor: name, content class, and a three-pass chain. */
@Composable
private fun PresetEditorDialog(
    initial: UpscaleProfile?,
    importedShaders: List<String>,
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
    var shaderChain by remember { mutableStateOf(initial?.shaderChain ?: emptyList()) }
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

                val upscaleText = if (upscale > UserPresetStore.UPSCALE_MIN) {
                    "×${formatMultiplier(upscale)}"
                } else {
                    offLabel
                }
                EditorSlider(
                    title = stringResource(Res.string.preset_upscale),
                    valueText = upscaleText,
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

                ShaderChainEditor(
                    importedShaders = importedShaders,
                    chain = shaderChain,
                    onToggle = { name ->
                        shaderChain = if (name in shaderChain) shaderChain - name else shaderChain + name
                    },
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
                                    description = chainSummary(passes, shaderChain),
                                    contentClass = contentClass,
                                    passes = passes,
                                    shaderChain = shaderChain,
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

/** Ordered selection of bundled and imported shaders: a tap appends/removes, the number shows the order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShaderChainEditor(
    importedShaders: List<String>,
    chain: List<String>,
    onToggle: (String) -> Unit,
) {
    Text(
        stringResource(Res.string.preset_shader_chain),
        style = MaterialTheme.typography.labelLarge,
        color = SakuroColors.AccentLavender,
        modifier = Modifier.padding(top = 16.dp),
    )
    // Imports first (an import shadows a bundled name), then the vendored set;
    // deleted files referenced by the preset stay visible so they can be unpicked.
    val bundled = BundledShaders.all.map { it.fileName }
    val names = importedShaders +
        bundled.filterNot { it in importedShaders } +
        chain.filterNot { it in importedShaders || it in bundled }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            ShaderChip(
                name = name,
                order = chain.indexOf(name),
                // An import shadows the bundled shader — its badges would lie.
                bundled = if (name in importedShaders) null else BundledShaders.byFileName(name),
                onToggle = onToggle,
            )
        }
    }
    Text(
        stringResource(Res.string.preset_shader_chain_hint),
        style = MaterialTheme.typography.bodySmall,
        color = SakuroColors.TextMuted,
    )
}

/** A two-line chip: the shader name over its badges (role · cost · content · ES tier). */
@Composable
private fun ShaderChip(
    name: String,
    order: Int,
    bundled: BundledShader?,
    onToggle: (String) -> Unit,
) {
    val title = bundled?.displayName ?: name
    val badges = if (bundled == null) {
        stringResource(Res.string.shader_imported)
    } else {
        buildList {
            add(bundled.role.label())
            add(bundled.cost.label())
            bundled.contentClasses?.firstOrNull()?.let { add(it.label()) }
            if (bundled.requiresEs31) add("ES 3.1")
        }.joinToString(" · ")
    }
    FilterChip(
        selected = order >= 0,
        onClick = { onToggle(name) },
        modifier = Modifier.height(52.dp),
        label = {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    if (order >= 0) "${order + 1}. $title" else title,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    badges,
                    style = MaterialTheme.typography.labelSmall,
                    color = SakuroColors.TextMuted,
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.4f),
            selectedLabelColor = SakuroColors.TextPrimary,
            labelColor = SakuroColors.TextMuted,
        ),
    )
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
private fun chainSummary(passes: List<UpscalePass>, shaderChain: List<String> = emptyList()): String {
    if (shaderChain.isNotEmpty()) return shaderChain.joinToString(" → ")
    return if (passes.isEmpty()) {
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
}

// 1x..4x with a 0.25 step gives 11 intermediate slider ticks.
private const val UPSCALE_SLIDER_STEPS = 11
