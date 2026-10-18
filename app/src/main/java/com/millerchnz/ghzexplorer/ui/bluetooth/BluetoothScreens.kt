package com.millerchnz.ghzexplorer.ui.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.millerchnz.ghzexplorer.bluetooth.BluetoothFields
import com.millerchnz.ghzexplorer.bluetooth.BtSighting
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.data.ExportText
import com.millerchnz.ghzexplorer.ui.common.displayName
import com.millerchnz.ghzexplorer.ui.find.FindDeviceCard
import com.millerchnz.ghzexplorer.ui.common.AliasCard
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import com.millerchnz.ghzexplorer.ui.common.FieldDetail
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import com.millerchnz.ghzexplorer.ui.common.HEARD_WINDOW_MS
import com.millerchnz.ghzexplorer.ui.common.rememberFreshness
import com.millerchnz.ghzexplorer.ui.common.rememberHeldReading
import com.millerchnz.ghzexplorer.ui.common.staleElapsed
import com.millerchnz.ghzexplorer.ui.common.rememberLastHeardAt
import com.millerchnz.ghzexplorer.ui.common.refreshNote
import com.millerchnz.ghzexplorer.ui.common.wallClockFromElapsed
import com.millerchnz.ghzexplorer.ui.common.shareTextFile
import com.millerchnz.ghzexplorer.ui.common.SectionCard
import com.millerchnz.ghzexplorer.ui.common.SignalList
import com.millerchnz.ghzexplorer.ui.common.SignalRow
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor

@Composable
fun BluetoothScreen(onOpen: (String) -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val scanning by AppGraph.bluetooth.scanning.collectAsStateWithLifecycle()
    val status by AppGraph.bluetooth.status.collectAsStateWithLifecycle()
    val adapter by AppGraph.bluetooth.adapter.collectAsStateWithLifecycle()
    val sightings by AppGraph.bluetooth.sightings.collectAsStateWithLifecycle()
    val events by AppGraph.pairing.events.collectAsStateWithLifecycle()
    val aliases by AppGraph.aliases.names.collectAsStateWithLifecycle()
    val freshness = rememberFreshness()
    val rows = buildList {
        adapter?.let { add(it.toRow(aliases)) }
        sightings.forEach {
            add(it.toRow(aliases, stale = freshness.staleElapsed(AppGraph.bluetooth.lastHeardElapsed(it.key))))
        }
    }
    SignalList(
        title = "Bluetooth",
        intro = "BLE advertisements and classic devices, plus anything already bonded. Tap a device " +
            "for the fields Android reported. Discover services on the detail screen lists GATT " +
            "characteristics and does not read or write them.",
        status = status,
        rows = rows,
        emptyMessage = "No devices yet. Bonded devices appear as soon as a scan starts.",
        onOpen = onOpen,
        actionLabel = if (scanning) "Stop" else "Scan",
        onAction = AppGraph.bluetooth::toggle,
        onExport = {
            val devices = buildList {
                adapter?.let {
                    val row = it.toRow(aliases)
                    add(AppGraph.seen.device(row.title, row.subtitle, it.fields, it.key))
                }
                sightings.forEach {
                    val row = it.toRow(aliases)
                    add(AppGraph.seen.device(row.title, row.subtitle, it.fields, it.key))
                }
            }
            context.shareTextFile(
                ExportText.fileName("bluetooth", "devices"),
                ExportText.listDocument("Bluetooth", devices),
            )
        },
        onMap = onMap,
        actionModifier = Modifier.tourAnchor(TourAnchors.BLUETOOTH_SCAN),
        banner = {
            PermissionBanner(
                permissions = AppPermissions.bluetooth() + AppPermissions.notifications(),
                message = "Bluetooth scan and connect let the list fill in. Notifications are used when " +
                    "something tries to pair while this app is in the background.",
            )
        },
        header = {
            ColumnHeader(events = events, onEnableBluetooth = {
                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            })
        },
    )
}

private fun BtSighting.listTitle(aliases: Map<String, String>): String = aliases.displayName(key, title)

private fun BtSighting.toRow(aliases: Map<String, String>, stale: Boolean = false): SignalRow {
    val shown = listTitle(aliases)
    val subtitle = BluetoothFields.listSubtitle(shown, alias, manufacturers, subtitle)
    return SignalRow(key, shown, subtitle, trailing, stale = stale)
}

