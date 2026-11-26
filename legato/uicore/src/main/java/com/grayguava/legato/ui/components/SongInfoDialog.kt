package com.grayguava.legato.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.source.songinfo.SONG_INFO_ROLES
import com.grayguava.legato.source.songinfo.SongInfo
import com.grayguava.legato.source.songinfo.SongInfoRepository
import com.grayguava.legato.source.songinfo.SongInfoSource
import com.grayguava.legato.source.songinfo.compactCount
import com.grayguava.legato.ui.formatTime
import kotlinx.coroutines.CancellationException

/**
 * The song-info sheet: album, length, release date, plays and the credits
 * YouTube Music lists. Rows the sources don't have are left out entirely.
 */
@Composable
public fun SongInfoDialog(song: Song, durationMs: Long, onDismiss: () -> Unit) {
    var info by remember(song, durationMs) { mutableStateOf<SongInfo?>(null) }
    var loading by remember(song, durationMs) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(song, durationMs, retry) {
        loading = true
        info = null
        info = try {
            SongInfoRepository.load(song, durationMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SongInfo(failed = SongInfoSource.entries.toSet())
        }
        loading = false
    }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song info") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val data = info
                data?.album?.let { Detail("Album", it) }
                if (data != null && data.lengthMs > 0) Detail("Track length", formatTime(data.lengthMs))
                data?.releaseDate?.let { Detail("Release date", it) }
                data?.plays?.let { Detail("Plays/views", compactCount(it)) }
                data?.let { found ->
                    for (role in SONG_INFO_ROLES) found.credit(role)?.let { Detail(role, it.text) }
                }
                if (loading) Text("Loading song info…", style = MaterialTheme.typography.bodySmall)
                if (data?.failed?.isNotEmpty() == true) {
                    Text("Some details couldn't be loaded.", style = MaterialTheme.typography.bodySmall)
                    SheetButton("Retry", onClick = { retry++ })
                }
            }
        },
        confirmButton = { SheetButton("Close", onClick = onDismiss, closes = true) },
    )
}

@Composable private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
