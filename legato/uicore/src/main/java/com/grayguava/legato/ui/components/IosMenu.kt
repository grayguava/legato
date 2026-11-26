package com.grayguava.legato.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.grayguava.legato.base.model.BrowseType
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.SavedList
import com.grayguava.legato.ui.icons.SfIcons
import com.grayguava.legato.ui.share.ListShare
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import kotlin.math.roundToInt

// BitChord's IosMenu (app/src/main/java/com/music/bitchord/ui/components/IosMenu.kt):
// a frosted charcoal slab with iOS colours, grown out of the corner nearest the button.

private val MenuFill = Color(0xFF1C1C1E)
private val MenuLabel = Color.White.copy(alpha = 0.75f)
private val MenuSecondary = Color.White.copy(alpha = 0.60f)
private val MenuSeparator = Color.White.copy(alpha = 0.10f)
private val MenuPressed = Color.White.copy(alpha = 0.10f)
private val MenuRim = Color.White.copy(alpha = 0.10f)
private val MenuWidth = 256.dp
private val MenuShape: Shape = RoundedCornerShape(30.dp)
private val MenuBlur = 18.dp

/**
 * Item model for [MenuPanel] — each surface builds its own list.
 */
sealed interface MenuItem {
    data class Row(
        val icon: ImageVector,
        val label: String,
        val description: String? = null,
        val enabled: Boolean = true,
        val onPick: () -> Unit
    ) : MenuItem
    data class Tile(
        val icon: ImageVector,
        val label: String,
        /** An icon-only action in the top row: the label is its description, not drawn. */
        val iconOnly: Boolean = false,
        /** A colour for the glyph instead of the menu's own, as a liked heart's red. */
        val tint: Color? = null,
        val onPick: () -> Unit
    ) : MenuItem
    object Separator : MenuItem
}

/**
 * Where a page's song menu is drawn: the shell's own layer, over the pages and
 * outside the recording the glass blurs, so the panel frosts the page the way
 * the player's menu frosts the player. A page holds it only while its menu is
 * up, so the panel goes when the page does.
 */
public class MenuLayer {
    private var owner: Any? by mutableStateOf(null)
    private var drawn by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** What to draw, if anyone holds the layer. */
    public val panel: (@Composable () -> Unit)? get() = drawn

    /** Draws [panel] from now on, in [holder]'s name. */
    public fun show(holder: Any, panel: @Composable () -> Unit) {
        owner = holder
        drawn = panel
    }

    /** Lets the layer go, if [holder] is still the one holding it. */
    public fun hide(holder: Any) {
        if (owner !== holder) return
        owner = null
        drawn = null
    }
}

/** Null outside the app shell — the player's own window, previews — where a page keeps its older menu. */
public val LocalMenuLayer: ProvidableCompositionLocal<MenuLayer?> = staticCompositionLocalOf { null }

/** Safe-area edges in px for a menu drawn where insets don't reach (a popup window). */
private val LocalMenuInsets = staticCompositionLocalOf<Pair<Int, Int>> { Pair(0, 0) }

/**
 * Puts a menu on screen: the shell's own layer when there is one, so the panel
 * frosts the page the way the player's does, and a window of its own otherwise.
 * The content gets the anchor's place on screen and keeps its own open/close
 * state; the host only decides where it is drawn.
 */
@Composable
public fun MenuPanelHost(
    anchor: Rect?,
    onDismiss: () -> Unit,
    content: @Composable (Rect) -> Unit,
) {
    val at = anchor ?: return
    val layer = LocalMenuLayer.current
    if (layer != null) {
        val holder = remember { Any() }
        DisposableEffect(layer, at) {
            layer.show(holder) { content(at) }
            onDispose { layer.hide(holder) }
        }
    } else {
        // A popup window reports no insets of its own; the screen's are read here.
        val insets = WindowInsets.safeDrawing
        val density = LocalDensity.current
        Popup(
            alignment = Alignment.TopStart,
            properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false, clippingEnabled = false),
            onDismissRequest = onDismiss,
        ) {
            CompositionLocalProvider(LocalMenuInsets provides (insets.getTop(density) to insets.getBottom(density))) {
                content(at)
            }
        }
    }
}

