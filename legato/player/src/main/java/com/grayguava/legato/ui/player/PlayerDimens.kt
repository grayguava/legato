package com.grayguava.legato.ui.player

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The player's rhythm: the gap between the blocks under the cover, and where the
 * format line and the seek bar sit. One object, so a gap is one change — or one
 * number of the page height, for a different screen.
 */
public data class PlayerDimens(
    public val gap: Dp = 20.dp,
    public val tight: Dp = 18.dp,
    public val seekLift: Dp = 20.dp,
    public val statsInset: Dp = 8.dp,
    /** Room kept under the cover, so the art ends a little above the title. */
    public val coverInset: Dp = 12.dp,
) {
    /** The gap under the seek bar, measured back from [gap] so its lift doesn't widen it. */
    public val afterSeek: Dp
        get() = (gap - seekLift).coerceAtLeast(0.dp)
}

public val LocalPlayerDimens: CompositionLocal<PlayerDimens> = staticCompositionLocalOf { PlayerDimens() }

/** Scales the rhythm from the screen: a unit of 18% of the page height, clamped, font-scaled. */
public fun playerDimens(density: androidx.compose.ui.unit.Density, fontScale: Float, pageHeightPx: Float): PlayerDimens {
    fun dpOf(px: Float): Dp = Dp(((px * fontScale) / density.density).toInt().toFloat())
    val unit = (pageHeightPx * 0.018f * fontScale).coerceIn(12f, 22f)
    return PlayerDimens(gap = dpOf(unit), tight = dpOf(unit * 0.9f), seekLift = dpOf(unit), statsInset = dpOf(unit * 0.4f), coverInset = dpOf(unit * 0.6f))
}
