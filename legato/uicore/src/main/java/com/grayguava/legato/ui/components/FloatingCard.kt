package com.grayguava.legato.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.grayguava.legato.settings.AppSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Lets what's inside a [FloatingCard] close it with its exit motion. */
public class FloatingCardScope internal constructor(private val closeWith: (after: (() -> Unit)?) -> Unit) {
    /** Plays the card out, then calls [after], or the card's own dismiss without one. */
    public fun close(after: (() -> Unit)? = null): Unit = closeWith(after)
}

public val LocalFloatingCard: ProvidableCompositionLocal<FloatingCardScope?> =
        compositionLocalOf<FloatingCardScope?> { null }

/**
 * The card's edge stroke, stopping at the bottom: a sheet that runs to the
 * screen's bottom edge has no line drawn along it.
 */
private fun Modifier.sheetBorder(width: Dp, brush: Brush, corner: Dp): Modifier = drawWithCache {
    val stroke = width.toPx()
    val r = corner.toPx()
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, r * 2f, r * 2f), 180f, 90f, false)
        lineTo(size.width - r, 0f)
        arcTo(Rect(size.width - r * 2f, 0f, size.width, r * 2f), 270f, 90f, false)
        lineTo(size.width, size.height)
    }
    onDrawWithContent {
        drawContent()
        drawPath(path, brush, style = Stroke(stroke))
    }
}

/**
 * The app's popup: a rounded card that rises from the bottom over a frosted
 * blur of the screen (dimmed where the phone can't blur behind a window),
 * with a grab handle, an optional icon and title, and a round close button.
 * Dragging the top of the card down, tapping outside or pressing back plays
 * it out the way it came. With [centered] the card becomes a plain popup in
 * the middle of the screen: no handle, no drag, sized to its content.
 *
 * [content] is laid out in a column that keeps to the screen, so a list
 * inside can scroll. [actions] sit in a row along the bottom.
 */
