package com.grayguava.legato.ui.components

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grayguava.legato.base.model.BrowseType
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.isYouTubeId
import kotlin.math.roundToInt

/**
 * An album or playlist to open a long-press menu for.
 */
public data class MenuList(
    public val browseId: String,
    public val title: String,
    public val subtitle: String?,
    public val thumbnailUrl: String?,
    public val type: BrowseType
)

/**
 * Anchors a menu to an item's on-screen rectangle.
 * Used by [SongListItem] and [ItemCard] rows to place the menu.
 */
public class MenuAnchor {
    public var rect: Rect? = null
}

/** Reports this element's place on the screen to [anchor]. */
public fun Modifier.menuAnchor(anchor: MenuAnchor, view: View): Modifier = onGloballyPositioned { c ->
    val at = IntArray(2)
    view.rootView.getLocationOnScreen(at)
    anchor.rect = c.boundsInWindow().translate(at[0].toFloat(), at[1].toFloat())
}