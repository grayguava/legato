package com.grayguava.legato.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grayguava.legato.audio.playback.SoundEffects
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.EightD
import com.grayguava.legato.settings.RemixPreset
import com.grayguava.legato.settings.ReverbLevel
import com.grayguava.legato.settings.Space3DSpeed
import com.grayguava.legato.ui.components.GroupCard
import com.grayguava.legato.ui.components.GroupLabel
import com.grayguava.legato.ui.components.LiquidSwitch
import com.grayguava.legato.ui.components.PageHeader
import com.grayguava.legato.ui.components.ToggleRow
import com.grayguava.legato.ui.icons.SfIcons
import kotlin.math.roundToInt

/**
 * Settings → Tone → Remix: one switch that turns the remix on for every song,
 * then the presets and the controls it applies.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
public fun RemixScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val sound by AppSettings.sound.collectAsState()
    val reverbAvailable by SoundEffects.reverbAvailable.collectAsState()
    var linked by rememberSaveable { mutableStateOf(sound.speed == sound.pitch) }
    val preset = RemixPreset.matching(sound)
    val on = sound.enabled

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { PageHeader("Remix", onBack = onBack) }
        item {
            GroupCard {
                ToggleRow(
                    "Remix",
                    on,
                    { v -> AppSettings.updateSound { it.copy(enabled = v) } },
                    summary = "Speed, pitch, reverb and space for every song. Off plays the stream untouched.",
                    icon = SfIcons.Waveform,
                )
            }
        }
        if (!on) {
            item {
                Text(
                    "Turn on Remix to set speed, pitch, reverb and space.",
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        item { GroupLabel("Presets") }
        item {
            GroupCard {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemixPreset.entries.forEach { p ->
                        FilterChip(
                            selected = preset == p,
                            onClick = {
                                AppSettings.updateSound { p.sound.copy(enabled = true) }
                                linked = p.sound.speed == p.sound.pitch
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
            }
        }
        item { GroupLabel("Speed and pitch") }
        item {
            GroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    RemixSlider("Speed", sound.speed, 0.5f..2f, format = { "%.2f×".format(it) }) { v ->
                        AppSettings.updateSound { if (linked) it.copy(speed = v, pitch = v) else it.copy(speed = v) }
                    }
                    RemixSlider("Pitch", sound.pitch, 0.5f..2f, format = ::semitones) { v ->
                        AppSettings.updateSound { if (linked) it.copy(speed = v, pitch = v) else it.copy(pitch = v) }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Link pitch to speed", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Like a turntable: slower sounds deeper",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LiquidSwitch(
                            checked = linked,
                            onChange = { v ->
                                linked = v
                                if (v) AppSettings.updateSound { it.copy(pitch = it.speed) }
                            },
                        )
                    }
                }
            }
        }
        item { GroupLabel("Reverb") }
        item {
            GroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (!reverbAvailable) {
                        Text(
                            "This device doesn't offer a reverb effect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReverbLevel.entries.forEach { level ->
                            FilterChip(
                                selected = sound.reverb == level,
                                onClick = { AppSettings.updateSound { it.copy(reverb = level) } },
                                label = { Text(level.label) },
                            )
                        }
                    }
                }
            }
        }
        item { GroupLabel("Space and bass") }
        item {
            GroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    RemixSlider("Bass boost", sound.bassBoost / 1000f, 0f..1f, format = { "${(it * 100).roundToInt()}%" }) { v ->
                        AppSettings.updateSound { it.copy(bassBoost = (v * 1000).roundToInt()) }
                    }
                    Text("8D audio", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "The sound circles slowly around your head. Best on headphones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EightD.entries.forEach { e ->
                            FilterChip(
                                selected = sound.eightD == e,
                                onClick = {
                                    AppSettings.updateSound {
                                        it.copy(eightD = e, space = if (e != EightD.OFF) Space3DSpeed.OFF else it.space)
                                    }
                                },
                                label = { Text(e.label) },
                            )
                        }
                    }
                    Text("3D sound", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "The music goes all the way round you: in front, overhead, behind and below. Best on headphones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Space3DSpeed.entries.forEach { e ->
                            FilterChip(
                                selected = sound.space == e,
                                onClick = {
                                    AppSettings.updateSound {
                                        it.copy(space = e, eightD = if (e != Space3DSpeed.OFF) EightD.OFF else it.eightD)
                                    }
                                },
                                label = { Text(e.label) },
                            )
                        }
                    }
                    Text(
                        "Speed, pitch and bass work on every phone. Reverb uses Android's built-in effect, which some phones don't have.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemixSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LiquidSlider(value = value.coerceIn(range), onValueChange = { onChange(snap(it)) }, valueRange = range)
    }
}

/** Snap to two decimals, and onto 1.0 when close, so "normal" is easy to hit. */
private fun snap(v: Float): Float {
    val rounded = (v * 100).roundToInt() / 100f
    return if (kotlin.math.abs(rounded - 1f) < 0.03f) 1f else rounded
}

private fun semitones(pitch: Float): String {
    val st = 12 * kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)
    val r = (st * 10).roundToInt() / 10.0
    return when {
        r > 0 -> "+$r st"
        r < 0 -> "$r st"
        else -> "0 st"
    }
}
