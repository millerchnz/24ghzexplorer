package com.millerchnz.ghzexplorer.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

const val REFRESH_MS = 2_000L

/** How long a device can stay quiet before a detail screen stops refreshing it. */
const val HEARD_WINDOW_MS = 10_000L

data class Held<T>(val value: T?, val live: Boolean)

/**
 * Keeps the reading shown when the screen opened, and checks again every 2 seconds.
 * A null from [refresh] means the device has gone quiet: the last value stays, and the
 * text is stale. A later non-null reading replaces it and the page is live again.
 */
@Composable
fun <T> rememberHeldReading(key: String, initial: T?, refresh: () -> T?): Held<T> {
    var value by remember(key) { mutableStateOf(initial) }
    var live by remember(key) { mutableStateOf(initial != null) }
    LaunchedEffect(key) {
        if (initial == null) return@LaunchedEffect
        while (true) {
            val latest = refresh()
            if (latest == null) {
                live = false
            } else {
                live = true
                value = latest
            }
            delay(REFRESH_MS)
        }
    }
    return Held(value, live)
}

data class Freshness(val elapsedMs: Long, val wallMs: Long)

/** Ticks once a second so a row can turn stale without waiting for the next scan. */
@Composable
fun rememberFreshness(): Freshness {
    var freshness by remember {
        mutableStateOf(Freshness(SystemClock.elapsedRealtime(), System.currentTimeMillis()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            freshness = Freshness(SystemClock.elapsedRealtime(), System.currentTimeMillis())
        }
    }
    return freshness
}

fun Freshness.staleElapsed(heardAtElapsedMs: Long): Boolean =
    heardAtElapsedMs <= 0L || elapsedMs - heardAtElapsedMs > HEARD_WINDOW_MS

fun Freshness.staleWall(heardAtWallMs: Long): Boolean =
    heardAtWallMs <= 0L || wallMs - heardAtWallMs > HEARD_WINDOW_MS

/** Wall-clock millis for a stamp taken from [SystemClock.elapsedRealtime], or null when unknown. */
fun wallClockFromElapsed(heardElapsedMs: Long): Long? {
    if (heardElapsedMs <= 0L) return null
    return System.currentTimeMillis() - SystemClock.elapsedRealtime() + heardElapsedMs
}

/**
 * Keeps the last positive wall-clock stamp for [key]. A later 0 or null leaves that stamp in place,
 * so a device that goes quiet still shows when it was last heard.
 */
@Composable
fun rememberLastHeardAt(key: String, wallMillis: Long?): Long? {
    var kept by remember(key) { mutableStateOf(wallMillis?.takeIf { it > 0L }) }
    val latest = wallMillis?.takeIf { it > 0L }
    LaunchedEffect(latest) {
        if (latest != null) kept = latest
    }
    return latest ?: kept
}

fun refreshNote(live: Boolean): String = if (live) {
    "Refreshing every 2 seconds while this device is still heard."
} else {
    "Not heard in the last 10 seconds. Showing the last reading until it is heard again."
}
