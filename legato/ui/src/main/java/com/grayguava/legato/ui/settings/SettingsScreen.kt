package com.grayguava.legato.ui.settings

import android.content.Context
import com.grayguava.legato.ui.components.launchSafely
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.SingletonImageLoader
import com.grayguava.legato.ui.BuildConfig
import com.grayguava.legato.library.LogExport
import com.grayguava.legato.library.account.AccountStore
import com.grayguava.legato.library.history.History
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.source.local.LocalMusic
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.AudioQuality
import com.grayguava.legato.settings.Recommender
import com.grayguava.legato.settings.RemixPreset
import com.grayguava.legato.settings.VolumeLevel
import com.grayguava.legato.audio.sponsorblock.SponsorBlock
import com.grayguava.legato.audio.playback.AudioCache
import com.grayguava.legato.audio.playback.BitPerfectUsb
import com.grayguava.legato.ui.components.AppSwitch
import com.grayguava.legato.ui.components.Artwork
import com.grayguava.legato.ui.components.ChoiceSheet
import com.grayguava.legato.ui.components.FloatingDialog
import com.grayguava.legato.ui.components.GroupCard
import com.grayguava.legato.ui.components.GroupLabel
import com.grayguava.legato.ui.components.NavRow
import com.grayguava.legato.ui.components.PageHeader
import com.grayguava.legato.ui.components.PillSegmented
import com.grayguava.legato.ui.components.RowDivider
import com.grayguava.legato.ui.components.SettingRow
import com.grayguava.legato.ui.components.SheetButton
import com.grayguava.legato.ui.components.SheetTone
import com.grayguava.legato.ui.components.GlassBackground
import com.grayguava.legato.ui.components.GlassIconButton
import com.grayguava.legato.ui.components.ToggleRow
import com.grayguava.legato.ui.components.liquidGlassSupported
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.grayguava.legato.ui.icons.SfIcons

/** One searchable row: what it's called and the words it can be found by. */
private class Entry(val title: String, val summary: String = "", val content: @Composable () -> Unit)

private class Section(val title: String, val entries: List<Entry>)

/** A setting placed on its page: the category it's on and the subsection under it. */
private class Placed(val entry: Entry, val category: SettingsCategory, val group: String)

/**
 * Settings as a short list of categories, each opening its own page of
 * subsections (see [SETTINGS_CATEGORIES]). Searching from the main page looks
 * through every setting and shows where each one lives.
 */