/**
 * A lighter frost than the app's smoke glass: less blur, no lens or grain, and
 * a thinner fill so the player reads through the panel. With dynamic blur off
 * (the app's static glass mode) it is a solid charcoal panel instead.
 */
@Composable
public fun Modifier.menuPanel(): Modifier {
    val frosted = if (backdropBlurOn()) {
        LocalBackdrop.current?.let { backdrop ->
            drawBackdrop(
                backdrop = backdrop,
                shape = { MenuShape },
                effects = {
                    colorControls(saturation = 1.2f)
                    blur(MenuBlur.toPx())
                },
                shadow = null,
                onDrawSurface = { drawRect(MenuFill.copy(alpha = 0.48f)) },
            )
        }
    } else null
    val panel = frosted ?: clip(MenuShape).background(MenuFill.copy(alpha = 0.96f))
    return panel.clip(MenuShape).border(0.5.dp, MenuRim, MenuShape)
}

/** A hairline between groups, inset from the panel's edges with air either side. */@Composable
public fun MenuSeparatorLine() {
    Box(Modifier.fillMaxWidth().height(17.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MenuSeparator))
    }
}

/** Renders a list of [MenuItem] using the existing MenuRow / MenuTile / MenuSeparatorLine. */
@Composable
public fun MenuPanel(items: List<MenuItem>) {
    var index = 0
    while (index < items.size) {
        when (val item = items[index]) {
            is MenuItem.Tile -> {
                // Consecutive tiles share one row across the top, as in the player menu.
                val tiles = mutableListOf<MenuItem.Tile>()
                while (index < items.size && items[index] is MenuItem.Tile) {
                    tiles.add(items[index] as MenuItem.Tile)
                    index++
                }
                if (tiles.all { it.iconOnly }) {
                    // A song's own actions: big round buttons with no captions, split by the row's own line.
                    Row(
                        Modifier.fillMaxWidth().height(60.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tiles.forEachIndexed { i, tile ->
                            if (i > 0) MenuIconDivider()
                            MenuIconTile(tile.icon, tile.label, tile.tint, tile.onPick)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tiles.forEach { tile ->
                            MenuTile(tile.icon, tile.label, tile.onPick, Modifier.weight(1f))
                        }
                    }
                }
            }
            is MenuItem.Row -> { MenuRow(item.icon, item.label, item.onPick, item.description); index++ }
            is MenuItem.Separator -> { MenuSeparatorLine(); index++ }
        }
    }
}

/** The shared panel: [IosMenu]'s chrome around a list of [MenuItem]s. */
@Composable
public fun MenuPanelView(
    visible: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    items: List<MenuItem>,
) {
    IosMenu(
        visible = visible,
        anchor = anchor,
        onDismiss = onDismiss,
        onFinished = onFinished,
        placeBesideAnchor = true,
    ) {
        MenuPanel(items)
    }
}

/** One line of the menu: a fixed icon column, label, optional dimmer subtitle. */
@Composable
public fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, description: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) MenuPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = MenuLabel, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label, color = MenuLabel,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp,
                ),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(description, color = MenuSecondary, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The glyph-over-label tile across the top, as in BitChord's Share tile. */
@Composable
public fun MenuTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) MenuPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MenuLabel, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = MenuLabel, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An icon-only action in the menu's top row: no caption, [label] is its description. */
@Composable
private fun MenuIconTile(icon: ImageVector, label: String, tint: Color?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .padding(horizontal = 22.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(if (pressed) MenuPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint ?: MenuLabel, modifier = Modifier.size(29.dp)) }
}

