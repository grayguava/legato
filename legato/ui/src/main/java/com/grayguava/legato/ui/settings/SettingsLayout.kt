package com.grayguava.legato.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.grayguava.legato.ui.icons.SfIcons

/** A page of settings, reached from the list on the main Settings page. */
internal class SettingsCategory(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    /** Subsections, each a heading and the titles of the settings under it, in order. */
    val groups: List<Pair<String, List<String>>>,
)

/**
 * Where every setting lives. Each page is named for what someone opening it is looking for,
 * and each setting is on the page they'd try first: everything about a service with that
 * service, every kind of quality with sound, everything about listening history together,
 * Home and Library's own options with Home and Library. A setting not named here lands under
 * "More" on its section's page, so a new one is never lost, but every setting today has its place.
 */
internal val SETTINGS_CATEGORIES = listOf(
    SettingsCategory(
        "account", "Accounts and services", "YouTube Music account", SfIcons.Person,
        listOf(
            "YouTube Music" to listOf("YouTube Music account", "Add another account", "Sign out"),
        ),
    ),
    SettingsCategory(
        "quality", "Audio quality", "How much data streaming spends, on which network", SfIcons.Wifi,
        listOf(
            "Streaming" to listOf("On Wi-Fi", "On mobile data", "Upgrade quality while playing"),
        ),
    ),
    SettingsCategory(
        "tone", "Tone", "Loudness and the sound itself, on any device", SfIcons.Waveform,
        listOf(
            "Loudness" to listOf("Loudness normalization", "Volume level", "Normalize on the phone speaker"),
            "Enhancements" to listOf("Equalizer", "Remix", "Clarity", "Spatial audio", "System audio effects"),
        ),
    ),
    SettingsCategory(
        "output", "Output", "Where the audio goes and how it arrives", SfIcons.Speaker,
        listOf(
            "Output" to listOf("Output precision", "Prefer USB DAC", "Bit-perfect USB output"),
        ),
    ),
    SettingsCategory(
        "between", "Between songs", "Crossfade, silence, preloading and music videos", SfIcons.PlayCircle,
        listOf(
            "Songs" to listOf("Crossfade", "Skip silence", "Preload upcoming songs"),
            "Music videos" to listOf("SponsorBlock", "SponsorBlock categories"),
        ),
    ),
    SettingsCategory(
        "autoplay", "Autoplay and suggestions", "What plays when you're not choosing", SfIcons.Sparkles,
        listOf(
            "Autoplay" to listOf("Autoplay", "Recommendations", "Don't repeat songs in current session"),
            "Suggestions" to listOf("Smart shuffle", "Hidden songs and artists"),
        ),
    ),
    SettingsCategory(
        "appbeh", "App behavior", "What the app does while music plays", SfIcons.Phone,
        listOf(
            "While playing" to listOf("Pause at zero volume"),
            "In the background" to listOf("Background playback", "Stop music on close from recents"),
        ),
    ),
    SettingsCategory(
        "lyrics", "Lyrics", "Sync, words, sources and effects", SfIcons.Lyrics,
        listOf(
            "Display" to listOf("Synced lyrics", "Lyrics take the screen", "Blur unfocused lyrics"),
            "On the player" to listOf("Lyrics preview", "Lyric preview while seeking"),
            "Words" to listOf("Prioritize word-by-word lyrics", "Romanized lyrics"),
            "Sources" to listOf("Lyrics sources"),
        ),
    ),
    // Reached from the Lyrics page rather than the main card: its rows are the
    // sources themselves, each with a switch and a handle, not named settings.
    SettingsCategory(
        "sources", "Lyrics sources", "Where lyrics come from, in the order they're tried", SfIcons.TextFormat,
        emptyList(),
    ),
    // Reached from the Player page, the same way: rows are the sources, each
    // with a switch and a handle.
    SettingsCategory(
        "covers", "Cover sources", "Where album covers come from, in the order they're tried", SfIcons.Album,
        emptyList(),
    ),
    SettingsCategory(
        "look", "Appearance", "Glass and how the app looks", SfIcons.Palette,
        listOf(
            "Glass" to listOf("Liquid", "Frosted top edge", "Reduce dynamic blur"),
        ),
    ),
    SettingsCategory(
        "player", "Player", "Layout, cover art and what the player shows", SfIcons.Album,
        listOf(
            "Layout" to listOf("Player beside the page"),
            "Cover art" to listOf("Cover sources"),
            "On the player" to listOf("Hide volume bar", "Hide song status", "Show stats for nerds"),
        ),
    ),
    SettingsCategory(
        "motion", "Motion", "Animations and smoothness", SfIcons.Animation,
        listOf(
            "Dock" to listOf("Dock animation"),
            "Smoothness" to listOf("High performance mode", "Reduce animation"),
        ),
    ),
    SettingsCategory(
        "library", "Home and Library", "Tabs, artist pages and what's hidden", SfIcons.Library,
        listOf(
            "Home and tabs" to listOf("Separate Explore tab", "Wide-screen layout"),
            "What's hidden" to listOf("Hide explicit content", "Hidden songs and artists"),
        ),
    ),
    SettingsCategory(
        "data", "Music and storage", "Music on this phone and the space it uses", SfIcons.Storage,
        listOf(
            "Music on this phone" to listOf("Local music folder"),
            "Space used" to listOf("Song cache limit", "Show cached songs folder", "Clear song cache", "Clear image cache"),
        ),
    ),
    SettingsCategory(
        "history", "Listening history", "Replay, Wrapped, syncing and clearing what you've played", SfIcons.History,
        listOf(
            "Your year" to listOf("Replay", "Wrapped"),
            "Syncing" to listOf("Sync listening history", "Pause account sync"),
            // No label over it: the card holds one red row on its own.
            "" to listOf("Clear listening history"),
        ),
    ),
    SettingsCategory(
        "about", "About", "Updates, source code and diagnostics", SfIcons.Info,
        listOf(
            "Community" to listOf("Star on GitHub", "Source code"),
            "Help" to listOf("Export diagnostics"),
        ),
    ),
)

/** How the main Settings page groups its pages: what's yours, how it sounds, how it looks, the app. */
internal val SETTINGS_HOME = listOf(
    "You" to listOf("account", "history"),
    "Listening" to listOf("quality", "tone", "output", "between", "autoplay", "appbeh", "player", "lyrics"),
    "Look and feel" to listOf("look", "motion"),
    "Your music" to listOf("library", "data"),
    "App" to listOf("about"),
)

/** For a setting no category names: the page its old section belongs on. */
internal val SECTION_HOME = mapOf(
    "Account" to "account",
    "Audio quality" to "quality",
    "Playback" to "between",
    "Appearance" to "look",
    "Lyrics" to "lyrics",
    "New releases" to "library",
    "Content" to "library",
    "Local music" to "data",
    "Storage" to "data",
    "Your data" to "data",
    "Import" to "library",
    "Miscellaneous" to "player",
    "Advanced" to "about",
)
