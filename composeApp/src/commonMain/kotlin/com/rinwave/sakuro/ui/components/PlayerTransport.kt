package com.rinwave.sakuro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.rinwave.sakuro.ui.theme.SakuroColors

/** Round play/pause button: springs on press, cross-fades the icon. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = SakuroColors.AccentSakura,
        contentColor = SakuroColors.Background,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
            },
            label = "playIcon",
        ) { playing ->
            Icon(
                imageVector = if (playing) Lucide.Pause else Lucide.Play,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.padding(18.dp).size(32.dp),
            )
        }
    }
}

/** Round skip ± button with the same spring press animation. */
@Composable
fun SkipButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "skipScale",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = SakuroColors.Background.copy(alpha = 0.28f),
        contentColor = SakuroColors.TextPrimary,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Icon(icon, contentDescription, Modifier.padding(12.dp).size(26.dp))
    }
}
