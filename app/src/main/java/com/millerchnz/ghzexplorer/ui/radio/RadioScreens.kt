package com.millerchnz.ghzexplorer.ui.radio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.millerchnz.ghzexplorer.cell.CellFields
import com.millerchnz.ghzexplorer.cell.CellSighting
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.data.ExportText
import com.millerchnz.ghzexplorer.ui.find.FindDeviceCard
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import android.os.SystemClock
import com.millerchnz.ghzexplorer.ui.common.FieldDetail
import com.millerchnz.ghzexplorer.ui.common.HEARD_WINDOW_MS
import com.millerchnz.ghzexplorer.ui.common.REFRESH_MS
import com.millerchnz.ghzexplorer.ui.common.rememberFreshness
import com.millerchnz.ghzexplorer.ui.common.rememberLastHeardAt
import com.millerchnz.ghzexplorer.ui.common.staleElapsed
import com.millerchnz.ghzexplorer.ui.common.refreshNote
import com.millerchnz.ghzexplorer.ui.common.wallClockFromElapsed
import com.millerchnz.ghzexplorer.ui.common.shareTextFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.millerchnz.ghzexplorer.ui.common.LocationBanner
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import com.millerchnz.ghzexplorer.ui.common.SignalList
import com.millerchnz.ghzexplorer.ui.common.SignalRow
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor

@Composable
fun RadioScreen(onOpen: (String) -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val status by AppGraph.cells.status.collectAsStateWithLifecycle()
    val phone by AppGraph.cells.phone.collectAsStateWithLifecycle()
    val cells by AppGraph.cells.cells.collectAsStateWithLifecycle()
    val freshness = rememberFreshness()
    val rows = buildList {
        phone?.let { add(SignalRow(it.key, it.title, it.subtitle, it.trailing)) }
        cells.forEach {
            add(
                SignalRow(
                    it.key,
                    it.title,
                    it.subtitle,
                    it.trailing,
                    stale = freshness.staleElapsed(it.heardElapsedMs),
                ),
            )
        }
    }
    SignalList(
        title = "Radio",
        intro = "Cells the modem reports, plus the state of the Wi-Fi, Bluetooth and NFC radios in " +
            "this phone. Tap a cell for every identity and signal field.",
        status = status,
        rows = rows,
        emptyMessage = "No cells yet. Refresh after granting location.",
        onOpen = onOpen,
        actionLabel = "Refresh",
        onAction = { AppGraph.cells.refresh() },
        onExport = {
            val devices = buildList {
                phone?.let { add(AppGraph.seen.device(it.title, it.subtitle, it.fields, it.key)) }
                cells.forEach { add(AppGraph.seen.device(it.title, it.subtitle, it.fields, it.key)) }
            }
            context.shareTextFile(
                ExportText.fileName("radio", "devices"),
                ExportText.listDocument("Radio", devices),
            )
        },
        onMap = onMap,
        actionModifier = Modifier.tourAnchor(TourAnchors.RADIO_REFRESH),
        banner = {
            PermissionBanner(
                permissions = AppPermissions.cells(),
                message = "Precise location lets Android return cell identity and signal. Phone state " +
                    "fills in the data-network name.",
            )
            LocationBanner()
        },
    )
}

@Composable
fun RadioDetailScreen(key: String, onBack: () -> Unit, onFind: () -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    var page by remember(key) { mutableStateOf(AppGraph.cells.sighting(key)) }
    var live by remember(key) { mutableStateOf(page != null || key == CellFields.PHONE_KEY) }
    LaunchedEffect(key) {
        if (page == null && key != CellFields.PHONE_KEY) return@LaunchedEffect
        while (true) {
            delay(REFRESH_MS)
            if (key == CellFields.PHONE_KEY) {
                AppGraph.cells.refresh()
                page = AppGraph.cells.sighting(key) ?: page
                continue
            }
            val before = AppGraph.cells.updates.value
            AppGraph.cells.refresh()
            val updated = withTimeoutOrNull(5_000) {
                AppGraph.cells.updates.first { it != before }
            }
            if (updated == null) continue
            val latest = AppGraph.cells.sighting(key)
            if (latest == null) {
                live = false
            } else {
                page = latest
                val heard = latest.heardElapsedMs
                live = heard > 0L && SystemClock.elapsedRealtime() - heard <= HEARD_WINDOW_MS
            }
        }
    }
    val sighting: CellSighting? = page
    val fields = sighting?.fields.orEmpty()
    val heardElapsed = when {
        key == CellFields.PHONE_KEY && live -> SystemClock.elapsedRealtime()
        else -> sighting?.heardElapsedMs ?: 0L
    }
    val lastHeard = rememberLastHeardAt(key, wallClockFromElapsed(heardElapsed))
    FieldDetail(
        title = sighting?.title ?: "Radio",
        fields = fields,
        onBack = onBack,
        missingMessage = if (key == CellFields.PHONE_KEY) {
            "Refresh the radio tab to read this phone's radios."
        } else {
            "That cell is no longer in the latest modem report."
        },
        note = refreshNote(live),
        lastHeardMillis = lastHeard,
        stale = !live,
        header = {
            if (key != CellFields.PHONE_KEY && sighting != null) FindDeviceCard(onFind)
        },
        onMap = onMap,
        onExport = {
            val title = sighting?.title ?: "Radio"
            context.shareTextFile(
                ExportText.fileName("radio", title),
                ExportText.document(
                    "Radio",
                    title,
                    fields,
                    stopped = !live,
                    seen = AppGraph.seen.span(key),
                ),
            )
        },
    )
}