@Composable
public fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenReplay: () -> Unit,
    onOpenWrapped: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onOpenRemix: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Search waits behind its icon in the header; opened with a tap, left with Cancel or back.
    var searching by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val signedIn by AccountStore.signedIn.collectAsState()
    val account by AccountStore.account.collectAsState()
    val sections = settingsSections(onOpenEqualizer, onOpenReplay, onOpenWrapped, onSignIn, onOpenRemix) { openKey = it }
    val placed = remember(sections) { place(sections) }
    val open = SETTINGS_CATEGORIES.firstOrNull { it.key == openKey }
    androidx.activity.compose.BackHandler(enabled = open != null) { openKey = null }
    androidx.activity.compose.BackHandler(enabled = open == null && searching) {
        searching = false
        query = ""
        keyboard?.hide()
    }
    val q = query.trim()
    // Kept out here, so coming back from a page finds the main list where it was left.
    val mainList = androidx.compose.foundation.lazy.rememberLazyListState()
    // Which folded subsections have been opened with "See more".

    // The layer the switches and sliders bend: the page behind them, never the
    // content itself, which would make them draw the layer they're part of.
    GlassBackground(
        background = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) },
    ) {
        androidx.compose.animation.AnimatedContent(
            open,
            transitionSpec = {
                val forward = targetState != null
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { if (forward) it / 8 else -it / 8 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
            },
            label = "settingsPage",
        ) { page ->
            LazyColumn(
                state = if (page == null) mainList else androidx.compose.foundation.lazy.rememberLazyListState(),
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (page != null) {
                    item { PageHeader(page.title, onBack = { openKey = null }) }
                    if (page.key == "sources") {
                        // Sources carry their own switch and drag handle, so the page
                        // is drawn as one card of live rows rather than placed settings.
                        item(key = "sources") { LyricsSourcesPage() }
                    } else if (page.key == "covers") {
                        item(key = "covers") { CoverSourcesPage() }
                    } else {
                        placed.filter { it.category == page }.groupBy { it.group }.forEach { (group, rows) ->
                            item(key = "label:$group") {
                                // A group with no name still parts the cards a little.
                                if (group.isBlank()) Spacer(Modifier.height(24.dp)) else GroupLabel(group)
                            }
                            item(key = "card:$group") {
                                GroupCard(Modifier.animateContentSize(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))) {
                                    rows.forEachIndexed { i, row ->
                                        if (i > 0) RowDivider()
                                        row.entry.content()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { PageHeader("Settings", onBack = onBack, actions = {
                        if (!searching) {
                            GlassIconButton(SfIcons.Search, "Search settings", { searching = true })
                        }
                    }) }
                    if (searching) {
                        item(key = "search") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val focus = androidx.compose.ui.focus.FocusRequester()
                                LaunchedEffect(Unit) {
                                    focus.requestFocus()
                                    keyboard?.show()
                                }
                                TextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    placeholder = { Text("Search settings") },
                                    leadingIcon = { Icon(SfIcons.Search, null) },
                                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(SfIcons.Close, "Clear") } },
                                    shape = RoundedCornerShape(7.5.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    ),
                                    modifier = Modifier.weight(1f).focusRequester(focus),
                                )
                                Text(
                                    "Cancel",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp).clickable {
                                        searching = false
                                        query = ""
                                        keyboard?.hide()
                                    },
                                )
                            }
                        }
                    }
                    if (q.isNotEmpty()) {
                        val hits = placed.filter {
                            it.entry.title.contains(q, true) || it.entry.summary.contains(q, true) ||
                                it.group.contains(q, true) || it.category.title.contains(q, true)
                        }
                        if (hits.isEmpty()) {
                            item { Text("No settings match \"$q\".", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
                        }
                        hits.groupBy { if (it.group.isBlank()) it.category.title else "${it.category.title} · ${it.group}" }.forEach { (where, rows) ->
                            item(key = "hit:$where") { GroupLabel(where) }
                            item(key = "hitcard:$where") {
                                GroupCard {
                                    rows.forEachIndexed { i, row ->
                                        if (i > 0) RowDivider()
                                        row.entry.content()
                                    }
                                }
                            }
                        }
                    } else {
                        // The main card: every page SETTINGS_HOME lists, in its order.
                        // Accounts, Playback, Player and Lyrics, Look and feel unfold
                        // their pages below them instead of taking rows of their own.
                        item(key = "homecard") {
                            GroupCard(Modifier.padding(top = 16.dp)) {
                                SETTINGS_HOME.flatMap { (_, ks) -> ks }.let { keys ->
                                    var started = false
                                    keys.forEach { key ->
                                        when (key) {
                                        "account" -> FoldRow(
                                            "Accounts and services",
                                            SfIcons.Person,
                                            listOf(
                                                Fold("Manage accounts", SfIcons.Account) { openKey = "account" },
                                                Fold("Listening history", SfIcons.History) { openKey = "history" },
                                            ),
                                        )
                                        "history", "autoplay", "appbeh", "tone", "output", "lyrics", "motion" -> {}
                                        "quality" -> {
                                            if (started) RowDivider()
                                            FoldRow(
                                                "Sound and quality",
                                                SfIcons.Waveform,
                                                listOf(
                                                    Fold("Audio quality", SfIcons.Wifi) { openKey = "quality" },
                                                    Fold("Tone", SfIcons.Waveform) { openKey = "tone" },
                                                    Fold("Output", SfIcons.Speaker) { openKey = "output" },
                                                ),
                                            )
                                        }
                                        "between" -> {
                                            if (started) RowDivider()
                                            FoldRow(
                                                "Playback",
                                                SfIcons.Forward,
                                                listOf(
                                                    Fold("Between songs", SfIcons.PlayCircle) { openKey = "between" },
                                                    Fold("Autoplay and suggestions", SfIcons.Sparkles) { openKey = "autoplay" },
                                                    Fold("App behavior", SfIcons.Phone) { openKey = "appbeh" },
                                                ),
                                            )
                                        }
                                        "look" -> {
                                            if (started) RowDivider()
                                            FoldRow(
                                                "Look and feel",
                                                SfIcons.Palette,
                                                listOf(
                                                    Fold("Appearance", SfIcons.Palette) { openKey = "look" },
                                                    Fold("Motion", SfIcons.Animation) { openKey = "motion" },
                                                ),
                                            )
                                        }
                                        "player" -> {
                                            if (started) RowDivider()
                                            FoldRow(
                                                "Player and Lyrics",
                                                SfIcons.Forward,
                                                listOf(
                                                    Fold("Player", SfIcons.Album) { openKey = "player" },
                                                    Fold("Lyrics", SfIcons.Lyrics) { openKey = "lyrics" },
                                                ),
                                            )
                                        }
                                        else -> {
                                            if (started) RowDivider()
                                            val c = SETTINGS_CATEGORIES.first { it.key == key }
                                            NavRow(c.title, { openKey = c.key }, icon = c.icon)
                                        }
                                        }
                                        started = true
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

/** One page a fold row opens, with the icon its sub-row wears. */
internal class Fold(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val open: () -> Unit)

/** A row that unfolds its pages below it in the card, the chevron turning as it opens. */
@Composable
private fun FoldRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<Fold>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "foldChevron")
    SettingRow(
        title,
        icon = icon,
        onClick = { open = !open },
        trailing = {
            Icon(
                SfIcons.ChevronDown,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.2.dp).graphicsLayer { rotationZ = chevron },
            )
        },
    )
    AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
            items.forEach { SubRow(it.title, it.icon, it.open) }
        }
    }
}

/** A folded sub-setting under its row: its own icon, smaller and highlighted. */
@Composable
private fun SubRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 69.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 15.dp),
        )
    }
}

/**
 * A destructive clear row: red, and a confirmation popup before it runs,
 * with what goes and what stays.
 */
@Composable
private fun ClearDangerRow(
    title: String,
    summary: String,
    confirmLabel: String,
    whatGoes: String,
    onClear: () -> Unit,
) {
    var ask by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        title,
        summary = summary,
        icon = SfIcons.Trash,
        titleColor = MaterialTheme.colorScheme.error,
        onClick = { ask = true },
    )
    if (ask) {
        FloatingDialog(
            onDismissRequest = { ask = false },
            title = { Text(title + "?") },
            text = { Text(whatGoes) },
            confirmButton = { SheetButton(confirmLabel, tone = SheetTone.Danger, onClick = { ask = false; onClear() }) },
            dismissButton = { SheetButton("Cancel", onClick = { ask = false }, closes = true) },
        )
    }
}

