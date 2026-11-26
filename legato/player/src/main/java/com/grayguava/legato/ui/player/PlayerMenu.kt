package com.grayguava.legato.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.ui.components.SongActionsMenu

/**
 * The player's ... menu: the iOS panel's rows, with the player's own extras
 * slotted in. Convert to video, download, cache, history and automix are left out.
 */
@Composable
public fun PlayerActionsMenu(
    song: Song,
    visible: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    onShare: () -> Unit,
    onUpgradeQuality: () -> Unit,
    onRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onSongInfo: () -> Unit,
    onSleepTimer: () -> Unit,
    onLyricsOffset: () -> Unit,
    onCopyLog: () -> Unit,
) {
    SongActionsMenu(
        song = song,
        visible = visible,
        anchor = anchor,
        onDismiss = onDismiss,
        onFinished = onFinished,
        onShare = onShare,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onUpgradeQuality = onUpgradeQuality,
        onStartRadio = onRadio,
        onSongInfo = onSongInfo,
        onSleepTimer = onSleepTimer,
        onLyricsOffset = onLyricsOffset,
        onCopyLog = onCopyLog,
    )
}