@Composable
public fun FloatingCard(
    onDismiss: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    subtitle: String? = null,
    dismissible: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** Stays put above [content] and, like the handle, pulls the card up and down. */
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /** Opens at half height; dragging the top, or scrolling the content, takes it up to nearly full. */
    expandable: Boolean = false,
    /** Opens already at full content height (up to 90% of the screen), so nothing needs scrolling to reach. */
    startExpanded: Boolean = false,
    /** Enter motion for the sheet; null keeps the app's own. */
    enter: AnimationSpec<Float>? = null,
    /** Exit motion for the sheet; null keeps the app's own. */
    exit: AnimationSpec<Float>? = null,
    /** A normal popup in the middle of the screen instead of a card rising from the bottom. */
    centered: Boolean = false,
    /** iOS alert: 270dp wide, 14dp corners, iOS materials and a flat hairline. */
    alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui by AppSettings.ui.collectAsState()
    val still = ui.reduceAnimation
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var open by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    // Written straight from the finger, so a release spring can't be cancelled
    // by a drag step that arrives after it starts.
    var drag by remember { mutableFloatStateOf(0f) }
    // How far an expandable card has been pulled up past its half height, in px.
    var extra by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val card = remember {
        FloatingCardScope { after ->
            if (!closing) {
                closing = true
                scope.launch {
                    open = false
                    if (!still) delay(EXIT_MS)
                    (after ?: dismiss)()
                    // Still here a moment later: whoever owns the popup kept it
                    // open (a download running, say), so it comes back.
                    delay(KEPT_MS)
                    drag = 0f
                    closing = false
                    open = true
                }
            }
        }
    }

    // Where the card was opened from: in a sheet, the frost behind keeps the sheet in sight.
    val opener = LocalView.current
    Dialog(
        onDismissRequest = { if (dismissible) card.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        // The screen behind frosted by the app itself; the window's blur only where that can't be done.
        var frost by remember { mutableStateOf(if (ui.reduceBlur) Frost.FAILED else Frost.PENDING) }
        val windowFrost = FrostBehind(enabled = !ui.reduceBlur, standIn = frost != Frost.FAILED)
        val frosted = windowFrost || frost == Frost.READY
        val dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f
        val iosFill = if (dark) Color(0xFF1C1C1E).copy(alpha = if (frosted) 0.80f else 0.96f)
                      else Color(0xFFF2F2F2).copy(alpha = if (frosted) 0.86f else 0.98f)
        val shown by animateFloatAsState(
            if (open) 1f else 0f,
            // No bounce: a card that overshoots its place and comes back reads as a shudder.
            if (still) snap() else if (open) (enter ?: spring(dampingRatio = 1f, stiffness = 340f)) else (exit ?: tween(EXIT_MS.toInt())),
            label = "card",
        )
        if (frost != Frost.FAILED) {
            val got = ScreenFrost(shown = { shown }, over = opener)
            LaunchedEffect(got) { frost = got }
        }
        // The card rises once the blur behind it is ready (or has failed), and the first frame
        // is laid out: rising while the blur was still being made, its glass changed from nearly
        // solid to clear partway up, and the first frames, busy building the menu, were dropped.
        // An expandable card also waits to know its own height: measured on its first
        // frames, it went from tall to its real size while rising, a jump halfway up.
        var measured by remember { mutableStateOf(!expandable) }
        LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            kotlinx.coroutines.withTimeoutOrNull(FROST_WAIT_MS) {
                androidx.compose.runtime.snapshotFlow { frost != Frost.PENDING && measured }.first { it }
            }
            open = true
        }
        // A dialog's content sits outside the app's Surface, so it needs its own text colour.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, LocalFloatingCard provides card) {
            BoxWithConstraints(
                Modifier.fillMaxSize().pointerInput(dismissible) { detectTapGestures { if (dismissible) card.close() } },
                contentAlignment = if (centered) Alignment.Center else Alignment.BottomCenter,
            ) {
                val maxCard = maxHeight
                val corner = if (alert) 14.dp else if (centered) 22.dp else 18.dp
                // A sheet runs to the screen's bottom edge, so only its top corners round.
                val shape = if (centered || alert) RoundedCornerShape(corner)
                            else RoundedCornerShape(topStart = corner, topEnd = corner)
                // A sheet has no line along the screen's bottom edge; the other cards are fully outlined.
                fun Modifier.cardBorder(width: Dp, brush: Brush): Modifier =
                    if (centered || alert) border(width, brush, shape) else sheetBorder(width, brush, corner)
                val density = LocalDensity.current
                // The card's own height with everything shown, measured as it lays out;
                // an expandable card never grows past it, so a short menu has no empty foot.
                var naturalPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
                val fullPx = minOf(with(density) { (maxCard * 0.9f).toPx() }, naturalPx)
                // Start at full height when asked, so the whole menu shows at once; otherwise half.
                val halfPx = if (startExpanded) fullPx else minOf(with(density) { (maxCard * 0.5f).toPx() }, fullPx)
                val range = (fullPx - halfPx).coerceAtLeast(0f)
                fun settle(velocity: Float) {
                    settling?.cancel()
                    settling = scope.launch {
                        val target = when {
                            !expandable -> 0f
                            velocity < -FLING -> range
                            velocity > FLING -> 0f
                            else -> if (extra > range / 2) range else 0f
                        }
                        launch { animate(drag, 0f, velocity, spring(dampingRatio = 0.75f, stiffness = 500f)) { v, _ -> drag = v } }
                        if (expandable) animate(extra, target, -velocity, spring(dampingRatio = 0.85f, stiffness = 420f)) { v, _ -> extra = v.coerceIn(0f, range) }
                    }
                }
                /** Moves the card by [d] px (down positive): an expandable one grows or shrinks first, then the rest pulls it down. */
                fun pull(d: Float) {
                    var left = d
                    if (expandable) {
                        if (left < 0 && drag <= 0f) {
                            val take = minOf(-left, range - extra)
                            extra += take
                            left += take
                        } else if (left > 0 && extra > 0f && drag <= 0f) {
                            val take = minOf(left, extra)
                            extra -= take
                            left -= take
                        }
                    }
                    if (left != 0f) drag = (drag + if (drag + left < 0) left * 0.2f else left).coerceAtLeast(-24f)
                }
                val dragState = rememberDraggableState { d -> pull(d) }
                // Scrolling the content up grows the card first; pulling it down
                // at the top of the list shrinks it back to half.
                val grow = remember(expandable, range) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (!expandable || source != NestedScrollSource.UserInput || available.y >= 0 || extra >= range) return Offset.Zero
                            settling?.cancel()
                            val take = minOf(-available.y, range - extra)
                            extra += take
                            return Offset(0f, -take)
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (!expandable || source != NestedScrollSource.UserInput || available.y <= 0 || extra <= 0f) return Offset.Zero
                            settling?.cancel()
                            val take = minOf(available.y, extra)
                            extra -= take
                            return Offset(0f, take)
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (!expandable || extra <= 0f || extra >= range) return Velocity.Zero
                            settle(available.y)
                            return available
                        }
                    }
                }
                Column(
                    Modifier
                        .statusBarsPadding()
                        // A sheet reaches the screen's bottom edge; the other cards stay clear of the bar.
                        .then(if (centered || alert) Modifier.navigationBarsPadding() else Modifier)
                        .imePadding()
                        .padding(
                            if (alert) PaddingValues(0.dp)
                            else if (centered) PaddingValues(20.dp)
                            else PaddingValues(top = 12.dp),
                        )
                        .then(if (alert) Modifier.width(270.dp) else Modifier.widthIn(max = if (centered) 340.dp else 560.dp))
                        .then(if (centered) Modifier else Modifier.fillMaxWidth())
                        .then(
                            if (expandable) {
                                Modifier.layout { measurable, constraints ->
                                    // Lists that can't be measured whole (lazy ones) just take the height asked for.
                                    val natural = runCatching { measurable.maxIntrinsicHeight(constraints.maxWidth) }.getOrNull()
                                    if (natural != null && natural.toFloat() != naturalPx) naturalPx = natural.toFloat()
                                    measured = true
                                    val want = (halfPx + extra).roundToInt()
                                    val h = minOf(want, natural ?: want).coerceIn(constraints.minHeight, constraints.maxHeight)
                                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                }
                            } else if (centered) {
                                // A popup takes only the height its content needs.
                                Modifier
                            } else {
                                Modifier.heightIn(max = maxCard * 0.9f)
                            },
                        )
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, if (centered) 0.5f else 1f)
                            // A popup scales in place; a card slides up from the bottom edge.
                            translationY = if (centered) 0f else (1f - shown) * size.height * 0.5f + drag
                            val s = when {
                                alert -> 1.08f - 0.08f * shown
                                centered -> 0.88f + 0.12f * shown
                                else -> 0.92f + 0.08f * shown
                            }
                            scaleX = s
                            scaleY = s
                            alpha = shown.coerceIn(0f, 1f)
                        }
                        // No drop shadow under glass, whichever blur is behind it: Android
                        // draws the shadow under the whole card, a little lower and inset,
                        // and through the glass its edges read as a box inside the card.
                        // Only the solid card, with no blur at all, gets one.
                        .then(
                            if (frost != Frost.FAILED || frosted) Modifier
                            else Modifier.shadow(18.dp, shape, ambientColor = MaterialTheme.colorScheme.scrim, spotColor = MaterialTheme.colorScheme.scrim),
                        )
                        .clip(shape)
                        .then(
                            when {
                                alert -> Modifier.background(iosFill)
                                frosted -> {
                                // Glass: the blurred screen behind the window shows through
                                // a film of the surface, lit a little along the top.
                                // With Liquid Glass on, the film thins right out and takes
                                // the material's bright rim, so the card reads as glass
                                // rather than a panel with a blur behind it.
                                val liquid = liquidGlassOn()
                                Modifier
                                    // Over the app's own frost the blur is certain, so the film can be
                                    // thin glass. Over the window's it stays near solid: some phones say
                                    // they blur behind a window and then don't, and the page would read through.
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                            alpha = if (liquid) (if (dark) 0.34f else 0.46f)
                                            else if (frost == Frost.READY) (if (dark) 0.62f else 0.72f)
                                            else if (dark) 0.88f else 0.9f,
                                        ),
                                    )
                                    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.07f else 0.18f), 0.35f to Color.White.copy(alpha = 0f)))
                                    .cardBorder(
                                        if (liquid) 1.1.dp else 1.dp,
                                        if (liquid) {
                                            Brush.linearGradient(
                                                0f to Color.White.copy(alpha = if (dark) 0.6f else 0.85f),
                                                0.45f to Color.White.copy(alpha = if (dark) 0.14f else 0.3f),
                                                1f to Color.White.copy(alpha = if (dark) 0.44f else 0.65f),
                                            )
                                        } else {
                                            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.6f), Color.White.copy(alpha = 0.04f)))
                                        },
                                    )
                            }
                            else -> {
                                Modifier
                                    // Solid, so its shadow never shows through.
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .cardBorder(1.dp, SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                            }
                            },
                        )
                        // The sheet runs behind the navigation bar, so its content keeps clear from the inside.
                        .then(if (centered || alert) Modifier else Modifier.navigationBarsPadding())
                        // Taps on the card stay on the card.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(if (alert) 0.dp else 10.dp)
                        .padding(bottom = if (alert) 0.dp else 4.dp),
                ) {
                    // The handle and title pull the card down; content below keeps its own gestures.
                    // A centered popup has no handle and isn't dragged away: an outside tap closes it.
                    Column(
                        Modifier.fillMaxWidth().then(
                            if (centered) Modifier else Modifier.draggable(
                                orientation = Orientation.Vertical,
                                enabled = dismissible,
                                state = dragState,
                                onDragStarted = { settling?.cancel() },
                                onDragStopped = { v ->
                                    // Down hard from half height, or pulled well past it, closes; an
                                    // expanded card flung down only drops back to half.
                                    if (drag > with(density) { 110.dp.toPx() } || (v > 1400f && extra <= 1f)) card.close()
                                    else settle(v)
                                },
                            ),
                        ),
                    ) {
                        if (!centered) {
                            Box(
                                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
                                    .size(width = 36.dp, height = 4.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                            )
                        }
                        if (title != null) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (icon != null) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
                                    Spacer(Modifier.width(14.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (subtitle != null) {
                                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (dismissible) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .clickable(onClickLabel = "Close", role = Role.Button) { card.close() },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.Close, "Close", Modifier.size(20.dp)) }
                                }
                            }
                        } else {
                            Spacer(Modifier.size(4.dp))
                        }
                        if (header != null) header()
                    }
                    // The body rises in just behind the card, on the card's own clock, so the
                    // two never pull against each other.
                    Column(
                        Modifier.weight(1f, fill = expandable).fillMaxWidth().nestedScroll(grow).padding(contentPadding).graphicsLayer {
                            val rise = ((shown - 0.2f) / 0.8f).coerceIn(0f, 1f)
                            alpha = rise
                            translationY = (1f - rise) * 16.dp.toPx()
                        },
                        content = content,
                    )
                    if (actions != null) {
                        if (alert) {
                            Box(
                                Modifier.fillMaxWidth().height(0.5.dp)
                                    .background(if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f)),
                            )
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions,
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(start = if (centered) 6.dp else 4.dp, end = if (centered) 6.dp else 4.dp, top = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The app's alert: iOS's alert panel, with AlertDialog's slots so a dialog
 * moves over by changing its name. No grab handle or drag: an outside tap
 * closes it. Title, message and buttons take iOS's type and hairlines, and
 * the confirm button reads as the preferred action.
 */
@Composable
public fun FloatingDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f
    val hairline = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f)
    CompositionLocalProvider(LocalIosAlert provides true) {
        FloatingCard(
            onDismiss = onDismissRequest,
            dismissible = properties.dismissOnClickOutside || properties.dismissOnBackPress,
            centered = true,
            alert = true,
            contentPadding = PaddingValues(0.dp),
            actions = {
                if (dismissButton != null) {
                    Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalSheetTone provides SheetTone.Neutral) { dismissButton() } }
                    Box(Modifier.width(0.5.dp).height(44.dp).background(hairline))
                }
                Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalSheetTone provides SheetTone.Primary) { confirmButton() } }
            },
        ) {
            Column(
                modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (title != null) {
                    ProvideTextStyle(
                        TextStyle(
                            fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { title() }
                }
                if (title != null && text != null) Spacer(Modifier.height(6.dp))
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)) {
                        ProvideTextStyle(
                            TextStyle(
                                fontSize = 13.sp, lineHeight = 18.sp,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                            ),
                        ) { text() }
                    }
                }
            }
        }
    }
}