/**
 * Puts every setting on its category page, in the order [SETTINGS_CATEGORIES]
 * lists them; one it doesn't name goes under "More" on its old section's page.
 */
private fun place(sections: List<Section>): List<Placed> {
    val byTitle = sections.flatMap { s -> s.entries.map { it to s.title } }.associateBy { it.first.title }
    val used = mutableSetOf<String>()
    val out = mutableListOf<Placed>()
    // Every named setting first, so a page's "More" only gets what no page claims.
    SETTINGS_CATEGORIES.forEach { c ->
        c.groups.forEach { (group, titles) ->
            titles.forEach { t -> byTitle[t]?.let { (e, _) -> out += Placed(e, c, group); used += t } }
        }
    }
    SETTINGS_CATEGORIES.forEach { c ->
        byTitle.values.filter { (e, section) -> e.title !in used && SECTION_HOME[section] == c.key }
            .forEach { (e, _) -> out += Placed(e, c, "More"); used += e.title }
    }
    // Anything left (a section with no page) still shows, on About.
    val about = SETTINGS_CATEGORIES.last()
    byTitle.values.filter { (e, _) -> e.title !in used }.forEach { (e, _) -> out += Placed(e, about, "More") }
    return out
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun settingsSections(
    onOpenEqualizer: () -> Unit,
    onOpenReplay: () -> Unit,
    onOpenWrapped: () -> Unit,
    onSignIn: () -> Unit,
    onOpenRemix: () -> Unit,
    /** Opens another settings page: the sources pages live behind Lyrics and Player. */
    onOpenPage: (String) -> Unit,
): List<Section> {
    val signedIn by AccountStore.signedIn.collectAsState()
    val account by AccountStore.account.collectAsState()
    val accounts by AccountStore.accounts.collectAsState()
    var sponsorBlockDialog by remember { mutableStateOf(false) }
    var recommenderDialog by remember { mutableStateOf(false) }
    var historyTargetDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    val hidden by LibraryStore.hidden.collectAsState()
    val lyricsSettings by AppSettings.lyrics.collectAsState()
    val coverSettings by AppSettings.covers.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pb by AppSettings.playback.collectAsState()
    val sound by AppSettings.sound.collectAsState()
    val ui by AppSettings.ui.collectAsState()
    val lib by AppSettings.library.collectAsState()
    val metered = remember { AppSettings.onMeteredNetwork() }
    var qualityDialog by remember { mutableStateOf<Boolean?>(null) } // true = Wi-Fi, false = mobile
    var folderDialog by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { AudioCache.usedBytes(context) } }

    qualityDialog?.let { wifi ->
        ChoiceSheet(
            title = if (wifi) "Quality on Wi-Fi" else "Quality on mobile data",
            options = AudioQuality.entries,
            selected = if (wifi) pb.wifiQuality else pb.mobileQuality,
            label = { "${it.label} · ${it.summary}" },
            onSelect = { q -> AppSettings.updatePlayback { if (wifi) it.copy(wifiQuality = q) else it.copy(mobileQuality = q) } },
            onDismiss = { qualityDialog = null },
        )
    }
    if (sponsorBlockDialog) SponsorBlockDialog(onDismiss = { sponsorBlockDialog = false })

    if (hiddenDialog) HiddenDialog(onDismiss = { hiddenDialog = false })
    if (historyTargetDialog) {
        ChoiceSheet(
            title = "Sync listening history",
            options = com.grayguava.legato.settings.ListeningHistoryTarget.entries,
            selected = pb.listeningHistoryTarget,
            label = { "${it.label} · ${it.summary}" },
            onSelect = { target -> AppSettings.updatePlayback { it.copy(listeningHistoryTarget = target) } },
            onDismiss = { historyTargetDialog = false },
        )
    }
    if (recommenderDialog) {
        ChoiceSheet(
            title = "Recommendations",
            options = Recommender.entries,
            selected = pb.recommender,
            label = { "${it.label} · ${it.summary}" },
            onSelect = { r ->
                com.grayguava.legato.library.MusicRepository.refreshHomeRecommendations()
                com.grayguava.legato.ui.ScreenCache.invalidate("home:${r.name}")
                AppSettings.updatePlayback { it.copy(recommender = r) }
            },
            onDismiss = { recommenderDialog = false },
        )
    }
    if (confirmSignOut) {
        FloatingDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your likes and playlists on this device stay. YouTube Music stops seeing what you play here.") },
            confirmButton = {
                SheetButton("Sign out", tone = SheetTone.Danger, onClick = {
                    confirmSignOut = false
                    AccountStore.signOut()
                    // Every cookie goes, so a sign-in has to be done again.
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                })
            },
            dismissButton = { SheetButton("Cancel", onClick = { confirmSignOut = false }, closes = true) },
        )
    }
    if (folderDialog) {
        var folders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
        LaunchedEffect(Unit) { folders = runCatching { LocalMusic.folders(context) }.getOrDefault(emptyList()) }
        ChoiceSheet(
            title = "Local music folder",
            options = listOf<String?>(null) + folders.map { it.first },
            selected = lib.localFolder,
            label = { f ->
                f?.let { name ->
                    val n = folders.firstOrNull { it.first == name }?.second ?: 0
                    "$name · $n ${if (n == 1) "song" else "songs"}"
                } ?: "All audio folders · Every song on the phone"
            },
            onSelect = { f -> AppSettings.updateLibrary { it.copy(localFolder = f) } },
            onDismiss = { folderDialog = false },
        )
    }
    message?.let { m ->
        FloatingDialog(onDismissRequest = { message = null }, confirmButton = { SheetButton("OK", onClick = { message = null }, closes = true) }, text = { Text(m) })
    }

    return listOf(
        Section(
            "Account",
            listOfNotNull(
                Entry("YouTube Music account", "sign in login google profile switch add") {
                    if (signedIn) {
                        Column {
                            accounts.forEachIndexed { i, acc ->
                                if (i > 0) RowDivider()
                                SettingRow(
                                    acc.name.ifBlank { "Signed in" },
                                    summary = acc.email.ifBlank { "YouTube Music" },
                                    onClick = if (acc.active) null else ({ AccountStore.switchTo(acc.id) }),
                                    leading = { Artwork(acc.thumbnailUrl, Modifier.size(40.dp), CircleShape) },
                                    trailing = if (acc.active) ({ Icon(SfIcons.Check, "Active account", tint = MaterialTheme.colorScheme.primary) }) else null,
                                )
                            }
                        }
                    } else {
                        NavRow("Sign in to YouTube Music", onSignIn, summary = "Your playlists, liked songs and recommendations", icon = SfIcons.Account)
                    }
                },
                if (signedIn) {
                    Entry("Add another account", "add switch google account multiple") {
                        NavRow("Add another account", onSignIn, summary = "Sign in to a second YouTube Music account and switch between them", icon = SfIcons.PersonAdd)
                    }
                } else null,
                if (signedIn) {
                    Entry("Sign out", "logout account") {
                        SettingRow(if (accounts.size > 1) "Sign out of this account" else "Sign out", icon = Icons.AutoMirrored.Rounded.Logout, onClick = { confirmSignOut = true })
                    }
                } else null,
            ),
        ),
        Section(
            "Audio quality",
            listOfNotNull(
                // The two look alike; the one for the network in use now says so.
                Entry("On Wi-Fi", "stream quality bitrate") {
                    NavRow("On Wi-Fi", { qualityDialog = true }, summary = if (!metered) "In use now" else null, icon = SfIcons.Wifi, value = pb.wifiQuality.label)
                },
                Entry("On mobile data", "stream quality bitrate cellular") {
                    NavRow("On mobile data", { qualityDialog = false }, summary = if (metered) "In use now" else null, icon = SfIcons.Cellular, value = pb.mobileQuality.label)
                },
                Entry("Output precision", "16-bit 32-bit float sample rate") {
                    SettingRow("Output precision", summary = outputSummary(context, pb.floatOutput), icon = SfIcons.Waveform, beta = true, below = {
                        PillSegmented(listOf(false, true), pb.floatOutput, { if (it) "32-bit float" else "16-bit PCM" }, { v -> AppSettings.updatePlayback { it.copy(floatOutput = v) } })
                        Text(
                            "Float keeps hi-res files in 32-bit all the way to the output, effects included. 16-bit is dithered once, after the effects. Takes effect after a restart.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    })
                },
                Entry("Bit-perfect USB output", "dac exclusive mode usb audio player pro hiby passthrough hi-fi audiophile mixer bypass sample rate switching") {
                    val usb by BitPerfectUsb.status.collectAsState()
                    ToggleRow(
                        "Exclusive USB output",
                        pb.bitPerfectUsb,
                        { v -> AppSettings.updatePlayback { it.copy(bitPerfectUsb = v) } },
                        summary = if (!BitPerfectUsb.supported) "Needs Android 14 or newer"
                        else when (val s = usb) {
                            is BitPerfectUsb.Status.Active -> "Exclusive on ${s.device}: ${s.format.ifEmpty { "${s.bits}-bit" }} at ${s.sampleRate / 1000.0} kHz, bit for bit"
                            is BitPerfectUsb.Status.NoMatch -> "On. The DAC lists no bit-perfect mode for ${s.sampleRate / 1000.0} kHz, so this song goes through Android's mixer"
                            BitPerfectUsb.Status.NoDac -> "On. Plug in a USB DAC and the music moves to it"
                            else -> "The DAC plays only Legato, switched to each song's own sample rate and bit depth: no mixing, resampling or effects. Volume is applied by the app; at full volume the signal is bit-exact"
                        },
                        icon = SfIcons.Usb,
                        enabled = BitPerfectUsb.supported,
                        beta = true,
                    )
                },
                Entry("System audio effects", "dolby atmos soundalive equalizer device effects") {
                    ToggleRow("System audio effects", pb.systemEffects, { v -> AppSettings.updatePlayback { it.copy(systemEffects = v) } }, summary = "Let the phone's own effects (Dolby, SoundAlive, system equalizer) process playback", icon = SfIcons.Surround)
                },
                Entry("Background playback", "battery optimization doze killed stops samsung") {
                    val ignoring = remember { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }
                    NavRow(
                        "Background playback",
                        { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                        summary = if (ignoring) "Unrestricted: music keeps playing with the screen off" else "Restricted: if playback stops in the background, set Legato to Unrestricted",
                        icon = SfIcons.Battery,
                    )
                },
                Entry("Prefer USB DAC", "external dac usb audio") {
                    ToggleRow("Prefer USB DAC", pb.preferUsbDac, { v -> AppSettings.updatePlayback { it.copy(preferUsbDac = v) } }, summary = "Send audio to a USB DAC whenever one is plugged in", icon = SfIcons.Usb, beta = true)
                },
            ),
        ),
        Section(
            "Playback",
            listOfNotNull(
                Entry("Loudness normalization", "volume level") {
                    ToggleRow("Loudness normalization", pb.loudnessNormalization, { v -> AppSettings.updatePlayback { it.copy(loudnessNormalization = v) } }, summary = "Uses YouTube's loudness measurement to set one steady volume per song", icon = Icons.AutoMirrored.Rounded.VolumeUp)
                },
                if (pb.loudnessNormalization) Entry("Volume level", "loud louder quiet normal volume level loudness target boost") {
                    SettingRow("Volume level", summary = pb.volumeLevel.summary, icon = Icons.AutoMirrored.Rounded.VolumeUp, below = {
                        PillSegmented(VolumeLevel.entries, pb.volumeLevel, { it.label }, { v -> AppSettings.updatePlayback { it.copy(volumeLevel = v) } })
                    })
                } else null,
                if (pb.loudnessNormalization) Entry("Normalize on the phone speaker", "loudness volume speaker louder quiet normalization level") {
                    ToggleRow(
                        "Normalize on the phone speaker",
                        pb.normalizeOnSpeaker,
                        { v -> AppSettings.updatePlayback { it.copy(normalizeOnSpeaker = v) } },
                        summary = if (pb.normalizeOnSpeaker) "Songs are levelled on the speaker too, which turns loud ones down"
                        else "Off: the speaker never turns a song down, only lifts quiet ones; headphones and Bluetooth are fully levelled",
                        icon = SfIcons.Speaker,
                    )
                } else null,
                Entry("Preload upcoming songs", "latency fast skip instant buffer") {
                    ToggleRow("Preload upcoming songs", pb.preloadUpcoming, { v -> AppSettings.updatePlayback { it.copy(preloadUpcoming = v) } }, summary = "Next and previous songs start almost instantly. Uses a little extra data.", icon = SfIcons.Gauge)
                },
                Entry("Upgrade quality while playing", "better stream premium 256 opus swap") {
                    ToggleRow("Upgrade quality while playing", pb.qualityUpgrade, { v -> AppSettings.updatePlayback { it.copy(qualityUpgrade = v) } }, summary = "If a clearly better stream turns up a few seconds in, switch to it without stopping", icon = SfIcons.Quality, beta = true)
                },
                Entry("Crossfade", "fade blend transition overlap") {
                    SettingRow(
                        "Crossfade",
                        summary = if (pb.crossfadeSeconds == 0) "Off: songs play back to back" else "Blend each song into the next over ${pb.crossfadeSeconds} s",
                        icon = SfIcons.Animation,
                        below = {
                            LiquidSlider(
                                value = pb.crossfadeSeconds.toFloat(),
                                onValueChange = { v -> AppSettings.updatePlayback { it.copy(crossfadeSeconds = v.toInt()) } },
                                valueRange = 0f..12f,
                                snap = 1f,
                            )
                        },
                    )
                },
                Entry("Skip silence", "gaps") {
                    ToggleRow("Skip silence", pb.skipSilence, { v -> AppSettings.updatePlayback { it.copy(skipSilence = v) } }, summary = "Trim gaps longer than a second", icon = SfIcons.Keyboard)
                },
                Entry("Clarity", "studio master clarity presence air bass clear enhance") {
                    ToggleRow("Clarity", pb.clarity, { v -> AppSettings.updatePlayback { it.copy(clarity = v) } }, summary = "Firmer bass, less mud, more presence and air, at the same loudness. Off plays the stream untouched.", icon = SfIcons.Sparkles, beta = true)
                },
                Entry("Spatial audio", "stereo widen immersive") {
                    ToggleRow("Spatial audio", pb.spatialAudio, { v -> AppSettings.updatePlayback { it.copy(spatialAudio = v) } }, summary = "Widens stereo tracks for a more open sound", icon = SfIcons.Surround, beta = true)
                },
                Entry("Equalizer", "eq bands tone balance bass treble") {
                    NavRow("Equalizer", onOpenEqualizer, summary = "Fifteen bands, tone and balance", icon = SfIcons.Equalizer)
                },
                Entry("Remix", "speed pitch reverb bass 8d 3d nightcore slowed vaporwave remix") {
                    NavRow(
                        "Remix",
                        onOpenRemix,
                        summary = "Speed, pitch, reverb and space for every song",
                        icon = SfIcons.Waveform,
                        value = if (sound.enabled) RemixPreset.matching(sound)?.label ?: "Custom" else "Off",
                    )
                },
                Entry("Autoplay", "radio continue") {
                    ToggleRow("Autoplay", pb.autoplay, AppSettings::setAutoplay, summary = "Keep playing similar songs when the queue ends", icon = Icons.AutoMirrored.Rounded.PlaylistPlay)
                },
                Entry("Recommendations", "recommendation engine jiosaavn saavn youtube music radio suggestions similar songs") {
                    NavRow(
                        "Recommendations",
                        { recommenderDialog = true },
                        summary = "Whose picks autoplay adds and Home's \"Because you played\" shows. Songs still play from YouTube Music.",
                        icon = SfIcons.Sparkles,
                        value = pb.recommender.label,
                    )
                },
                Entry("Sync listening history", "youtube account privacy sync history") {
                    NavRow("Sync listening history", { historyTargetDialog = true },
                        summary = "YouTube Music needs a signed-in account.",
                        icon = SfIcons.History, value = pb.listeningHistoryTarget.label)
                },
                Entry("Pause account sync", "private pause youtube history recommendations") {
                    ToggleRow("Pause account sync", pb.pauseListeningHistory,
                        { v -> AppSettings.updatePlayback { it.copy(pauseListeningHistory = v) } },
                        summary = "Pause sending new listens to YouTube Music. Your local history, listening stats and personal recommendations keep updating. Paused listens are never sent later; likes and connected scrobblers keep their own settings.",
                        icon = SfIcons.History)
                },
                Entry("Don't repeat songs in current session", "autoplay duplicates") {
                    ToggleRow("Don't repeat songs in current session", pb.noRepeatInSession, { v -> AppSettings.updatePlayback { it.copy(noRepeatInSession = v) } }, summary = "Autoplay won't add a song already played or queued this session", icon = SfIcons.History)
                },
                Entry("Smart shuffle", "shuffle similar songs mix recommendations playlist album sparkle") {
                    ToggleRow("Smart shuffle", pb.smartShuffle, { v -> AppSettings.updatePlayback { it.copy(smartShuffle = v) } }, summary = "Mixes a similar song in after every four. Tap the player's shuffle button twice, or long-press Shuffle on a playlist or album", icon = SfIcons.Sparkles)
                },
                Entry("Hidden songs and artists", "not interested don't play hide block artist unhide suggestions") {
                    val count = hidden.songs.size + hidden.artists.size
                    NavRow(
                        "Hidden songs and artists",
                        { hiddenDialog = true },
                        summary = if (count == 0) "Kept out of autoplay, radio and Home. Nothing hidden yet"
                        else listOfNotNull(
                            hidden.songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
                            hidden.artists.size.takeIf { it > 0 }?.let { if (it == 1) "1 artist" else "$it artists" },
                        ).joinToString(" and ") + " kept out of suggestions",
                        icon = SfIcons.Hidden,
                    )
                },
                Entry("Pause at zero volume", "mute volume down silent") {
                    ToggleRow("Pause at zero volume", pb.pauseAtZeroVolume, { v -> AppSettings.updatePlayback { it.copy(pauseAtZeroVolume = v) } }, summary = "Pauses when the volume is turned all the way down and plays again when it comes back up", icon = Icons.AutoMirrored.Rounded.VolumeOff)
                },
                Entry("SponsorBlock", "sponsorblock skip sponsor intro outro non-music talking music video segments") {
                    ToggleRow("Skip non-music parts", pb.sponsorBlock, { v -> AppSettings.updatePlayback { it.copy(sponsorBlock = v) } }, summary = "Jumps over talking, skits and sponsor reads in music videos, from SponsorBlock", icon = SfIcons.Forward)
                },
                if (pb.sponsorBlock) Entry("SponsorBlock categories", "sponsorblock skip categories") {
                    NavRow("What to skip", { sponsorBlockDialog = true }, summary = pb.sponsorBlockCategories.mapNotNull { SponsorBlock.CATEGORIES[it]?.label }.joinToString().ifEmpty { "Nothing" }, icon = SfIcons.Forward)
                } else null,
                Entry("Stop music on close from recents", "swipe away") {
                    ToggleRow("Stop music on close from recents", pb.stopOnTaskRemoved, { v -> AppSettings.updatePlayback { it.copy(stopOnTaskRemoved = v) } }, summary = "Stops playback when swiped away from recent apps", icon = SfIcons.MusicOff)
                },
            ),
        ),
        Section(
            "Appearance",
            listOf(
                Entry("Player beside the page", "tablet foldable large screen side pane docked player split landscape") {
                    ToggleRow(
                        "Player beside the page",
                        ui.sidePlayer,
                        { v -> AppSettings.updateUi { it.copy(sidePlayer = v) } },
                        summary = "On large screens, the player stays open on the right while music plays",
                        icon = SfIcons.Split,
                        enabled = ui.wideLayout,
                    )
                },
                Entry("Lyric preview while seeking", "scrub seek bar lyric line preview bubble drag") {
                    ToggleRow("Lyric preview while seeking", ui.lyricScrubPreview, { v -> AppSettings.updateUi { it.copy(lyricScrubPreview = v) } }, summary = "Dragging the seek bar shows the line sung at that point above your finger", icon = SfIcons.Lyrics)
                },
                Entry("Cover sources", "sharper album covers artwork cover musicbrainz cover art archive video thumbnail apple deezer youtube order priority") {
                    NavRow(
                        "Cover sources",
                        { onOpenPage("covers") },
                        summary = if (!ui.albumCovers) "Off" else coverSettings.ordered.filter { it.enabled }.joinToString(", then ") { it.source.label }.ifEmpty { "Off" },
                        icon = SfIcons.Album,
                    )
                },
                Entry("Reduce animation", "motion") {
                    ToggleRow("Reduce animation", ui.reduceAnimation, { v -> AppSettings.updateUi { it.copy(reduceAnimation = v) } }, summary = "Freezes decorative motion and simplifies player and lyric transitions", icon = SfIcons.Animation)
                },
                Entry("Liquid", "glass liquid refraction apple lens smoke frosted blur") {
                    ToggleRow(
                        "Liquid",
                        ui.liquidGlass && liquidGlassSupported,
                        { v -> AppSettings.updateUi { it.copy(liquidGlass = v) } },
                        summary = if (liquidGlassSupported) "Apple's Liquid Glass, everywhere: softly frosted, the edges bend and split the light, with a bright rim"
                        else "Needs Android 13 or newer; smoke glass is used instead",
                        icon = SfIcons.Sparkles,
                        enabled = liquidGlassSupported && !ui.reduceBlur,
                    )
                },
                Entry("Frosted top edge", "status bar blur fade top scroll glass") {
                    ToggleRow("Frosted top edge", ui.frostedTopEdge, { v -> AppSettings.updateUi { it.copy(frostedTopEdge = v) } }, summary = "Pages blur and fade as they scroll under the status bar", icon = SfIcons.Gradient)
                },
                Entry("High performance mode", "120hz 90hz smooth fps refresh rate frame rate scrolling performance battery unrestricted lag") {
                    HighPerformanceRows(ui)
                },
                Entry("Separate Explore tab", "explore tab dock search page navigation moods genres own") {
                    ToggleRow(
                        "Separate Explore tab",
                        ui.separateExplore,
                        { v -> AppSettings.updateUi { it.copy(separateExplore = v) } },
                        summary = if (ui.separateExplore) "Explore has a tab in the dock" else "Explore fills the Search page until you start a search",
                        icon = SfIcons.Compass,
                    )
                },
                Entry("Wide-screen layout", "tablet foldable large screen landscape rail side navigation dock columns") {
                    ToggleRow(
                        "Wide-screen layout",
                        ui.wideLayout,
                        { v -> AppSettings.updateUi { it.copy(wideLayout = v) } },
                        summary = "On tablets, unfolded phones and in landscape, the tabs move to a rail on the side and pages keep a readable width",
                        icon = SfIcons.Tablet,
                    )
                },
                Entry("Reduce dynamic blur", "glass frosted performance") {
                    ToggleRow("Reduce dynamic blur", ui.reduceBlur, { v -> AppSettings.updateUi { it.copy(reduceBlur = v) } }, summary = "Swaps frosted glass for solid fills across the app", icon = SfIcons.Drop)
                },
            ),
        ),
        Section(
            "Lyrics",
            listOfNotNull(
                Entry("Synced lyrics", "karaoke words") {
                    ToggleRow("Synced lyrics", ui.syncedLyrics, { v -> AppSettings.updateUi { it.copy(syncedLyrics = v) } }, summary = "Lights up the words as they're sung", icon = SfIcons.Lyrics)
                },
                Entry("Blur unfocused lyrics", "spotlight") {
                    ToggleRow("Blur unfocused lyrics", ui.blurLyrics, { v -> AppSettings.updateUi { it.copy(blurLyrics = v) } }, summary = "Keeps the spotlight on the current line", icon = SfIcons.DropHalf)
                },
                Entry("Lyrics take the screen", "hide controls idle focus fullscreen lyrics auto") {
                    ToggleRow("Lyrics take the screen", ui.lyricsFocus, { v -> AppSettings.updateUi { it.copy(lyricsFocus = v) } }, summary = "On the lyrics view, the controls slide away after a few seconds untouched. Touch or swipe to bring them back.", icon = SfIcons.Lyrics)
                },
                Entry("Prioritize word-by-word lyrics", "word synced karaoke exact search order") {
                    ToggleRow(
                        "Prioritize word-by-word lyrics",
                        lyricsSettings.preferWordSynced,
                        { v -> AppSettings.updateLyrics { it.copy(preferWordSynced = v) } },
                        summary = "Keeps searching past a line-synced match for word-by-word lyrics",
                        icon = SfIcons.Lyrics,
                    )
                },
                if (com.grayguava.legato.lyrics.Romanizer.available) Entry("Romanized lyrics", "latin letters transliteration hindi korean japanese script") {
                    ToggleRow(
                        "Romanized lyrics",
                        lyricsSettings.romanize,
                        { v -> AppSettings.updateLyrics { it.copy(romanize = v) } },
                        summary = "A line in Latin letters under lyrics in Hindi, Korean, Japanese and other scripts",
                        icon = SfIcons.TextFormat,
                    )
                } else null,
                Entry("Lyrics sources", "lrclib youtube order provider") {
                    NavRow(
                        "Lyrics sources",
                        { onOpenPage("sources") },
                        summary = lyricsSettings.ordered.filter { it.enabled }.joinToString(", then ") { it.source.label }.ifEmpty { "None: lyrics are off" },
                        icon = SfIcons.TextFormat,
                    )
                },
            ),
        ),
        Section(
            "Content",
            listOf(
                Entry("Hide explicit content", "explicit clean kids family parental filter") {
                    ToggleRow(
                        "Hide explicit content",
                        lib.hideExplicit,
                        { v -> AppSettings.updateLibrary { it.copy(hideExplicit = v) } },
                        summary = "Leaves out songs and albums marked explicit on Home, in search, on album and artist pages and in autoplay",
                        icon = SfIcons.Filter,
                    )
                },
            ),
        ),
        Section(
            "Local music",
            listOf(
                Entry("Local music folder", "device files") {
                    NavRow("Local music folder", { folderDialog = true }, summary = lib.localFolder ?: "All audio folders", icon = SfIcons.Folder)
                },
            ),
        ),
        Section(
            "Storage",
            listOf(
                Entry("Song cache limit", "disk offline seeking") {
                    var draft by remember(lib.songCacheMb) { mutableFloatStateOf(lib.songCacheMb.toFloat()) }
                    SettingRow(
                        "Song cache limit",
                        summary = "Keeps played audio on disk for instant seeking and replays. Takes effect after a restart.",
                        icon = SfIcons.Storage,
                        trailing = { Text(formatMb(draft.toInt()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal) },
                        below = {
                            LiquidSlider(
                                value = draft,
                                onValueChange = { draft = it },
                                valueRange = 128f..5120f,
                                snap = 128f,
                                onValueChangeFinished = { AppSettings.updateLibrary { it.copy(songCacheMb = draft.toInt()) } },
                            )
                        },
                    )
                },
                Entry("Show cached songs folder", "cache offline offline listening kept songs playlist folder library") {
                    ToggleRow(
                        "Show cached songs folder",
                        lib.showCachedSongs,
                        { v -> AppSettings.updateLibrary { it.copy(showCachedSongs = v) } },
                        summary = "Adds a Cached songs folder to Library: the YouTube Music tracks kept on this phone, playable without a connection",
                        icon = SfIcons.Storage,
                    )
                },
                Entry("Clear song cache", "free space") {
                    ClearDangerRow(
                        title = "Clear song cache",
                        summary = "Using ${Formatter.formatShortFileSize(context, cacheBytes)}",
                        confirmLabel = "Clear cache",
                        whatGoes = "Cached songs go back to free space. Your likes and playlists stay.",
                        onClear = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AudioCache.clear(context) }
                                cacheBytes = withContext(Dispatchers.IO) { AudioCache.usedBytes(context) }
                            }
                            message = "Song cache cleared."
                        },
                    )
                },
                Entry("Clear image cache", "artwork") {
                    ClearDangerRow(
                        title = "Clear image cache",
                        summary = "Frees space used by album artwork",
                        confirmLabel = "Clear image cache",
                        whatGoes = "Cached album artwork goes back to free space. Your likes and playlists stay.",
                        onClear = {
                            val loader = SingletonImageLoader.get(context)
                            loader.memoryCache?.clear()
                            scope.launch { withContext(Dispatchers.IO) { loader.diskCache?.clear() } }
                            message = "Image cache cleared."
                        },
                    )
                },
            ),
        ),
        Section(
            "Your data",
            listOf(
                Entry("Replay", "stats top songs artists") {
                    NavRow("Replay", onOpenReplay, summary = "Your top songs, artists and albums", icon = SfIcons.Chart)
                },
                Entry("Wrapped", "wrapped story year review stats recap top artists") {
                    NavRow("Wrapped", onOpenWrapped, summary = "Your listening as an animated story", icon = SfIcons.Sparkles)
                },
                Entry("Clear listening history", "delete history") {
                    ClearDangerRow(
                        title = "Clear listening history",
                        summary = "Removes Recents and Replay data",
                        confirmLabel = "Clear history",
                        whatGoes = "Recents, Replay and Wrapped go back to empty. Your likes, playlists and downloads stay.",
                        onClear = {
                            History.clear()
                            message = "Listening history cleared."
                        },
                    )
                },
            ),
        ),
        Section(
            "Miscellaneous",
            listOf(
                Entry("Hide volume bar", "player slider") {
                    ToggleRow("Hide volume bar", ui.hideVolumeBar, { v -> AppSettings.updateUi { it.copy(hideVolumeBar = v) } }, summary = "Removes the volume slider from the player", icon = Icons.AutoMirrored.Rounded.VolumeOff)
                },
                Entry("Lyrics preview", "player lyrics line apple music classic theme") {
                    ToggleRow("Lyrics preview", ui.lyricsPreview, { v -> AppSettings.updateUi { it.copy(lyricsPreview = v) } },
                        summary = "Show the current lyric on the player in every theme", icon = SfIcons.Lyrics)
                },
                Entry("Hide song status", "playing from") {
                    ToggleRow("Hide song status", ui.hideSongStatus, { v -> AppSettings.updateUi { it.copy(hideSongStatus = v) } }, summary = "Hides the \"Playing from\" line in the player", icon = SfIcons.Hidden)
                },
            ),
        ),
        Section(
            "Advanced",
            listOf(
                Entry("Star on GitHub", "star github rate like") {
                    NavRow("Star on GitHub", {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, com.grayguava.legato.ui.components.Support.REPO.toUri())) }
                    }, summary = "Helps other people find Legato", icon = SfIcons.Star)
                },
                Entry("Source code", "github open source license gpl") {
                    NavRow("Source code", {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/grayguava/legato".toUri())) }
                    }, summary = "github.com/grayguava/legato · GPL v3", icon = SfIcons.Code)
                },
                Entry("Export diagnostics", "log share troubleshooting bug report") {
                    NavRow("Export diagnostics", {
                        scope.launch {
                            val log = LogExport.recent()
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Legato ${BuildConfig.VERSION_NAME} log")
                                .putExtra(Intent.EXTRA_TEXT, log)
                            context.startActivity(Intent.createChooser(send, "Share log"))
                        }
                    }, summary = "Share this app's recent log for troubleshooting", icon = SfIcons.Bug)
                },
                Entry("Show stats for nerds", "codec bitrate sample rate debug") {
                    ToggleRow("Show stats for nerds", ui.statsForNerds, { v -> AppSettings.updateUi { it.copy(statsForNerds = v) } }, summary = "Codec, bitrate, sample rate and channels under the cover art", icon = SfIcons.Info)
                },
            ),
        ),
    )
}

private fun outputSummary(context: Context, float: Boolean): String {
    val am = context.getSystemService(AudioManager::class.java)
    val rate = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    val khz = rate?.let { "%.1f kHz".format(it / 1000f) } ?: "unknown rate"
    return "AudioTrack · ${Build.MODEL} · $khz · ${if (float) "32-bit float" else "16-bit PCM"}"
}

private fun formatMb(mb: Int): String = if (mb >= 1024) "%.1f GB".format(mb / 1024f) else "$mb MB"
