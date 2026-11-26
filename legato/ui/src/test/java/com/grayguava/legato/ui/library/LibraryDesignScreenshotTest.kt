package com.grayguava.legato.ui.library

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grayguava.legato.ui.components.PageHeader
import com.grayguava.legato.ui.components.glass
import com.grayguava.legato.ui.icons.SfIcons
import com.grayguava.legato.ui.theme.LegatoTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Three candidate layouts for Library, drawn with real components and SF
 * glyphs, as pictures to choose from. Not a product test: it asserts nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class LibraryDesignScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Shelf(val title: String, val songs: Int)

    private val lists = listOf(
        Shelf("Lub", 38),
        Shelf("Old Hindi Romantic Songs", 120),
        Shelf("Throwback Workout", 64),
        Shelf("Late night drive", 27),
        Shelf("Punjabi hits", 88),
        Shelf("Ghazals", 41),
    )

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            LegatoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.waitForIdle()
        save(name, topWindow() ?: compose.activity.window.decorView)
    }

    // ---- A: collection cards over a playlist shelf -------------------------

    @Test fun shelf() = shoot("library-design-a-shelf") {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item { PageHeader("Library", actions = { HeaderPill() }) }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CollectionCard("Liked Music", "128 songs", SfIcons.HeartFill, listOf(Color(0xFF8C6AF0), Color(0xFFF95AAE)), Modifier.weight(1f))
                    CollectionCard("Replay", "Your 2026", SfIcons.Chart, listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)), Modifier.weight(1f))
                    CollectionCard("Recents", "12 played", SfIcons.History, listOf(Color(0xFF1F5F8B), Color(0xFF102A3C)), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.glass(CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(SfIcons.Filter, null, Modifier.size(13.dp))
                        Text("Recent", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Text("See all", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            items((lists.size + 1) / 2) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    lists.drop(row * 2).take(2).forEach { list ->
                        Column(Modifier.weight(1f)) {
                            Cover(lists.indexOf(list), Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
                            Text(list.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.songs} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // ---- B: grouped glass index over playlist rows -------------------------

    @Test fun index() = shoot("library-design-b-index") {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item { PageHeader("Library", actions = { HeaderPill() }) }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).glass(RoundedCornerShape(26.dp)).padding(vertical = 6.dp),
                ) {
                    IndexRow(SfIcons.HeartFill, "Liked Music", "128 songs", Color(0xFFF95AAE))
                    IndexRow(SfIcons.Chart, "Replay", "Top songs, artists and albums", Color(0xFFF0A24A))
                    IndexRow(SfIcons.History, "Listening history", "Recently played", Color(0xFF4A9DF0))
                    IndexRow(SfIcons.Smartphone, "On this phone", "14 local files", Color(0xFF7C8CFF))
                    IndexRow(SfIcons.Storage, "Cached songs", "312 kept on this phone", Color(0xFF5AC8A0))
                    IndexRow(SfIcons.Server, "Music server", "Subsonic, lossless", Color(0xFFB08CFF), last = true)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("See all", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(lists.take(5)) { list ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(lists.indexOf(list), Modifier.size(52.dp), 12.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(list.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.songs} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(SfIcons.ChevronDown, null, Modifier.size(16.dp).rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // ---- C: a hero card over a cover wall ----------------------------------

    @Test fun wall() = shoot("library-design-c-wall") {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item { PageHeader("Library", actions = { HeaderPill() }) }
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(172.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8C6AF0), Color(0xFFF95AAE)))),
                ) {
                    Icon(SfIcons.HeartFill, null, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(132.dp), tint = Color.White.copy(alpha = 0.22f))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text("Liked Music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("128 songs · plays first", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Replay", SfIcons.Chart, Modifier.weight(1f))
                    Chip("Recents", SfIcons.History, Modifier.weight(1f))
                    Chip("On phone", SfIcons.Smartphone, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Icon(SfIcons.Grid, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items((lists.size + 1) / 2) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    lists.drop(row * 2).take(2).forEach { list ->
                        Column(Modifier.weight(1f)) {
                            Cover(lists.indexOf(list), Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                            Text(list.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.songs} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // ---- D/E: five named cards over a square playlist grid -----------------

    @Test fun fiveCardsMono() = shoot("library-design-d-five-mono") { FiveCardPage(washed = false) }

    @Test fun fiveCardsWashed() = shoot("library-design-e-five-washed") { FiveCardPage(washed = true) }

    @Test fun washedMid() = shoot("library-design-f-washed-mid") { FiveCardPage(washed = true, mid = true) }

    @Test fun washedGlow() = shoot("library-design-f-washed-glow") { FiveCardPage(washed = true, glow = true) }

    @Test fun washedVertical() = shoot("library-design-f-washed-vertical") { FiveCardPage(washed = true, vertical = true) }

    @Composable private fun FiveCardPage(washed: Boolean, mid: Boolean = false, glow: Boolean = false, vertical: Boolean = false) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item { PageHeader("Library", actions = { HeaderPill() }) }
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(172.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8C6AF0), Color(0xFFF95AAE)))),
                ) {
                    Icon(SfIcons.HeartFill, null, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(132.dp), tint = Color.White.copy(alpha = 0.22f))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text("Liked Music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("128 songs · plays first", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            val cards = listOf(
                Triple("Replay", "Your top songs · 2026", SfIcons.Chart) to (Color(0xFFF0A24A) to Color(0xFF2C1E0E)),
                Triple("Recents", "12 played this week", SfIcons.History) to (Color(0xFF4A9DF0) to Color(0xFF10202E)),
                Triple("On this phone", "14 local files", SfIcons.Smartphone) to (Color(0xFF7C8CFF) to Color(0xFF171932)),
                Triple("Cached songs", "312 kept on this phone", SfIcons.Storage) to (Color(0xFF5AC8A0) to Color(0xFF0E241C)),
            )
            for (row in 0 until 2) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        cards.drop(row * 2).take(2).forEachIndexed { i, (card, colors) ->
                            NamedCard(card.first, card.second, card.third, colors, washed, Modifier.weight(1f), mid = mid, glow = glow, vertical = vertical)
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.height(34.dp).glass(CircleShape).padding(start = 12.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+", Modifier.padding(end = 5.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("New playlist", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            items((lists.size + 1) / 2) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    lists.drop(row * 2).take(2).forEach { list ->
                        Column(Modifier.weight(1f)) {
                            Cover(lists.indexOf(list), Modifier.fillMaxWidth().aspectRatio(1f), 16.dp)
                            Text(list.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.songs} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    /** One of the four small cards: its name and count inside, bottom left. */
    @Composable private fun NamedCard(title: String, subtitle: String, icon: ImageVector, colors: Pair<Color, Color>, washed: Boolean, modifier: Modifier, mid: Boolean = false, glow: Boolean = false, vertical: Boolean = false) {
        val dark = colors.second
        Box(
            modifier.height(128.dp).clip(RoundedCornerShape(18.dp))
                .then(
                    if (washed) Modifier.background(washedBrush(listOf(colors.first, dark), mid, glow, vertical))
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ),
        ) {
            Icon(
                icon, null,
                Modifier.align(Alignment.TopEnd).padding(if (glow) 16.dp else 10.dp).size(if (glow) 88.dp else if (washed) 74.dp else 26.dp),
                tint = if (washed) Color.White.copy(alpha = if (glow) 0.55f else 0.18f) else colors.first,
            )
            Column(Modifier.align(Alignment.BottomStart).padding(if (glow) 18.dp else 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = if (washed) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = if (washed) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    /** A washed gradient: the colour running into its dark end, with the option of a mid tone, a glow, or a vertical wash. */
    private fun washedBrush(colors: List<Color>, mid: Boolean, glow: Boolean, vertical: Boolean): Brush = when {
        glow -> Brush.radialGradient(listOf(colors[0], colors[1]))
        vertical -> Brush.linearGradient(colors)
        mid -> Brush.linearGradient(listOf(colors[0], androidx.compose.ui.graphics.lerp(colors[0], colors[1], 0.45f), colors[1]))
        else -> Brush.linearGradient(colors)
    }

    // ---- pieces ------------------------------------------------------------

    @Composable private fun HeaderPill() {
        Row(Modifier.height(54.dp).glass(CircleShape).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(SfIcons.History, "History", Modifier.size(21.dp)) }
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(SfIcons.Account, "Account", Modifier.size(30.dp)) }
        }
    }

    @Composable private fun CollectionCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, modifier: Modifier) {
        Column(modifier) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors))) {
                Icon(icon, null, Modifier.align(Alignment.Center).size(38.dp), tint = Color.White.copy(alpha = 0.92f))
            }
            Text(title, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }

    @Composable private fun IndexRow(icon: ImageVector, title: String, subtitle: String, tint: Color, last: Boolean = false) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.22f)), Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = tint)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(SfIcons.ChevronDown, null, Modifier.size(15.dp).rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        if (!last) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)))
    }

    @Composable private fun Chip(label: String, icon: ImageVector, modifier: Modifier) {
        Row(
            modifier.height(38.dp).glass(CircleShape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(15.dp))
            Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }

    @Composable private fun Cover(index: Int, modifier: Modifier, corner: Dp) {
        val pairs = listOf(
            Color(0xFF2E3A59) to Color(0xFF151A26),
            Color(0xFF5B2E52) to Color(0xFF231019),
            Color(0xFF2B4A3F) to Color(0xFF101E19),
            Color(0xFF4A3A22) to Color(0xFF1C150C),
            Color(0xFF3A2B52) to Color(0xFF171020),
            Color(0xFF23414A) to Color(0xFF0E1B1F),
        )
        val (a, b) = pairs[index % pairs.size]
        Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, b))), Alignment.Center) {
            Icon(SfIcons.MusicNote, null, Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.25f))
        }
    }

    private fun topWindow(): View? = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(global.getMethod("getInstance").invoke(null)) as List<*>
        views.filterIsInstance<View>().lastOrNull { it.isAttachedToWindow }
    }.getOrNull()

    private fun save(name: String, view: View) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