/** The line between the top row's buttons: the same weight as the lines between the rows. */
@Composable
private fun MenuIconDivider() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(MenuSeparator))
}
/** One line of the menu: a fixed icon column, label, optional dimmer subtitle. */

/**
 * BitChord's iOS menu, drawn in place over the player so the panel frosts the
 * player's own backdrop. Trailing edge lines up with the button; a button in
 * the lower half settles at the bottom and grows from its bottom corner.
 */
@Composable
public fun IosMenu(
    visible: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    /** Open right below the anchor (above it when it doesn't fit), instead of settling at the bottom. */
    placeBesideAnchor: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing
    val forced = LocalMenuInsets.current
    // The anchor arrives in screen px, so the overlay's own screen position comes
    // off it: in the player both windows start at zero, in a popup they don't.
    val view = androidx.compose.ui.platform.LocalView.current
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f))
        } else {
            progress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            onFinished()
        }
    }
    androidx.activity.compose.BackHandler(enabled = visible, onBack = onDismiss)
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val at = IntArray(2)
                view.rootView.getLocationOnScreen(at)
                overlayOffset = it.positionInWindow() + Offset(at[0].toFloat(), at[1].toFloat())
            },
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .drawBehind {
                        // The dim fills the scrim, but not the song the menu is for, so it stays clearly visible.
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipRect(0f, 0f, size.width, size.height)
                        drawRect(Color.Black.copy(alpha = 0.60f), size = size)
                        canvas.restore()
                        if (placeBesideAnchor) {
                            val at = anchor?.translate(-overlayOffset) ?: return@drawBehind
                            canvas.save()
                            canvas.clipRect(at.left, at.top, at.right, at.bottom)
                            canvas.restore()
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
            Box(Modifier.shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.30f)))
            Box(Modifier.menuPanel().pointerInputNoScroll()) {
                Column(
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = content,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 14.dp.roundToPx()
        val top = (forced.first + 8.dp.roundToPx()) + insets.getTop(this)
        val bottom = height - (forced.second + 12.dp.roundToPx()) - insets.getBottom(this)
        val scrim = measurables[0].measure(Constraints.fixed(width, height))
        val panelWidth = MenuWidth.roundToPx().coerceAtMost(width - margin * 2)
        val panel = measurables[2].measure(Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (bottom - top).coerceAtLeast(0)))
        val shadowPlate = measurables[1].measure(Constraints.fixed(panel.width, panel.height))
        val bounds = anchor?.translate(-overlayOffset)
        val right = bounds?.right?.roundToInt() ?: (width - margin)
        val x = (right - panel.width).coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin))
        // Page panels sit beside the pressed item: below it when there's room,
        // else above it. The player's menu keeps its own bottom-settling look.
        val gap = 8.dp.roundToPx()
        val lowest = (bottom - panel.height).coerceAtLeast(top)
        val belowFits = bounds != null && bounds.bottom.roundToInt() + gap + panel.height <= bottom
        val aboveFits = bounds != null && bounds.top.roundToInt() - gap - panel.height >= top
        val hangsDown = when {
            placeBesideAnchor && bounds != null -> belowFits || (!aboveFits && bounds.center.y < height / 2f)
            bounds != null -> bounds.center.y < height / 2f
            else -> true
        }
        val y = when {
            placeBesideAnchor && bounds != null && hangsDown -> (bounds.bottom.roundToInt() + gap).coerceIn(top, lowest)
            placeBesideAnchor && bounds != null -> (bounds.top.roundToInt() - gap - panel.height).coerceIn(top, lowest)
            bounds != null && hangsDown -> bounds.top.roundToInt().coerceIn(top, lowest)
            else -> lowest
        }
        layout(width, height) {
            scrim.place(0, 0)
            val growth: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                alpha = progress.value.coerceIn(0f, 1f)
                val s = lerp(0.6f, 1f, progress.value)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, if (hangsDown) 0f else 1f)
            }
            shadowPlate.placeWithLayer(x, y, layerBlock = growth)
            panel.placeWithLayer(x, y) {
                growth()
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}

