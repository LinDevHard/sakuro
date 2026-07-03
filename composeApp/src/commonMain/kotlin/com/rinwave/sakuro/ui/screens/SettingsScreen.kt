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
                Icon(Lucide.ChevronLeft, "Назад", tint = SakuroColors.TextPrimary)
            }
            Text("Настройки", style = MaterialTheme.typography.titleLarge, color = SakuroColors.TextPrimary)
        }

        SectionTitle("Движок воспроизведения")
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

        SectionTitle("Пресет апскейла по умолчанию")
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

        SectionTitle("Свои пресеты")
        if (userPresets.isEmpty()) {
            Text(
                "Пока нет своих пресетов — создайте или вставьте из буфера обмена.",
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
                    presetMessage = "«${preset.name}» скопирован в буфер обмена"
                },
                onDelete = {
                    component.deleteUserPreset(preset.id)
                    presetMessage = "«${preset.name}» удалён"
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
                Text("Создать", color = SakuroColors.AccentSakura)
            }
            TextButton(
                onClick = {
                    val raw = clipboard.getText()?.text.orEmpty()
                    presetMessage = component.importUserPreset(raw).fold(
                        onSuccess = { "Импортирован «${it.name}»" },
                        onFailure = { "В буфере обмена нет корректного пресета" },
                    )
                },
            ) {
                Icon(Lucide.ClipboardPaste, null, Modifier.size(16.dp), tint = SakuroColors.AccentSakura)
                Spacer(Modifier.size(6.dp))
                Text("Из буфера", color = SakuroColors.AccentSakura)
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

        SectionTitle("Управление")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setGesturesEnabled(!gesturesEnabled) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Жесты в плеере", color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Свайпы: яркость/громкость/перемотка; пинч: режим кадра",
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
                    "Чувствительность свайпов",
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

        SectionTitle("Отладка")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setDebugOverlay(!debugOverlay) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Debug-оверлей", color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "«Stats for nerds» поверх плеера",
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

        SectionTitle("О приложении")
        Text(
            "Sakuro 0.1.0 — видеоплеер с реалтайм-апскейлом.\nOpen source (GPLv3), by Rinwave.",
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
                presetMessage = "Сохранён «${saved.name}»"
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
            Icon(Lucide.Pencil, "Изменить", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
        IconButton(onClick = onExport) {
            Icon(Lucide.Copy, "Экспорт в буфер обмена", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
        IconButton(onClick = onDelete) {
            Icon(Lucide.Trash2, "Удалить", Modifier.size(18.dp), tint = SakuroColors.TextMuted)
        }
    }
}

/** Редактор пресета (FEATURES.md §2.2): имя, класс контента, цепочка из трёх проходов. */
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
                    if (initial == null) "Новый пресет" else "Изменить пресет",
                    style = MaterialTheme.typography.titleMedium,
                    color = SakuroColors.TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SakuroColors.AccentSakura,
                        cursorColor = SakuroColors.AccentSakura,
                        focusedLabelColor = SakuroColors.AccentSakura,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                Text(
                    "Класс контента",
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
                    "Для «Аниме» и «Мультфильм» движок mpv применяет Anime4K-шейдеры",
                    style = MaterialTheme.typography.bodySmall,
                    color = SakuroColors.TextMuted,
                )

                EditorSlider(
                    title = "Апскейл",
                    valueText = if (upscale > UserPresetStore.UPSCALE_MIN) "×${formatMultiplier(upscale)}" else "выкл",
                    value = upscale,
                    range = UserPresetStore.UPSCALE_MIN..UserPresetStore.UPSCALE_MAX,
                    steps = UPSCALE_SLIDER_STEPS,
                    onChange = { upscale = it },
                )
                EditorSlider(
                    title = "Резкость",
                    valueText = if (sharpen > 0f) formatPercent(sharpen) else "выкл",
                    value = sharpen,
                    range = 0f..1f,
                    onChange = { sharpen = it },
                )
                EditorSlider(
                    title = "Деноиз",
                    valueText = if (denoise > 0f) formatPercent(denoise) else "выкл",
                    value = denoise,
                    range = 0f..1f,
                    onChange = { denoise = it },
                )

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена", color = SakuroColors.TextMuted) }
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
                        Text("Сохранить", color = SakuroColors.AccentSakura)
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
        ContentClass.ANIME -> "Аниме"
        ContentClass.CARTOON -> "Мультфильм"
        ContentClass.LIVE_ACTION -> "Live-action"
        ContentClass.UNKNOWN -> "Любой"
    }

/** Читаемое описание цепочки для списков пресетов. */
private fun chainSummary(passes: List<UpscalePass>): String =
    if (passes.isEmpty()) {
        "Без обработки"
    } else {
        passes.joinToString(" · ") { pass ->
            when (pass) {
                is UpscalePass.Upscale -> "апскейл ×${formatMultiplier(pass.factor)}"
                is UpscalePass.Sharpen -> "резкость ${formatPercent(pass.strength)}"
                is UpscalePass.Denoise -> "деноиз ${formatPercent(pass.strength)}"
            }
        }
    }

private fun formatPercent(value: Float): String = "${(value * PERCENT).toInt()}%"

private const val PERCENT = 100

// 1×..4× с шагом 0.25 → 11 промежуточных делений слайдера.
private const val UPSCALE_SLIDER_STEPS = 11

// 0.5×..2× с шагом 0.25 → 5 промежуточных делений слайдера.
private const val SENSITIVITY_STEPS = 5

/** «1», «1.25» — множитель без хвостовых нулей (чувствительность, фактор апскейла). */
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
                EngineType.MEDIA3 -> "Нативный Android-движок, апскейл через GlEffect-цепочку"
                EngineType.MPV -> "libmpv: широкий декод, пресеты на лету без re-prepare"
                EngineType.FAKE -> "Мок для отладки интерфейса без воспроизведения"
            }
            Text(hint, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
