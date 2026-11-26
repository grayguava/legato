package com.grayguava.legato.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grayguava.legato.ui.icons.SfIcons

/**
 * Back, play/pause and forward: Apple's filled glyphs on the player itself,
 * with no plates behind them. The skips lean toward where they go as they're
 * tapped, and the play glyph breathes while the song loads.
 */
@Composable
public fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    var nextRolls by remember { mutableIntStateOf(0) }
    var previousRolls by remember { mutableIntStateOf(0) }
    val back = {  previousRolls++; onPrevious() }
    val ahead = {  nextRolls++; onNext() }
    Row(
        modifier.fillMaxWidth().height(86.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(forward = false, previousRolls, enabled = true, animate, back)
        Spacer(Modifier.width(23.1.dp))
        PlayButton(isPlaying, isBuffering, animate, onTogglePlay)
        Spacer(Modifier.width(23.1.dp))
        SkipButton(forward = true, nextRolls, hasNext, animate, ahead)
    }
}

/** How far a press has gone, 0 to 1, springing in under the finger and easing back out. */
@Composable
private fun pressAmount(pressed: Boolean, animate: Boolean): Float {
    val p by animateFloatAsState(
        if (pressed) 1f else 0f,
        when {
            !animate -> snap()
            pressed -> spring(dampingRatio = 0.8f, stiffness = 900f)
            else -> spring(dampingRatio = 0.55f, stiffness = 380f)
        },
        label = "press",
    )
    return p
}

private fun Modifier.button(interaction: MutableInteractionSource, enabled: Boolean, label: String, onClick: () -> Unit) =
    clickable(interactionSource = interaction, indication = com.grayguava.legato.ui.components.FocusRing(androidx.compose.foundation.shape.CircleShape), enabled = enabled, onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }

/**
 * A value that travels from one end to the other and back, for the loading
 * breath. It runs only while [on] is true, so a still glyph costs nothing.
 */
@Composable
private fun looping(on: Boolean, from: Float, to: Float, ms: Int, reverse: Boolean): Float {
    if (!on) return from
    val v by rememberInfiniteTransition(label = "loop").animateFloat(
        from,
        to,
        infiniteRepeatable(
            tween(ms, easing = if (reverse) FastOutSlowInEasing else LinearEasing),
            if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "loopValue",
    )
    return v
}

// ---- Glyphs ------------------------------------------------------------------

@Composable
private fun PlayButton(isPlaying: Boolean, isBuffering: Boolean, animate: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = pressAmount(interaction.collectIsPressedAsState().value, animate)
    // While the song loads the glyph breathes rather than sitting still.
    val loading = isBuffering && animate
    val breath = looping(loading, 0.4f, 1f, 900, reverse = true)
    Box(
        Modifier
            .size(85.dp)
            .graphicsLayer {
                val s = 1f - 0.06f * press
                scaleX = s
                scaleY = s
                alpha = if (loading) breath else 1f
            }
            .button(interaction, true, if (isPlaying) "Pause" else "Play", onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) SfIcons.PauseFill else SfIcons.PlayFill,
            null,
            Modifier.size(48.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun SkipButton(forward: Boolean, rolls: Int, enabled: Boolean, animate: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = pressAmount(interaction.collectIsPressedAsState().value, animate)
    // A lean toward where the tap is going, that wobbles back once let go.
    val lean = remember { Animatable(0f) }
    LaunchedEffect(rolls) {
        if (rolls == 0 || !animate) return@LaunchedEffect
        lean.snapTo(1f)
        lean.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
    }
    val dir = if (forward) 1f else -1f
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer {
                val s = 1f - 0.1f * press
                scaleX = s
                scaleY = s
                rotationZ = dir * (12f * lean.value + 6f * press)
                translationX = dir * (4.dp.toPx() * lean.value)
                alpha = if (enabled) 1f else 0.35f
            }
            .button(interaction, enabled, if (forward) "Next" else "Previous", onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (forward) SfIcons.ForwardFill else SfIcons.BackwardFill,
            null,
            Modifier.size(40.dp),
            tint = Color.White,
        )
    }
}