public enum class SheetTone { Primary, Neutral, Danger }

/** Which look a [SheetButton] takes when it isn't told: set by the slot it sits in. */
public val LocalSheetTone: ProvidableCompositionLocal<SheetTone> = compositionLocalOf { SheetTone.Neutral }

/** Set inside [FloatingDialog]: its buttons draw as iOS alert actions, not pills. */
private val LocalIosAlert: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * A pill button for popups. Filled in the accent for the main action,
 * a soft fill for the rest, red for [SheetTone.Danger]. With [closes] it
 * plays the popup out first and then runs [onClick]; with [loading] it
 * shows a spinner by its label.
 */
@Composable
public fun SheetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: SheetTone = LocalSheetTone.current,
    closes: Boolean = false,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val card = LocalFloatingCard.current
    val scheme = MaterialTheme.colorScheme
    if (LocalIosAlert.current) {
        // iOS alert action: plain tinted text on the panel, dimming where pressed.
        // Monochrome actions: ink for the ordinary ones, the theme's red for destructive.
        val tint = if (tone == SheetTone.Danger) scheme.error else scheme.onSurface
        val alertInteraction = remember { MutableInteractionSource() }
        val alertPressed by alertInteraction.collectIsPressedAsState()
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(if (alertPressed) scheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(interactionSource = alertInteraction, indication = null, enabled = enabled, role = Role.Button) {
                    if (closes && card != null) card.close(onClick) else onClick()
                }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = tint.copy(alpha = if (enabled || loading) 1f else 0.35f),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = if (tone == SheetTone.Primary) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    // Liquid glass: the button is a thin tinted film with a bright rim, not a
    // solid pill. Colour comes from the ink, so Danger reads as red text on glass.
    val liquid = liquidGlassOn()
    val (fill, ink) = when (tone) {
        SheetTone.Primary -> if (liquid) scheme.primary.copy(alpha = 0.26f) to scheme.primary else scheme.primary to scheme.onPrimary
        SheetTone.Danger -> if (liquid) scheme.error.copy(alpha = 0.22f) to scheme.error else scheme.error to scheme.onError
        SheetTone.Neutral -> scheme.onSurface.copy(alpha = if (liquid) 0.10f else 0.08f) to scheme.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
                alpha = if (enabled || loading) 1f else 0.4f
            }
            .clip(CircleShape)
            .background(fill)
            .then(
                if (liquid) {
                    Modifier.border(
                        1.dp,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.5f),
                            0.5f to Color.White.copy(alpha = 0.12f),
                            1f to Color.White.copy(alpha = 0.4f),
                        ),
                        CircleShape,
                    )
                } else Modifier,
            )
            .clickable(interactionSource = interaction, indication = FocusRing(CircleShape), enabled = enabled, role = Role.Button) {
                if (closes && card != null) card.close(onClick) else onClick()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = ink, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Frosts what's behind the dialog's window where the phone allows it; dims it
 * either way. Returns whether the blur is on right now: the system can turn
 * cross-window blur off (battery saver, some animations off) and back on.
 */
@Composable
public fun FrostBehind(
    enabled: Boolean,
    /** True while [ScreenFrost] frosts the screen itself: the window is left alone. */
    standIn: Boolean = false,
): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    var blurring by remember { mutableStateOf(false) }
    DisposableEffect(view, enabled, standIn) {
        val window = (view.parent as? DialogWindowProvider)?.window?.takeIf { !standIn }
        var listener: java.util.function.Consumer<Boolean>? = null
        if (standIn) blurring = false
        if (window != null) {
            window.setDimAmount(if (enabled) 0.35f else 0.55f)
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = with(density) { 36.dp.roundToPx() }
                val apply = java.util.function.Consumer<Boolean> { on ->
                    blurring = on
                    // Lighter dim over a blur, so the colours behind still come through the card.
                    window.setDimAmount(if (on) 0.22f else 0.35f)
                    if (on) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes = window.attributes.apply { blurBehindRadius = radius }
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
                apply.accept(window.windowManager.isCrossWindowBlurEnabled)
                window.windowManager.addCrossWindowBlurEnabledListener(apply)
                listener = apply
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listener?.let { window?.windowManager?.removeCrossWindowBlurEnabledListener(it) }
        }
    }
    return blurring
}

private const val EXIT_MS = 200L
private const val KEPT_MS = 120L
/** The longest a card waits for the blur behind it before rising without it. */
private const val FROST_WAIT_MS = 180L
/** A release faster than this, in px/s, decides an expandable card's height on its own. */
private const val FLING = 800f
