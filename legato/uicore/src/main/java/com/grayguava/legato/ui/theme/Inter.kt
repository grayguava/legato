package com.grayguava.legato.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.grayguava.legato.uicore.R

/*
 * Inter, in the two cuts that mirror Apple's own typeface split: the plain
 * cut where SF Pro Text is read, the Display cut where SF Pro Display is.
 * Body text and labels take the plain cut; the display, headline and title
 * styles take Display, which is drawn tighter and higher-contrast for the
 * large sizes it is read at.
 *
 * As in Apple's scale, a FontWeight here names the step in the scale, not
 * the cut it draws: W500 draws Regular, W600 draws Medium, W700 draws
 * SemiBold, W800 draws Bold. Compose synthesises nothing, so a weight
 * outside these falls back to the nearest one listed.
 */
public val InterText: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_regular, FontWeight.W500),
    Font(R.font.inter_medium, FontWeight.W600),
    Font(R.font.inter_semibold, FontWeight.W700),
    Font(R.font.inter_bold, FontWeight.W800),
)

public val InterDisplay: FontFamily = FontFamily(
    Font(R.font.interdisplay_regular, FontWeight.W400),
    Font(R.font.interdisplay_regular, FontWeight.W500),
    Font(R.font.interdisplay_medium, FontWeight.W600),
    Font(R.font.interdisplay_semibold, FontWeight.W700),
    // W800 is the scale step the lyric lines are set at; there it draws
    // ExtraBold, the weight Apple Music sets its lyrics at.
    Font(R.font.interdisplay_extrabold, FontWeight.W800),
)
