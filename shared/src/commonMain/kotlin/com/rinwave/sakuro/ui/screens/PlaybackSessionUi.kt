package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.navigation.PlayerComponent
import com.rinwave.sakuro.playback.buildDiagnosticReport
import com.rinwave.sakuro.ui.rememberDiagnosticPlatform
import com.rinwave.sakuro.ui.rememberShareDiagnosticReport
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatTime
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_back
import sakuro.composeapp.generated.resources.session_autoplay
import sakuro.composeapp.generated.resources.session_close
import sakuro.composeapp.generated.resources.session_continue
import sakuro.composeapp.generated.resources.session_copied
import sakuro.composeapp.generated.resources.session_copy
import sakuro.composeapp.generated.resources.session_diagnostics
import sakuro.composeapp.generated.resources.session_diagnostics_hint
import sakuro.composeapp.generated.resources.session_error
import sakuro.composeapp.generated.resources.session_error_hint
import sakuro.composeapp.generated.resources.session_next
import sakuro.composeapp.generated.resources.session_previous
import sakuro.composeapp.generated.resources.session_processing_off
import sakuro.composeapp.generated.resources.session_restart
import sakuro.composeapp.generated.resources.session_resume
import sakuro.composeapp.generated.resources.session_resume_at
import sakuro.composeapp.generated.resources.session_retry_plain
import sakuro.composeapp.generated.resources.session_share
import sakuro.composeapp.generated.resources.session_tools

@Composable
internal fun PlaybackToolsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Lucide.Settings, stringResource(Res.string.session_tools), tint = SakuroColors.TextMuted)
    }
}

/** Modal choices are hidden in PiP by the caller. The report is captured once before preview. */
@Composable
internal fun PlaybackSessionUi(
    component: PlayerComponent,
    stats: DebugStats,
    presetId: String,
    toolsVisible: Boolean,
    onDismissTools: () -> Unit,
) {
    val state by component.engine.state.collectAsState()
    val resumePosition by component.resumePosition.collectAsState()
    val platform = rememberDiagnosticPlatform()
    var report by remember(component) { mutableStateOf<String?>(null) }
    val previewReport = {
        report = buildDiagnosticReport(stats, state.errorMessage, presetId, platform)
        onDismissTools()
    }
    when {
        report != null -> DiagnosticPreview(checkNotNull(report), onDismiss = { report = null })
        state.status == PlaybackStatus.ERROR -> PlaybackFailureDialog(component, previewReport)
        resumePosition != null -> ResumeDialog(checkNotNull(resumePosition), component)
        toolsVisible -> PlaybackToolsDialog(component, onDismissTools, previewReport)
    }
}

@Composable
private fun ResumeDialog(positionMs: Long, component: PlayerComponent) {
    AlertDialog(
        onDismissRequest = component::onBack,
        title = { Text(stringResource(Res.string.session_resume)) },
        text = { Text(stringResource(Res.string.session_resume_at, formatTime(positionMs))) },
        confirmButton = {
            TextButton(onClick = { component.chooseResume(true) }) {
                Text(stringResource(Res.string.session_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { component.chooseResume(false) }) {
                Text(stringResource(Res.string.session_restart))
            }
        },
    )
}

@Composable
private fun PlaybackToolsDialog(
    component: PlayerComponent,
    onDismiss: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val previous by component.previousVideo.collectAsState()
    val next by component.nextVideo.collectAsState()
    val autoPlay by component.autoPlayNext.collectAsState()
    val processingDisabled by component.processingDisabled.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.session_tools)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    onDismiss()
                    component.playPrevious()
                }, enabled = previous != null) {
                    Text(stringResource(Res.string.session_previous))
                }
                TextButton(onClick = {
                    onDismiss()
                    component.playNext()
                }, enabled = next != null) {
                    Text(stringResource(Res.string.session_next))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.session_autoplay), Modifier.weight(1f))
                    Switch(checked = autoPlay, onCheckedChange = component::setAutoPlayNext)
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        component.retryWithoutProcessing()
                    },
                    enabled = !processingDisabled,
                ) {
                    val label = if (processingDisabled) {
                        Res.string.session_processing_off
                    } else {
                        Res.string.session_retry_plain
                    }
                    Text(stringResource(label))
                }
                TextButton(onClick = onDiagnostics) { Text(stringResource(Res.string.session_diagnostics)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.session_close)) } },
    )
}

@Composable
private fun PlaybackFailureDialog(component: PlayerComponent, onDiagnostics: () -> Unit) {
    AlertDialog(
        onDismissRequest = component::onBack,
        title = { Text(stringResource(Res.string.session_error)) },
        text = {
            Column {
                Text(stringResource(Res.string.session_error_hint))
                TextButton(onClick = onDiagnostics) { Text(stringResource(Res.string.session_diagnostics)) }
            }
        },
        confirmButton = {
            TextButton(onClick = component::retryWithoutProcessing) {
                Text(stringResource(Res.string.session_retry_plain))
            }
        },
        dismissButton = {
            TextButton(onClick = component::onBack) { Text(stringResource(Res.string.action_back)) }
        },
    )
}

@Suppress("DEPRECATION")
@Composable
private fun DiagnosticPreview(report: String, onDismiss: () -> Unit) {
    val share = rememberShareDiagnosticReport()
    val clipboard = LocalClipboardManager.current
    var copied by remember(report) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.session_diagnostics)) },
        text = {
            Column {
                Text(stringResource(Res.string.session_diagnostics_hint))
                SelectionContainer {
                    Text(
                        report,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    copied = true
                }) {
                    Text(stringResource(if (copied) Res.string.session_copied else Res.string.session_copy))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { share(report) }) { Text(stringResource(Res.string.session_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.session_close)) } },
    )
}
