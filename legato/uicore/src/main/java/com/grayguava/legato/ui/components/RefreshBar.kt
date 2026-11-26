package com.grayguava.legato.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val BAR_HEIGHT = 8.dp

/**
 * Where the current page's pull-to-refresh sits. Home hands its state here and
 * the shell draws the bar, because inside the page the top frost's blur would
 * take the stripes apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
public object RefreshBarSlot {
    public val state: androidx.compose.runtime.MutableState<PullToRefreshState?> = androidx.compose.runtime.mutableStateOf(null)
    public val refreshing: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false)
}

/** The shell's copy of [RefreshBar], drawn over the top frost so it stays sharp. */
@Composable
public fun RefreshBarHost(modifier: Modifier = Modifier) {
    val state = RefreshBarSlot.state.value ?: return
    RefreshBar(state, RefreshBarSlot.refreshing.value, modifier)
}

/**
 * The pull-to-refresh mark: a striped black-and-white track that fills with the
 * pull and, once released past the threshold, keeps its stripes and shows a
 * sweeping sheen pinned under the status bar until the page is back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun RefreshBar(
    state: PullToRefreshState,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val pull = state.distanceFraction.coerceIn(0f, 1f)
    // Pulling follows the finger; letting go eases the bar back down instead of
    // snapping it shut the moment the pull is released.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(pull) {
        if (pull >= shown.value) shown.snapTo(pull)
        else shown.animateTo(pull, tween(460, easing = FastOutSlowInEasing))
    }
    val fraction = shown.value
    if (!refreshing && fraction <= 0.001f) return
    val surface = MaterialTheme.colorScheme.surface
    val head by rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "refresh-head",
    )
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(50))
            .semantics {
                if (refreshing) {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    contentDescription = "Refreshing"
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            // The mark, laid out along the bar: the same two-slash stroke at the
            // same 65 degrees, repeated. Each slash runs past the bar's edges so
            // its round ends are clipped off and what shows is a clean diagonal.
            val step = 7.2.dp.toPx()
            val stroke = 2.4.dp.toPx()
            val run = h * 0.4663f
            val over = stroke
            drawRect(Color.Black)
            var x = -run - over - step
            while (x < size.width + step) {
                drawLine(
                    color = Color.White,
                    start = Offset(x, h + over),
                    end = Offset(x + run + over * 0.4663f, -over),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                x += step
            }
            if (refreshing) {
                // Released: a soft sheen travels along the stripes.
                val sheen = size.width * 0.55f
                val left = -sheen + (size.width + sheen) * head
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
                        startX = left,
                        endX = left + sheen,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(sheen, h),
                )
            } else {
                // The pull fills the bar: the run still to come is knocked back,
                // so the crisp stripes to the left are the progress.
                drawRect(
                    color = surface.copy(alpha = 0.75f),
                    topLeft = Offset(size.width * fraction, 0f),
                    size = Size(size.width * (1f - fraction), h),
                )
            }
        }
    }
}
