package com.rinwave.sakuro.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.FileX
import com.composables.icons.lucide.Link2Off
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ServerCrash
import com.composables.icons.lucide.ShieldAlert
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.WifiOff
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.player.PlaybackError
import com.rinwave.sakuro.core.player.PlaybackErrorKind
import com.rinwave.sakuro.feature.player.resources.Res
import com.rinwave.sakuro.feature.player.resources.action_back
import com.rinwave.sakuro.feature.player.resources.session_copy_link
import com.rinwave.sakuro.feature.player.resources.session_diagnostics
import com.rinwave.sakuro.feature.player.resources.session_error
import com.rinwave.sakuro.feature.player.resources.session_error_401_hint
import com.rinwave.sakuro.feature.player.resources.session_error_401_title
import com.rinwave.sakuro.feature.player.resources.session_error_403_hint
import com.rinwave.sakuro.feature.player.resources.session_error_403_title
import com.rinwave.sakuro.feature.player.resources.session_error_404_hint
import com.rinwave.sakuro.feature.player.resources.session_error_404_title
import com.rinwave.sakuro.feature.player.resources.session_error_drm_hint
import com.rinwave.sakuro.feature.player.resources.session_error_drm_title
import com.rinwave.sakuro.feature.player.resources.session_error_format_hint
import com.rinwave.sakuro.feature.player.resources.session_error_format_title
import com.rinwave.sakuro.feature.player.resources.session_error_hint
import com.rinwave.sakuro.feature.player.resources.session_error_insecure_hint
import com.rinwave.sakuro.feature.player.resources.session_error_insecure_title
import com.rinwave.sakuro.feature.player.resources.session_error_link_hint
import com.rinwave.sakuro.feature.player.resources.session_error_link_title
import com.rinwave.sakuro.feature.player.resources.session_error_network_hint
import com.rinwave.sakuro.feature.player.resources.session_error_network_title
import com.rinwave.sakuro.feature.player.resources.session_error_server_hint
import com.rinwave.sakuro.feature.player.resources.session_error_server_title
import com.rinwave.sakuro.feature.player.resources.session_link_copied
import com.rinwave.sakuro.feature.player.resources.session_live_ended_hint
import com.rinwave.sakuro.feature.player.resources.session_live_ended_title
import com.rinwave.sakuro.feature.player.resources.session_live_replay
import com.rinwave.sakuro.feature.player.resources.session_retry
import com.rinwave.sakuro.feature.player.resources.session_retry_plain
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A full-screen failure state instead of a dialog: an illustration for the kind of failure,
 * plain-language explanation and the actions that can actually help.
 */
@Suppress("DEPRECATION")
@Composable
internal fun PlaybackErrorScreen(
    component: PlayerComponent,
    error: PlaybackError?,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = failureCopy(error)
    val clipboard = LocalClipboardManager.current
    var copied by remember(error) { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(SakuroColors.Twilight.copy(alpha = 0.55f), SakuroColors.Background),
                    radius = 1400f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = component::onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(Lucide.ArrowLeft, null, tint = SakuroColors.TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(Res.string.action_back), color = SakuroColors.TextPrimary)
        }
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorIllustration(copy.icon, badge = error?.httpStatus?.toString())
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(copy.title),
                style = MaterialTheme.typography.headlineSmall,
                color = SakuroColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(copy.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SakuroColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            when (copy.action) {
                FailureAction.RETRY -> PrimaryAction(Res.string.session_retry, component::retry)
                FailureAction.RETRY_WITHOUT_PROCESSING ->
                    PrimaryAction(Res.string.session_retry_plain, component::retryWithoutProcessing)
                FailureAction.NONE -> PrimaryAction(Res.string.action_back, component::onBack, Lucide.ArrowLeft)
            }
            if (component.isRemote) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(component.media.uri))
                        copied = true
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Lucide.Copy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (copied) Res.string.session_link_copied else Res.string.session_copy_link))
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDiagnostics) {
                Text(stringResource(Res.string.session_diagnostics), color = SakuroColors.TextMuted)
            }
        }
    }
}

