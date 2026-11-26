package com.grayguava.legato.settings

import kotlinx.serialization.Serializable

@Serializable
public enum class ListeningHistoryTarget(public val label: String, public val summary: String) {
    DEVICE_ONLY("This device", "Keep listening history in Legato"),
    YOUTUBE_MUSIC("YouTube Music", "Also send listened songs to your signed-in YouTube Music account"),
}
