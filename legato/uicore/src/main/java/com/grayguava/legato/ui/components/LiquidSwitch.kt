package com.grayguava.legato.ui.components

/*
 * An iOS-style switch, adapted from Kyant0/AndroidLiquidGlass
 * (app/src/commonMain/.../catalog/components/LiquidToggle.kt), Apache License 2.0.
 *
 * The knob is Liquid Glass over the page's recorded layer when that's on; the
 * geometry and the drag are the same on every layer, only the material changes.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

/** Track and knob, in dp: a wide 64 x 28 track with a 40 x 24 oval knob, scaled down a fifth. */
private val TRACK_W = 51.2.dp
private val TRACK_H = 22.4.dp
private val KNOB_W = 32.dp
private val KNOB_H = 19.2.dp
private val PADDING = 1.6.dp
private val TRAVEL = TRACK_W - KNOB_W - PADDING * 2

@Composable
public fun LiquidSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The track takes the theme's accent, which is where a custom accent will land.
    val onColor = scheme.primary
    val offColor = scheme.surfaceContainerHighest
    val shape = RoundedCornerShape(50)

    val liquid = liquidGlassOn()
    val backdrop = LocalBackdrop.current.takeIf { liquid }

    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onChange)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current

    var didDrag by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val damped = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = { dragging = true; didDrag = false },
            onDragStopped = {
                dragging = false
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    currentOnChange(fraction == 1f)
                    didDrag = false
                } else {
                    // A tap: the knob crosses over.
                    fraction = if (currentChecked) 0f else 1f
                    currentOnChange(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                // Any movement, vertical too, means this was a scroll, not a tap.
                if (!didDrag && (dragAmount.x != 0f || dragAmount.y != 0f)) didDrag = true
                val delta = with(density) { dragAmount.x / TRAVEL.toPx() }
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
                dragTo(fraction)
            },
        )
    }
    LaunchedEffect(damped) {
        // The knob follows the finger itself; the spring is for taps and outside changes.
        snapshotFlow { fraction }.collectLatest { if (!dragging) damped.updateValue(it) }
    }
    LaunchedEffect(checked) {
        snapshotFlow { currentChecked }.collectLatest { isOn ->
            val target = if (isOn) 1f else 0f
            if (target != fraction) {
                fraction = target
                damped.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    // Legible on the dark track when off and on the light one when on, the way
    // the app's Material switch changed its thumb.
    val knobColor = lerpColor(scheme.onSurfaceVariant, scheme.onPrimary, damped.value)
    // Only worth recording when something above samples it.
    val trackLayer = if (backdrop != null) Modifier.layerBackdrop(trackBackdrop) else Modifier

    Box(
        modifier.size(TRACK_W, TRACK_H).alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(trackLayer)
                .clip(shape)
                .drawBehind { drawRect(lerpColor(offColor, onColor, damped.value)) },
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX = lerp(PADDING.toPx(), (PADDING + TRAVEL).toPx(), damped.value)
                }
                .then(if (enabled) damped.modifier else Modifier)
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(currentChecked)
                    if (!enabled) disabled()
                    else onClick(label = "Toggle") {
                        currentOnChange(!currentChecked)
                        true
                    }
                }
                .then(
                    if (backdrop != null) Modifier.drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawTrack ->
                                val progress = damped.pressProgress
                                scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) {
                                    drawTrack()
                                }
                            },
                        ),
                        shape = { shape },
                        effects = {
                            val progress = damped.pressProgress
                            blur(8.dp.toPx() * (1f - progress))
                            lens(4.dp.toPx() * progress, 8.dp.toPx() * progress, chromaticAberration = true)
                        },
                        highlight = {
                            val progress = damped.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress,
                            )
                        },
                        shadow = { Shadow(radius = 3.dp, color = Color.Black.copy(alpha = 0.08f)) },
                        innerShadow = {
                            val progress = damped.pressProgress
                            InnerShadow(radius = 3.dp * progress, alpha = progress)
                        },
                        layerBlock = {
                            scaleX = damped.scaleX
                            scaleY = damped.scaleY
                            val velocity = damped.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(knobColor.copy(alpha = 1f - damped.pressProgress))
                        },
                    )
                    else Modifier.shadow(1.5.dp, shape).background(knobColor, shape),
                )
                .size(KNOB_W, KNOB_H),
        )
    }
}
