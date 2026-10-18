package com.millerchnz.ghzexplorer.ui.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.data.ExportText
import com.millerchnz.ghzexplorer.nfc.NfcSighting
import com.millerchnz.ghzexplorer.ui.common.FieldDetail
import com.millerchnz.ghzexplorer.ui.common.HEARD_WINDOW_MS
import com.millerchnz.ghzexplorer.ui.common.REFRESH_MS
import com.millerchnz.ghzexplorer.ui.common.SignalList
import com.millerchnz.ghzexplorer.ui.common.rememberLastHeardAt
import com.millerchnz.ghzexplorer.ui.common.refreshNote
import com.millerchnz.ghzexplorer.ui.common.shareTextFile
import kotlinx.coroutines.delay
import com.millerchnz.ghzexplorer.ui.common.SignalRow
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor

@Composable
fun NfcScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val status by AppGraph.nfc.status.collectAsStateWithLifecycle()
    val sightings by AppGraph.nfc.sightings.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            if (activity == null) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> AppGraph.nfc.enable(activity)
                Lifecycle.Event.ON_PAUSE -> AppGraph.nfc.disable(activity)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity != null) {
            AppGraph.nfc.enable(activity)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity != null) AppGraph.nfc.disable(activity)
        }
    }
    SignalList(
        title = "NFC",
        intro = "Reader mode is on while this tab is open. Hold a card or tag flat against the back " +
            "of the phone. The list keeps the tags read in this session. Nothing is written to them.",
        status = status,
        rows = sightings.map { SignalRow(it.key, it.title, it.subtitle, it.trailing) },
        emptyMessage = "No tag yet.",
        onOpen = onOpen,
        onExport = {
            val devices = sightings.map { AppGraph.seen.device(it.title, it.subtitle, it.fields, it.key) }
            context.shareTextFile(
                ExportText.fileName("nfc", "devices"),
                ExportText.listDocument("NFC", devices),
            )
        },
        header = {
            TextButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                modifier = androidx.compose.ui.Modifier.tourAnchor(TourAnchors.NFC_READER),
            ) {
                Text("Open NFC settings")
            }
        },
    )
}

@Composable
fun NfcDetailScreen(key: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var page by remember(key) { mutableStateOf(AppGraph.nfc.sighting(key)) }
    var live by remember(key) { mutableStateOf(page != null) }
    LaunchedEffect(key) {
        val start = page ?: return@LaunchedEffect
        var seenAt = start.scannedAt
        while (true) {
            val newer = AppGraph.nfc.newerReading(start.uid, seenAt)
            if (newer == null) {
                live = System.currentTimeMillis() - seenAt <= HEARD_WINDOW_MS
            } else {
                page = newer
                seenAt = newer.scannedAt
                live = true
            }
            delay(REFRESH_MS)
        }
    }
    val sighting: NfcSighting? = page
    val fields = sighting?.fields.orEmpty()
    val lastHeard = rememberLastHeardAt(key, sighting?.scannedAt)
    FieldDetail(
        title = sighting?.title ?: "NFC tag",
        fields = fields,
        onBack = onBack,
        missingMessage = "That tag is no longer in this session.",
        note = refreshNote(live),
        lastHeardMillis = lastHeard,
        stale = !live,
        onExport = {
            val title = sighting?.title ?: "NFC"
            context.shareTextFile(
                ExportText.fileName("nfc", title),
                ExportText.document(
                    "NFC",
                    title,
                    fields,
                    stopped = !live,
                    seen = AppGraph.seen.span(key),
                ),
            )
        },
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
