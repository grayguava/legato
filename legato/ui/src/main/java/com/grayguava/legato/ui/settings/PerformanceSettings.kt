package com.grayguava.legato.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.InterfaceSettings
import com.grayguava.legato.ui.components.FloatingDialog
import com.grayguava.legato.ui.components.PillSegmented
import com.grayguava.legato.ui.components.RowDivider
import com.grayguava.legato.ui.components.SheetButton
import com.grayguava.legato.ui.components.SettingRow
import com.grayguava.legato.ui.components.ToggleRow
import kotlin.math.roundToInt
import com.grayguava.legato.ui.icons.SfIcons

/**
 * High performance mode and the refresh rate it asks for. Turning it on also
 * asks for unrestricted battery use: Android's battery saver throttles apps it
 * restricts, which shows up as dropped frames and a stutter on the first
 * scrolls after the app comes back.
 */
@Composable
internal fun HighPerformanceRows(ui: InterfaceSettings) {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }
    var askBattery by remember { mutableStateOf(false) }
    // Read again on return from the system's battery screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) unrestricted = isUnrestricted(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val rates = remember { supportedRates(context) }
    val chosen = ui.refreshRateHz.takeIf { it in rates } ?: rates.lastOrNull() ?: 60
    // Already on (from before, or turned on elsewhere) but still restricted: asked once a run,
    // here, rather than only at the moment the switch is flipped.
    LaunchedEffect(Unit) {
        if (ui.highRefreshRate && !unrestricted && !askedThisRun) {
            askedThisRun = true
            askBattery = true
        }
    }

    ToggleRow(
        "High performance mode",
        ui.highRefreshRate,
        { v ->
            AppSettings.updateUi { it.copy(highRefreshRate = v) }
            if (v && !unrestricted) { askedThisRun = true; askBattery = true }
        },
        summary = if (ui.highRefreshRate) "$chosen Hz requested. Android may lower it when needed." else "Runs the screen at a faster rate while Legato is open, for smoother scrolling",
        icon = SfIcons.Gauge,
    )
    if (ui.highRefreshRate && rates.size > 1) {
        RowDivider()
        SettingRow("Refresh rate", icon = SfIcons.Waveform, below = {
            PillSegmented(
                options = rates,
                selected = chosen,
                label = { "$it Hz" },
                onSelect = { hz -> AppSettings.updateUi { it.copy(refreshRateHz = hz) } },
            )
        })
    }
    if (ui.highRefreshRate && !unrestricted) {
        RowDivider()
        SettingRow(
            "Allow unrestricted battery use",
            summary = "Keeps Android from slowing Legato down to save power",
            icon = SfIcons.Battery,
            onClick = { requestUnrestricted(context) },
        )
    }

    if (askBattery) {
        FloatingDialog(
            onDismissRequest = { askBattery = false },
            icon = { Icon(SfIcons.Battery, null) },
            title = { Text("Allow unrestricted battery use?") },
            text = {
                Text(
                    "Android slows down apps it saves battery on, so frames drop and the first scrolls stutter. " +
                        "Unrestricted use lets Legato keep the faster refresh rate. You can change this later in Android's settings.",
                )
            },
            confirmButton = { SheetButton("Allow", onClick = { askBattery = false; requestUnrestricted(context) }) },
            dismissButton = { SheetButton("Not now", onClick = { askBattery = false }, closes = true) },
        )
    }
}

/** Whether the battery prompt has come up this run, so opening the page again doesn't ask again. */
private var askedThisRun = false

private fun isUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Opens Android's own prompt for this app; the general battery list where that isn't there. */
@SuppressLint("BatteryLife")
private fun requestUnrestricted(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

/** The refresh rates offered: 60, 90 and 120 Hz, those the screen has at its current resolution. */
public val OFFERED_RATES: List<Int> = listOf(60, 90, 120)

/** The refresh rates the screen offers at its current resolution, of [OFFERED_RATES], slowest first. */
private fun supportedRates(context: Context): List<Int> {
    @Suppress("DEPRECATION")
    val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display else
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay) ?: return emptyList()
    val now = display.mode
    return display.supportedModes
        .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
        .map { it.refreshRate.roundToInt() }
        .filter { it in OFFERED_RATES }
        .distinct()
        .sorted()
}
