package com.rinwave.sakuro.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.model.StreamHeaders
import com.rinwave.sakuro.core.model.StreamUrl
import com.rinwave.sakuro.feature.library.resources.Res
import com.rinwave.sakuro.feature.library.resources.stream_advanced
import com.rinwave.sakuro.feature.library.resources.stream_advanced_hint
import com.rinwave.sakuro.feature.library.resources.stream_cancel
import com.rinwave.sakuro.feature.library.resources.stream_cookie
import com.rinwave.sakuro.feature.library.resources.stream_error_empty
import com.rinwave.sakuro.feature.library.resources.stream_error_malformed
import com.rinwave.sakuro.feature.library.resources.stream_error_scheme
import com.rinwave.sakuro.feature.library.resources.stream_hint
import com.rinwave.sakuro.feature.library.resources.stream_label
import com.rinwave.sakuro.feature.library.resources.stream_play
import com.rinwave.sakuro.feature.library.resources.stream_referer
import com.rinwave.sakuro.feature.library.resources.stream_title
import com.rinwave.sakuro.feature.library.resources.stream_user_agent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A direct link to an HLS/DASH playlist or a progressive file; prefilled from the clipboard. */
@Suppress("DEPRECATION")
@Composable
internal fun OpenStreamDialog(onPlay: (StreamUrl, Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var field by remember {
        val clip = runCatching { clipboard.getText()?.text }.getOrNull()?.trim().orEmpty()
        val prefill = clip.takeIf { StreamUrl.parse(it) is StreamUrl.ParseResult.Valid && "://" in it }.orEmpty()
        mutableStateOf(TextFieldValue(prefill, TextRange(0, prefill.length)))
    }
    var error by remember { mutableStateOf<StreamUrl.ParseResult?>(null) }
    var advancedOpen by remember { mutableStateOf(false) }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val submit = {
        when (val result = StreamUrl.parse(field.text)) {
            is StreamUrl.ParseResult.Valid -> onPlay(
                result.url,
                mapOf(
                    StreamHeaders.USER_AGENT to userAgent,
                    StreamHeaders.REFERER to referer,
                    StreamHeaders.COOKIE to cookie,
                ),
            )
            else -> error = result
        }
    }
    val errorText = when (error) {
        StreamUrl.ParseResult.Empty -> stringResource(Res.string.stream_error_empty)
        StreamUrl.ParseResult.UnsupportedScheme -> stringResource(Res.string.stream_error_scheme)
        StreamUrl.ParseResult.Malformed -> stringResource(Res.string.stream_error_malformed)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stream_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Res.string.stream_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SakuroColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.stream_label)) },
                    placeholder = { Text("https://…/master.m3u8") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                TextButton(onClick = { advancedOpen = !advancedOpen }) {
                    Text(stringResource(Res.string.stream_advanced), color = SakuroColors.TextMuted)
                    Icon(
                        if (advancedOpen) Lucide.ChevronUp else Lucide.ChevronDown,
                        null,
                        tint = SakuroColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                AnimatedVisibility(advancedOpen) {
                    Column {
                        Text(
                            stringResource(Res.string.stream_advanced_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = SakuroColors.TextMuted,
                        )
                        HeaderField(userAgent, { userAgent = it }, Res.string.stream_user_agent, "Mozilla/5.0 …")
                        HeaderField(referer, { referer = it }, Res.string.stream_referer, "https://example.com/")
                        HeaderField(cookie, { cookie = it }, Res.string.stream_cookie, "name=value; other=value")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit) { Text(stringResource(Res.string.stream_play)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.stream_cancel)) }
        },
    )
}

@Composable
private fun HeaderField(value: String, onChange: (String) -> Unit, label: StringResource, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.replace("\n", "").replace("\r", "")) },
        label = { Text(stringResource(label)) },
        placeholder = { Text(placeholder, color = SakuroColors.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
