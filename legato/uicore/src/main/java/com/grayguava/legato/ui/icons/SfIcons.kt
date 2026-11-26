package com.grayguava.legato.ui.icons

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFAirpods
import com.composables.sfsymbols.monochrome.SFPersonCropCircleFill
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeadphones
import com.composables.sfsymbols.monochrome.SFPauseCircle
import com.composables.sfsymbols.monochrome.SFSmartphone
import com.composables.sfsymbols.monochrome.SFSquareStack
import com.composables.sfsymbols.monochrome.SFSquareStackFill
import com.composables.sfsymbols.monochrome.SFHouse
import com.composables.sfsymbols.monochrome.SFArrowLeft
import com.composables.sfsymbols.monochrome.SFHouseFill
import com.composables.sfsymbols.monochrome.SFSafari
import com.composables.sfsymbols.monochrome.SFSafariFill
import com.composables.sfsymbols.monochrome.SFFigureWalkMotion
import com.composables.sfsymbols.monochrome.SFBattery100percent
import com.composables.sfsymbols.monochrome.SFLadybug
import com.composables.sfsymbols.monochrome.SFCellularbars
import com.composables.sfsymbols.monochrome.SFChartBar
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.composables.sfsymbols.monochrome.SFChevronDown
import com.composables.sfsymbols.monochrome.SFChevronForward
import com.composables.sfsymbols.monochrome.SFXmark
import com.composables.sfsymbols.monochrome.SFChevronLeftForwardslashChevronRight
import com.composables.sfsymbols.monochrome.SFCompassDrawing
import com.composables.sfsymbols.monochrome.SFDockRectangle
import com.composables.sfsymbols.monochrome.SFLine3Horizontal
import com.composables.sfsymbols.monochrome.SFDrop
import com.composables.sfsymbols.monochrome.SFDropHalffull
import com.composables.sfsymbols.monochrome.SFSliderVertical3
import com.composables.sfsymbols.monochrome.SFLine3HorizontalDecreaseCircle
import com.composables.sfsymbols.monochrome.SFFolder
import com.composables.sfsymbols.monochrome.SFForwardEnd
import com.composables.sfsymbols.monochrome.SFGaugeWithDotsNeedle67percent
import com.composables.sfsymbols.monochrome.SFGear
import com.composables.sfsymbols.monochrome.SFCircleLefthalfFilled
import com.composables.sfsymbols.monochrome.SFSquareGrid2x2
import com.composables.sfsymbols.monochrome.SFEyeSlash
import com.composables.sfsymbols.monochrome.SFClockArrowTriangleheadCounterclockwiseRotate90
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFKeyboard
import com.composables.sfsymbols.monochrome.SFMusicNoteHouse
import com.composables.sfsymbols.monochrome.SFQuoteBubble
import com.composables.sfsymbols.monochrome.SFMusicNote
import com.composables.sfsymbols.monochrome.SFMusicNoteSlash
import com.composables.sfsymbols.monochrome.SFPaintpalette
import com.composables.sfsymbols.monochrome.SFPerson
import com.composables.sfsymbols.monochrome.SFPersonBadgePlus
import com.composables.sfsymbols.monochrome.SFIphone
import com.composables.sfsymbols.monochrome.SFPlayCircle
import com.composables.sfsymbols.monochrome.SFPlus
import com.composables.sfsymbols.monochrome.SFCheckmarkSeal
import com.composables.sfsymbols.monochrome.SFArrowClockwise
import com.composables.sfsymbols.monochrome.SFBackwardFill
import com.composables.sfsymbols.monochrome.SFForwardFill
import com.composables.sfsymbols.monochrome.SFMagnifyingglass
import com.composables.sfsymbols.monochrome.SFMoonZzz
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFPlayFill
import com.composables.sfsymbols.monochrome.SFRadio
import com.composables.sfsymbols.monochrome.SFServerRack
import com.composables.sfsymbols.monochrome.SFSquareAndArrowUp
import com.composables.sfsymbols.monochrome.SFSparkles
import com.composables.sfsymbols.monochrome.SFSpeakerWave2
import com.composables.sfsymbols.monochrome.SFRectangleSplit2x1
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFInternaldrive
import com.composables.sfsymbols.monochrome.SFHifispeaker
import com.composables.sfsymbols.monochrome.SFIpad
import com.composables.sfsymbols.monochrome.SFTextformat
import com.composables.sfsymbols.monochrome.SFTextInsert
import com.composables.sfsymbols.monochrome.SFTrash
import com.composables.sfsymbols.monochrome.SFCableConnector
import com.composables.sfsymbols.monochrome.SFWaveform
import com.composables.sfsymbols.monochrome.SFWaveformBadgeExclamationmark
import com.composables.sfsymbols.monochrome.SFWifi