@Composable
private fun ColumnHeader(
    events: List<com.millerchnz.ghzexplorer.bluetooth.PairingLog.Event>,
    onEnableBluetooth: () -> Unit,
) {
    SectionCard(
        title = "Pairing and connections",
        subtitle = "Devices that asked this phone to pair, or that opened a link to it. The system " +
            "dialog still accepts or rejects pairing.",
        modifier = Modifier.tourAnchor(TourAnchors.PAIRING),
    ) {
        if (events.isEmpty()) {
            Text(
                "Nothing yet. Leave the app running and a pairing request will show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            events.take(5).forEach { event ->
                Text(event.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${event.line}\n${event.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Button(onClick = onEnableBluetooth) { Text("Turn Bluetooth on") }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun BluetoothDetailScreen(key: String, onBack: () -> Unit, onFind: () -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val held = rememberHeldReading(key, AppGraph.bluetooth.sighting(key)) {
        AppGraph.bluetooth.readingIfPresent(key, HEARD_WINDOW_MS)
    }
    val sighting = held.value
    val looks by AppGraph.services.looks.collectAsStateWithLifecycle()
    val look = sighting?.address?.let { looks[it] }
    val address = sighting?.address
    DisposableEffect(address) {
        onDispose {
            AppGraph.services.cancel()
            if (address != null && AppGraph.links.state.value?.address == address) {
                AppGraph.links.disconnect()
            }
        }
    }
    val aliases by AppGraph.aliases.names.collectAsStateWithLifecycle()
    val fields = sighting?.fields.orEmpty() + look?.fields.orEmpty()
    val title = aliases.displayName(key, sighting?.title ?: "Bluetooth")
    val freshness = rememberFreshness()
    val heardElapsed = AppGraph.bluetooth.lastHeardElapsed(key)
    val lastHeard = rememberLastHeardAt(key, wallClockFromElapsed(heardElapsed))
    val stale = freshness.staleElapsed(heardElapsed)
    FieldDetail(
        title = title,
        fields = fields,
        onBack = onBack,
        note = refreshNote(!stale),
        lastHeardMillis = lastHeard,
        stale = stale,
        onExport = {
            context.shareTextFile(
                ExportText.fileName("bluetooth", title),
                ExportText.document(
                    "Bluetooth",
                    title,
                    fields,
                    stopped = stale,
                    seen = AppGraph.seen.span(key),
                ),
            )
        },
        onMap = onMap,
        header = {
            AliasCard(key)
            Spacer(Modifier.height(12.dp))
            if (address != null) {
                FindDeviceCard(onFind)
                Spacer(Modifier.height(12.dp))
                ConnectCard(address)
            }
        },
        footer = {
            val address = sighting?.address ?: return@FieldDetail
            SectionCard(
                title = "Services",
                subtitle = look?.status ?: "Connects, lists services and characteristics, then disconnects. " +
                    "No characteristic is read or written, and pairing is not accepted.",
            ) {
                Button(
                    onClick = {
                        val device = AppGraph.bluetooth.device(address) ?: return@Button
                        AppGraph.services.start(device)
                    },
                    enabled = look?.running != true,
                ) {
                    Text(if (look?.running == true) "Discovering…" else "Discover services")
                }
            }
        },
    )
}

@Composable
private fun ConnectCard(address: String) {
    val events by AppGraph.pairing.events.collectAsStateWithLifecycle()
    val link by AppGraph.links.state.collectAsStateWithLifecycle()
    val bond = remember(events, address) {
        AppGraph.bluetooth.device(address)?.let { runCatching { it.bondState }.getOrNull() }
    }
    val ours = link?.takeIf { it.address == address }
    val paired = bond == BluetoothDevice.BOND_BONDED
    val pairing = bond == BluetoothDevice.BOND_BONDING
    SectionCard(
        title = "Connect and pair",
        subtitle = ours?.message
            ?: "Connect opens a link and does not read data. Pair starts the system dialog, which is where you confirm.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ours?.connected == true) {
                Button(onClick = { AppGraph.links.disconnect() }) { Text("Disconnect") }
            } else {
                Button(onClick = {
                    val device = AppGraph.bluetooth.device(address) ?: return@Button
                    AppGraph.links.connect(device)
                }) { Text("Connect") }
            }
            Button(
                onClick = {
                    val device = AppGraph.bluetooth.device(address) ?: return@Button
                    AppGraph.bluetooth.pauseDiscoveryForPairing()
                    AppGraph.links.pair(device)
                },
                enabled = !paired && !pairing,
            ) {
                Text(
                    when {
                        paired -> "Paired"
                        pairing -> "Pairing…"
                        else -> "Pair"
                    },
                )
            }
        }
    }
}