private fun Modifier.pointerInputNoScroll(): Modifier = this.pointerInput(Unit) { }

/**
 * The song's action rows in the iOS panel, for any screen that long-presses a
 * song: the same rows and motion as the player's menu. The player's own extras
 * ([onUpgradeQuality], [onStartRadio], [onSongInfo], [onSleepTimer],
 * [onLyricsOffset], [onCopyLog]) slot in around the shared rows; leave them null
 * and the panel shows the rows any screen can act on.
 */
@Composable
public fun SongActionsMenu(
    song: Song,
    visible: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    onShare: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    /** Inside one of your own playlists: takes this song out of it. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onUpgradeQuality: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
    onSleepTimer: (() -> Unit)? = null,
    onLyricsOffset: (() -> Unit)? = null,
    onCopyLog: (() -> Unit)? = null,
    /** Page panels open beside the item; the player's menu settles at the bottom. */
    placeBesideAnchor: Boolean = false,
) {
    IosMenu(
        visible = visible,
        anchor = anchor,
        onDismiss = onDismiss,
        onFinished = onFinished,
        placeBesideAnchor = placeBesideAnchor,
    ) {
        MenuPanel(songMenuItems(
            song, onShare, onPlayNext, onAddToQueue, onRemoveFromPlaylist,
            onUpgradeQuality, onStartRadio, onOpenAlbum, onOpenArtist, onSongInfo, onSleepTimer, onLyricsOffset, onCopyLog
        ))
    }
}

