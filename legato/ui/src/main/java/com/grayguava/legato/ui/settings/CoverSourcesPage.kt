package com.grayguava.legato.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.CoverSettings
import com.grayguava.legato.settings.CoverSource
import com.grayguava.legato.settings.CoverSourceEntry
import com.grayguava.legato.ui.components.AppSwitch
import com.grayguava.legato.ui.components.GroupCard
import com.grayguava.legato.ui.components.RowDivider
import com.grayguava.legato.ui.components.SettingRow
import com.grayguava.legato.ui.components.ToggleRow
import com.grayguava.legato.ui.icons.SfIcons
import kotlin.math.roundToInt

/** A source row's height, in dp; the drag maths moves rows by exactly this much. */
private val ROW = 68.dp

/**
 * Where sharper album covers come from, in the order they're tried: every
 * catalogue with its own switch, and a handle to drag it up or down the list.
 *
 * YouTube Music's row has no switch, because it isn't a catalogue: it stands
 * for the song's own art, and its place in the order is the rule. Above it a
 * source may replace that art; below it, nothing can.
 */
@Composable
internal fun CoverSourcesPage() {
    val settings by AppSettings.covers.collectAsState()
    val ui by AppSettings.ui.collectAsState()
    val rowPx = with(LocalDensity.current) { ROW.toPx() }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val order = settings.ordered
    val latest by rememberUpdatedState(order)

    fun save(list: List<CoverSourceEntry>) = AppSettings.updateCovers { it.copy(sources = list) }

    Column {
        GroupCard {
            ToggleRow(
                "Sharper album covers",
                ui.albumCovers,
                { v -> AppSettings.updateUi { it.copy(albumCovers = v) } },
                summary = "The player shows the album's cover from the sources below, sharper than YouTube's and in place of a video frame",
                icon = SfIcons.Album,
            )
        }
        Text(
            "Tried in this order. Drag a source by its handle to move it; the first source with a cover wins, " +
                "and YouTube Music is the song's own art: a source above it may replace that art, one below it never will.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        )
        GroupCard {
            order.forEachIndexed { i, entry ->
                if (i > 0) RowDivider()
                // Keyed by source, so a row keeps its drag as it changes place.
                key(entry.source) {
                    val isDragged = dragged == i
                    val lift by animateFloatAsState(if (isDragged) 1.03f else 1f, label = "lift")
                    val switchable = entry.source != CoverSource.YOUTUBE_MUSIC
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) offset else 0f
                                scaleX = lift
                                scaleY = lift
                            }
                            .then(
                                if (switchable) Modifier.clickable {
                                    save(order.mapIndexed { j, e -> if (j == i) e.copy(enabled = !e.enabled) else e })
                                } else Modifier,
                            )
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SfIcons.DragHandle,
                            "Drag to reorder",
                            Modifier
                                .size(23.dp)
                                .pointerInput(entry.source) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragged = latest.indexOfFirst { it.source == entry.source }
                                            offset = 0f
                                        },
                                        onDragEnd = { dragged = null; offset = 0f },
                                        onDragCancel = { dragged = null; offset = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        val at = dragged ?: return@detectDragGestures
                                        offset += amount.y
                                        val target = (at + (offset / rowPx).roundToInt()).coerceIn(0, latest.lastIndex)
                                        if (target != at) {
                                            save(latest.toMutableList().apply { add(target, removeAt(at)) })
                                            offset -= (target - at) * rowPx
                                            dragged = target
                                        }
                                    }
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 26.dp)) {
                            Text(entry.source.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                entry.source.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (switchable) {
                            AppSwitch(entry.enabled, { v ->
                                save(order.mapIndexed { j, e -> if (j == i) e.copy(enabled = v) else e })
                            })
                        }
                    }
                }
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) {
            SettingRow("Reset the order", summary = "Back to the default sources, all switched on", icon = SfIcons.Restart, onClick = {
                val defaults = CoverSettings()
                AppSettings.updateCovers { it.copy(sources = defaults.sources) }
            })
        }
    }
}
