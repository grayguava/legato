package com.grayguava.legato.ui.settings

/*
 * An iOS-style slider, adapted from Kyant0/AndroidLiquidGlass
 * (app/src/commonMain/.../catalog/components/LiquidSlider.kt), Apache License 2.0.
 *
 * The thumb is Liquid Glass over the page's recorded layer when that's on; the
 * geometry and the drag are the same on every layer, only the material changes.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.grayguava.legato.ui.components.DampedDragAnimation
import com.grayguava.legato.ui.components.LocalBackdrop
import com.grayguava.legato.ui.components.liquidGlassOn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val THUMB_W = 40.dp
private val THUMB_H = 24.dp
private val TRACK_H = 6.dp

/**
 * A slider with a glass knob. [snap] rounds the value to a step (0 keeps it
 * continuous); [onValueChangeFinished] runs once the finger lifts, for settings
 * that only write when the drag is over.
 */
@Composable
public fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    snap: Float = 0f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val trackColor = scheme.surfaceContainerHighest
    val shape = RoundedCornerShape(50)

    val liquid = liquidGlassOn()
    val backdrop = LocalBackdrop.current.takeIf { liquid }

    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValue by rememberUpdatedState(value)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    fun stepped(v: Float): Float =
        (if (snap > 0f) (v / snap).roundToInt() * snap else v).coerceIn(valueRange)

    BoxWithConstraints(
        modifier.fillMaxWidth().height(40.dp).alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val span = valueRange.endInclusive - valueRange.start
        var didDrag by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        // The finger's own value, kept here so it never waits for the caller to echo it back.
        var dragValue by remember { mutableFloatStateOf(value.coerceIn(valueRange)) }
        // What the caller was last told, so a drag only reports a value that changed.
        var emitted by remember { mutableFloatStateOf(stepped(value)) }
        val scope = rememberCoroutineScope()
        val damped = remember(scope, trackWidth, valueRange, snap) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = value.coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = if (snap > 0f) snap / 2f else span / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    // Start from the value on screen now, not the one captured when
                    // this animation was first remembered, or a second drag snaps back.
                    dragValue = currentValue.coerceIn(valueRange)
                    didDrag = false
                    dragging = true
                },
                onDragStopped = {
                    dragging = false
                    // Taps on the track are handled by the full-width gesture under
                    // the thumb; a tap that lands on the thumb itself just stays put.
                    if (didDrag) currentOnChange(stepped(dragValue))
                    currentOnFinished?.invoke()
                },
                onDrag = { _, dragAmount ->
                    // Any movement, vertical too, means this was a scroll, not a tap.
                    if (!didDrag && (dragAmount.x != 0f || dragAmount.y != 0f)) didDrag = true
                    val delta = span * (dragAmount.x / trackWidth)
                    dragValue = (if (isLtr) dragValue + delta else dragValue - delta).coerceIn(valueRange)
                    dragTo(dragValue)
                    val reported = stepped(dragValue)
                    if (reported != emitted) {
                        emitted = reported
                        currentOnChange(reported)
                    }
                },
            )
        }
        // Solid and bright, so the handle reads over both the dark track and the fill.
        val thumbColor = scheme.onSurface
        LaunchedEffect(damped) {
            snapshotFlow { currentValue }.collectLatest { v ->
                emitted = v
                // The thumb follows the finger itself; the spring is for taps and outside changes.
                if (!dragging) {
                    dragValue = v.coerceIn(valueRange)
                    if (damped.targetValue != v) damped.updateValue(v.coerceIn(valueRange))
                }
            }
        }

        val trackBackdrop = rememberLayerBackdrop()

        // The track, recorded so the knob can bend it too — only when it will.
        Box(
            Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .height(TRACK_H)
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .height(TRACK_H)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(trackColor),
            )
            Box(
                Modifier
                    .height(TRACK_H)
                    .clip(shape)
                    .background(accent)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * damped.progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }

        // Tapping anywhere on the track jumps there; the knob keeps its own drag.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (enabled) Modifier.pointerInput(trackWidth, valueRange, snap) {
                        detectTapGestures { position ->
                            val fraction = (position.x / trackWidth).coerceIn(0f, 1f)
                            val target = valueRange.start + span * if (isLtr) fraction else 1f - fraction
                            damped.animateToValue(target)
                            currentOnChange(stepped(target))
                            currentOnFinished?.invoke()
                        }
                    } else Modifier,
                ),
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX = (trackWidth * damped.progress - size.width / 2f)
                        .fastCoerceIn(0f, trackWidth - size.width)
                }
                .then(if (enabled) damped.modifier else Modifier)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(damped.progress, valueRange)
                    if (!enabled) disabled()
                    else setProgress { target ->
                        currentOnChange(stepped(target))
                        currentOnFinished?.invoke()
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
                            lens(8.dp.toPx() * progress, 12.dp.toPx() * progress, chromaticAberration = true)
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
                            val velocity = damped.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(thumbColor.copy(alpha = 1f - damped.pressProgress))
                        },
                    )
                    else Modifier.shadow(1.5.dp, shape).background(thumbColor, shape),
                )
                .size(THUMB_W, THUMB_H),
        )
    }
}