/** Builds the shared song-menu item list from song + action slots. */
private fun songMenuItems(
    song: Song,
    onShare: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onUpgradeQuality: (() -> Unit)?,
    onStartRadio: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onSongInfo: (() -> Unit)?,
    onSleepTimer: (() -> Unit)?,
    onLyricsOffset: (() -> Unit)?,
    onCopyLog: (() -> Unit)?,
): List<MenuItem> {
    val result = mutableListOf<MenuItem>()
    // Share tile (only for non-local songs)
    if (!song.videoId.startsWith("local:")) result.add(MenuItem.Tile(SfIcons.Share, "Share", onPick = onShare!!))
    // Separator after share
    result.add(MenuItem.Separator)
    // Upgrade quality
    if (onUpgradeQuality != null) result.add(MenuItem.Row(SfIcons.Sparkles, "Upgrade quality", null, true, onUpgradeQuality!!))
    // Start radio
    if (onStartRadio != null) result.add(MenuItem.Row(SfIcons.Radio, "Start radio", null, true, onStartRadio!!))
    // Play next
    result.add(MenuItem.Row(SfIcons.PlayNext, "Play next", null, true, onPlayNext!!))
    // Add to queue
    result.add(MenuItem.Row(SfIcons.DragHandle, "Add to queue", null, true, onAddToQueue!!))
    // Remove from playlist
    if (onRemoveFromPlaylist != null) result.add(MenuItem.Row(SfIcons.Trash, "Remove from playlist", null, true, onRemoveFromPlaylist!!))
    // Separator before album/artist
    result.add(MenuItem.Separator)
    // Open album
    if (onOpenAlbum != null) result.add(MenuItem.Row(SfIcons.Album, "Open album", song.albumName, true, onOpenAlbum!!))
    // Open artist
    if (onOpenArtist != null) {
        val artistName = song.artist.takeIf { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: "Open artist"
        result.add(MenuItem.Row(SfIcons.Person, "Open artist", artistName, true, onOpenArtist!!))
    }
    // Song info
    if (onSongInfo != null) result.add(MenuItem.Row(SfIcons.Info, "Song Info", null, true, onSongInfo!!))
    if (onSongInfo != null) result.add(MenuItem.Separator)
    // Sleep timer
    if (onSleepTimer != null) result.add(MenuItem.Row(SfIcons.SleepTimer, "Sleep timer", null, true, onSleepTimer!!))
    // Lyrics offset
    if (onLyricsOffset != null) result.add(MenuItem.Row(SfIcons.Equalizer, "Lyrics offset", null, true, onLyricsOffset!!))
    // Copy log
    if (onCopyLog != null) result.add(MenuItem.Row(SfIcons.Bug, "Copy log", null, true, onCopyLog!!))
    return result
}

/** What a long-press on an album or playlist can do; built by the app shell. */
public class CollectionMenuActions(
    public val play: (browseId: String, title: String, shuffle: Boolean) -> Unit,
    public val playNext: (browseId: String) -> Unit,
    public val addToQueue: (browseId: String) -> Unit,
    public val open: (browseId: String, type: BrowseType) -> Unit,
)

public val LocalCollectionMenu: ProvidableCompositionLocal<CollectionMenuActions?> =
    staticCompositionLocalOf<CollectionMenuActions?> { null }

/** Menu items for a collection (album/playlist) card, wired to [LocalCollectionMenu]. */
@Composable
public fun collectionMenu(list: MenuList, onDismiss: () -> Unit = {}): List<MenuItem> {
    val actions = LocalCollectionMenu.current
    val context = LocalContext.current
    val kind = if (list.type == BrowseType.ALBUM) "album" else "playlist"
    return buildList {
        add(MenuItem.Tile(SfIcons.Share, "Share") {
            onDismiss()
            ListShare.shareList(context, list.title, list.browseId, listOf(list.thumbnailUrl), list.subtitle)
        })
        add(MenuItem.Separator)
        if (actions != null) {
            add(MenuItem.Row(SfIcons.PlayNext, "Play next") { onDismiss(); actions.playNext(list.browseId) })
            add(MenuItem.Row(SfIcons.DragHandle, "Add to queue") { onDismiss(); actions.addToQueue(list.browseId) })
            add(MenuItem.Separator)
            add(MenuItem.Row(SfIcons.Album, "Open $kind", list.title) { onDismiss(); actions.open(list.browseId, list.type) })
        }
    }
}

/** Home's playlist/album card menu: Share, Play, Play next, Add to queue, Open, Add to library. */
@Composable
public fun homeCollectionMenu(list: MenuList, onDismiss: () -> Unit = {}): List<MenuItem> {
    val actions = LocalCollectionMenu.current
    val context = LocalContext.current
    val saved by LibraryStore.savedLists.collectAsState()
    val isSaved = saved.any { it.browseId == list.browseId }
    val kind = if (list.type == BrowseType.ALBUM) "album" else "playlist"
    return buildList {
        add(MenuItem.Tile(SfIcons.Share, "Share") {
            onDismiss()
            ListShare.shareList(context, list.title, list.browseId, listOf(list.thumbnailUrl), list.subtitle)
        })
        add(MenuItem.Separator)
        if (actions != null) {
            add(MenuItem.Row(SfIcons.PlayFill, "Play") { onDismiss(); actions.play(list.browseId, list.title, false) })
            add(MenuItem.Row(SfIcons.PlayNext, "Play next") { onDismiss(); actions.playNext(list.browseId) })
            add(MenuItem.Row(SfIcons.DragHandle, "Add to queue") { onDismiss(); actions.addToQueue(list.browseId) })
            add(MenuItem.Row(SfIcons.Album, "Open $kind") { onDismiss(); actions.open(list.browseId, list.type) })
        }
        add(MenuItem.Row(SfIcons.Library, if (isSaved) "Remove from library" else "Add to library") {
            onDismiss()
            LibraryStore.setSaved(SavedList(list.browseId, list.title, list.subtitle.orEmpty(), list.thumbnailUrl), !isSaved)
        })
    }
}