/**
 * Apple's SF Symbols, as Compose vectors, for the settings and the menus.
 *
 * The library draws the Regular weight, which is too thin beside the rest of
 * the app, so every symbol is drawn again with a stroke of its own colour:
 * a filled shape's outline, stroked in the same colour, grows the shape by
 * half the stroke on every side, which takes Regular to about Bold.
 *
 * The library also cuts each symbol to its own bounds, so a square gear fills
 * whatever box it is given while a wide glyph shrinks to fit its width, and a
 * row of them reads as a jumble of sizes. Here every symbol goes onto one
 * square canvas at the size it was drawn, centred, the way Apple sets them.
 */
public object SfIcons {
    // ---- The symbols, named for what they are ----
    val Account: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPersonCropCircleFill) }
    val Airpods: ImageVector by lazy { bold(SfSymbols.Monochrome.SFAirpods) }
    val Album: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStack) }
    val Back: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowLeft) }
    val BackwardFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFBackwardFill) }
    val AlbumFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStackFill) }
    val Animation: ImageVector by lazy { bold(SfSymbols.Monochrome.SFFigureWalkMotion) }
    val Battery: ImageVector by lazy { bold(SfSymbols.Monochrome.SFBattery100percent) }
    val Bug: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLadybug) }
    val Cellular: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCellularbars) }
    val Chart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChartBar) }
    val Check: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmark) }
    val ChevronDown: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronDown) }
    val ChevronForward: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronForward) }
    val Close: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val Code: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronLeftForwardslashChevronRight) }
    val Compass: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCompassDrawing) }
    val Dock: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDockRectangle) }
    val DragHandle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLine3Horizontal) }
    val Drop: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDrop) }
    val DropHalf: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDropHalffull) }
    val Equalizer: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSliderVertical3) }
    val Filter: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLine3HorizontalDecreaseCircle) }
    val Folder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFFolder) }
    val Forward: ImageVector by lazy { bold(SfSymbols.Monochrome.SFForwardEnd) }
    val ForwardFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFForwardFill) }
    val Gauge: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGaugeWithDotsNeedle67percent) }
    val Gradient: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCircleLefthalfFilled) }
    val Grid: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareGrid2x2) }
    val Headphones: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeadphones) }
    val Heart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeart) }
    val HeartFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeartFill) }
    val Hidden: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEyeSlash) }
    val History: ImageVector by lazy { bold(SfSymbols.Monochrome.SFClockArrowTriangleheadCounterclockwiseRotate90) }
    val House: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHouse) }
    val HouseFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHouseFill) }
    val Info: ImageVector by lazy { bold(SfSymbols.Monochrome.SFInfoCircle) }
    val Keyboard: ImageVector by lazy { bold(SfSymbols.Monochrome.SFKeyboard) }
    val Library: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNoteHouse) }
    val Lyrics: ImageVector by lazy { bold(SfSymbols.Monochrome.SFQuoteBubble) }
    val MusicNote: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNote) }
    val MusicOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNoteSlash) }
    val Palette: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPaintpalette) }
    val Person: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPerson) }
    val PersonAdd: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPersonBadgePlus) }
    val PauseCircle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPauseCircle) }
    val PauseFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPauseFill) }
    val Phone: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIphone) }
    val PlayCircle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlayCircle) }
    val PlayFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlayFill) }
    val Plus: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlus) }
    val Quality: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmarkSeal) }
    val Restart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowClockwise) }
    val Safari: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSafari) }
    val SafariFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSafariFill) }
    val Search: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMagnifyingglass) }
    val Server: ImageVector by lazy { bold(SfSymbols.Monochrome.SFServerRack) }
    val Settings: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGear) }
    val Smartphone: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSmartphone) }
    val Sparkles: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSparkles) }
    val Speaker: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSpeakerWave2) }
    val Split: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRectangleSplit2x1) }
    val Star: ImageVector by lazy { bold(SfSymbols.Monochrome.SFStar) }
    val Share: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareAndArrowUp) }
    val Radio: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRadio) }
    val PlayNext: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextInsert) }
    val SleepTimer: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMoonZzz) }
    val Storage: ImageVector by lazy { bold(SfSymbols.Monochrome.SFInternaldrive) }
    val Surround: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHifispeaker) }
    val Tablet: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIpad) }
    val TextFormat: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextformat) }
    val Trash: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTrash) }
    val Usb: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCableConnector) }
    val Waveform: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWaveform) }
    val WaveformBadgeExclamationmark: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWaveformBadgeExclamationmark) }
    val Wifi: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWifi) }

    /**
     * How much line every symbol grows by, in the symbols' own units: Regular's
     * ~1.7 to Bold's ~2.6.
     */
    private const val BOLD_STROKE = 0.9f

    /**
     * The canvas every symbol shares, in the symbols' own units. Apple sets
     * them all at one point size; this is the same idea.
     */
    private const val CANVAS = 31f

    /** A [width]x[height] drawing, in symbol units, centred on the shared canvas. */
    private fun canvas(
        name: String,
        width: Float,
        height: Float,
        autoMirror: Boolean = false,
        content: ImageVector.Builder.() -> Unit,
    ): ImageVector {
        val fit = minOf(1f, CANVAS / width, CANVAS / height)
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = CANVAS,
            viewportHeight = CANVAS,
            autoMirror = autoMirror,
        ).apply {
            addGroup(
                translationX = (CANVAS - width * fit) / 2f,
                translationY = (CANVAS - height * fit) / 2f,
                scaleX = fit,
                scaleY = fit,
            )
            content()
            clearGroup()
        }.build()
    }

    /** Grown by half the stroke on every side, which the added line reaches into. */
    private fun bold(regular: ImageVector): ImageVector {
        val pad = BOLD_STROKE / 2f
        return canvas(
            name = regular.name + ".bold",
            width = regular.viewportWidth + pad * 2f,
            height = regular.viewportHeight + pad * 2f,
            autoMirror = regular.autoMirror,
        ) {
            addGroup(translationX = pad, translationY = pad)
            copyInto(this, regular.root)
            clearGroup()
        }
    }

    /**
     * Adds every node of [group] to [builder], each fully opaque and traced
     * with [extra] more line - Bold's by default. Give it Apple's own Regular
     * vectors: a symbol already made bold here would be thickened twice.
     */
    private fun copyInto(builder: ImageVector.Builder, group: VectorGroup, extra: Float = BOLD_STROKE) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        name = node.name,
                        rotate = node.rotation,
                        pivotX = node.pivotX,
                        pivotY = node.pivotY,
                        scaleX = node.scaleX,
                        scaleY = node.scaleY,
                        translationX = node.translationX,
                        translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    copyInto(builder, node, extra)
                    builder.clearGroup()
                }
                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = 1f,
                    // A filled shape's outline traced in its own colour: every
                    // line grows by the stroke's width, half on each side.
                    stroke = node.stroke ?: node.fill,
                    strokeAlpha = 1f,
                    strokeLineWidth = node.strokeLineWidth + extra,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset,
                )
            }
        }
    }

    /** The same symbols at Regular weight: no line grown on. */
    private fun thin(regular: ImageVector): ImageVector = canvas(
        name = regular.name + ".thin",
        width = regular.viewportWidth,
        height = regular.viewportHeight,
        autoMirror = regular.autoMirror,
    ) {
        addGroup(translationX = 0f, translationY = 0f)
        copyInto(this, regular.root, extra = 0f)
        clearGroup()
    }

    val ThinHeartFill: ImageVector by lazy { thin(SfSymbols.Monochrome.SFHeartFill) }
    val ThinChart: ImageVector by lazy { thin(SfSymbols.Monochrome.SFChartBar) }
    val ThinHistory: ImageVector by lazy { thin(SfSymbols.Monochrome.SFClockArrowTriangleheadCounterclockwiseRotate90) }
    val ThinSmartphone: ImageVector by lazy { thin(SfSymbols.Monochrome.SFSmartphone) }
    val ThinStorage: ImageVector by lazy { thin(SfSymbols.Monochrome.SFInternaldrive) }
    val ThinServer: ImageVector by lazy { thin(SfSymbols.Monochrome.SFServerRack) }
    val ThinPlus: ImageVector by lazy { thin(SfSymbols.Monochrome.SFPlus) }
    val ThinRestart: ImageVector by lazy { thin(SfSymbols.Monochrome.SFArrowClockwise) }
}
