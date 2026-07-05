package com.rinwave.sakuro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.seek_seconds

/**
 * A ripple overlay for double-tap seek, YouTube-style: a circle radiates from
 * the tap point, animated chevrons show the direction, and the seconds appear
 * below. [rippleKey] restarts the ripple on every tap;
 * [verticalFraction] (0..1) is the vertical position of the tap.
 */
@Composable
fun YouTubeSeekOverlay(
    forward: Boolean,
    seconds: Int,
    rippleKey: Int,
    verticalFraction: Float,
    modifier: Modifier = Modifier,
) {
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(rippleKey) {
        ripple.snapTo(0f)
        ripple.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val chevrons = rememberInfiniteTransition(label = "seekChevrons")
    val phase by chevrons.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chevronPhase",
    )

    val lens = remember(forward) { edgeLensShape(forward) }
    Box(
        modifier = modifier.fillMaxHeight().clip(lens),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(SakuroColors.Background.copy(alpha = 0.20f)))

        Canvas(Modifier.matchParentSize()) {
            val cx = if (forward) size.width * 0.72f else size.width * 0.28f
            val cy = (size.height * verticalFraction)
                .coerceIn(size.height * 0.15f, size.height * 0.85f)
            drawCircle(
                color = SakuroColors.TextPrimary.copy(alpha = (1f - ripple.value) * 0.22f),
                radius = size.height * 0.55f * ripple.value,
                center = Offset(cx, cy),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.offset(x = if (forward) 16.dp else (-16).dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                repeat(3) { i ->
                    val index = if (forward) i else 2 - i
                    val active = (phase.toInt() % 3) == index
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        tint = SakuroColors.TextPrimary.copy(alpha = if (active) 1f else 0.4f),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = if (forward) 0f else 180f },
                    )
                }
            }
            Text(
                stringResource(Res.string.seek_seconds, seconds),
                color = SakuroColors.TextPrimary,
                fontSize = 15.sp,
            )
        }
    }
}

/** Edge lens: a semicircular region at the screen edge, cut out by a quadratic curve. */
private fun edgeLensShape(forward: Boolean) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    if (forward) {
        moveTo(w, 0f)
        lineTo(w, h)
        quadraticTo(-w * 0.5f, h / 2f, w, 0f)
    } else {
        moveTo(0f, 0f)
        lineTo(0f, h)
        quadraticTo(w * 1.5f, h / 2f, 0f, 0f)
    }
    close()
}