/** An event stream reached its announced end: say so instead of a frozen last frame. */
@Composable
internal fun LiveEndedScreen(component: PlayerComponent, canReplay: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(SakuroColors.Twilight.copy(alpha = 0.55f), SakuroColors.Background),
                    radius = 1400f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorIllustration(Lucide.Radio, badge = null)
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(Res.string.session_live_ended_title),
                style = MaterialTheme.typography.headlineSmall,
                color = SakuroColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(Res.string.session_live_ended_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SakuroColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            PrimaryAction(Res.string.action_back, component::onBack, Lucide.ArrowLeft)
            if (canReplay) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { component.engine.play() },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Lucide.RefreshCw, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.session_live_replay))
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: StringResource, onClick: () -> Unit, icon: ImageVector = Lucide.RefreshCw) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = SakuroColors.AccentSakura,
            contentColor = SakuroColors.Background,
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(label), fontWeight = FontWeight.SemiBold)
    }
}

/** A glowing sakura disc with the failure's icon and, for HTTP answers, the status code. */
@Composable
private fun ErrorIllustration(icon: ImageVector, badge: String?) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(176.dp)
                .background(
                    Brush.radialGradient(
                        listOf(SakuroColors.GlowMagenta.copy(alpha = 0.35f), SakuroColors.Background.copy(alpha = 0f)),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(112.dp)
                .background(
                    Brush.linearGradient(listOf(SakuroColors.SurfaceElevated, SakuroColors.Surface)),
                    CircleShape,
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(SakuroColors.AccentSakura, SakuroColors.AccentLavender)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(48.dp))
        }
        if (badge != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .background(SakuroColors.Background, RoundedCornerShape(50))
                    .border(1.dp, SakuroColors.AccentSakura.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "HTTP $badge",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = SakuroColors.TextPrimary,
                )
            }
        }
    }
}

private enum class FailureAction { RETRY, RETRY_WITHOUT_PROCESSING, NONE }

private data class FailureCopy(
    val icon: ImageVector,
    val title: StringResource,
    val hint: StringResource,
    val action: FailureAction,
)

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410

/** DRM and dead links get no retry: retrying cannot help and only confuses. */
private fun failureCopy(error: PlaybackError?): FailureCopy = when (error?.kind) {
    PlaybackErrorKind.DRM -> FailureCopy(
        Lucide.Lock, Res.string.session_error_drm_title, Res.string.session_error_drm_hint, FailureAction.NONE,
    )
    PlaybackErrorKind.NETWORK -> FailureCopy(
        Lucide.WifiOff, Res.string.session_error_network_title, Res.string.session_error_network_hint,
        FailureAction.RETRY,
    )
    PlaybackErrorKind.SERVER -> FailureCopy(
        Lucide.ServerCrash, Res.string.session_error_server_title, Res.string.session_error_server_hint,
        FailureAction.RETRY,
    )
    PlaybackErrorKind.HTTP_CLIENT -> when (error.httpStatus) {
        HTTP_NOT_FOUND, HTTP_GONE -> FailureCopy(
            Lucide.Link2Off, Res.string.session_error_404_title, Res.string.session_error_404_hint, FailureAction.NONE,
        )
        HTTP_FORBIDDEN -> FailureCopy(
            Lucide.Ban, Res.string.session_error_403_title, Res.string.session_error_403_hint, FailureAction.NONE,
        )
        HTTP_UNAUTHORIZED -> FailureCopy(
            Lucide.Lock, Res.string.session_error_401_title, Res.string.session_error_401_hint, FailureAction.NONE,
        )
        else -> FailureCopy(
            Lucide.Link2Off, Res.string.session_error_link_title, Res.string.session_error_link_hint,
            FailureAction.NONE,
        )
    }
    PlaybackErrorKind.INSECURE -> FailureCopy(
        Lucide.ShieldAlert, Res.string.session_error_insecure_title, Res.string.session_error_insecure_hint,
        FailureAction.RETRY,
    )
    PlaybackErrorKind.UNSUPPORTED -> FailureCopy(
        Lucide.FileX, Res.string.session_error_format_title, Res.string.session_error_format_hint,
        FailureAction.RETRY_WITHOUT_PROCESSING,
    )
    PlaybackErrorKind.SOURCE, PlaybackErrorKind.UNKNOWN, null -> FailureCopy(
        Lucide.TriangleAlert, Res.string.session_error, Res.string.session_error_hint,
        FailureAction.RETRY_WITHOUT_PROCESSING,
    )
}
