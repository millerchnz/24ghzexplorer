package com.millerchnz.ghzexplorer.ui.wifi

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.data.ExportText
import com.millerchnz.ghzexplorer.ui.common.AliasCard
import com.millerchnz.ghzexplorer.ui.common.displayName
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import com.millerchnz.ghzexplorer.ui.common.FieldDetail
import com.millerchnz.ghzexplorer.ui.common.HEARD_WINDOW_MS
import com.millerchnz.ghzexplorer.ui.common.rememberFreshness
import com.millerchnz.ghzexplorer.ui.common.rememberHeldReading
import com.millerchnz.ghzexplorer.ui.common.rememberLastHeardAt
import com.millerchnz.ghzexplorer.ui.common.wallClockFromElapsed
import com.millerchnz.ghzexplorer.ui.common.refreshNote
import com.millerchnz.ghzexplorer.ui.common.staleElapsed
import com.millerchnz.ghzexplorer.ui.common.shareTextFile
import com.millerchnz.ghzexplorer.ui.find.FindDeviceCard
import com.millerchnz.ghzexplorer.ui.common.LocationBanner
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import com.millerchnz.ghzexplorer.ui.common.SectionCard
import com.millerchnz.ghzexplorer.ui.common.SignalList
import com.millerchnz.ghzexplorer.ui.common.SignalRow
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor
import com.millerchnz.ghzexplorer.wifi.WifiFields
import com.millerchnz.ghzexplorer.wifi.WifiSecurity
import com.millerchnz.ghzexplorer.wifi.WifiSighting

@Composable
fun WifiScreen(onOpen: (String) -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val scanning by AppGraph.wifi.scanning.collectAsStateWithLifecycle()
    val status by AppGraph.wifi.status.collectAsStateWithLifecycle()
    val link by AppGraph.wifi.link.collectAsStateWithLifecycle()
    val sightings by AppGraph.wifi.sightings.collectAsStateWithLifecycle()
    val aliases by AppGraph.aliases.names.collectAsStateWithLifecycle()
    val freshness = rememberFreshness()
    val rows = buildList {
        link?.let { add(SignalRow(it.key, aliases.displayName(it.key, it.title), it.subtitle, it.trailing)) }
        sightings.forEach {
            add(
                SignalRow(
                    it.key,
                    aliases.displayName(it.key, it.title),
                    it.subtitle,
                    it.trailing,
                    stale = freshness.staleElapsed(AppGraph.wifi.lastHeardElapsed(it.key)),
                ),
            )
        }
    }
    SignalList(
        title = "Wi-Fi",
        intro = "Access points in the order they were first discovered. Tap a row for every field " +
            "ScanResult reported, including information elements.",
        status = status,
        rows = rows,
        emptyMessage = "No access points yet.",
        onOpen = onOpen,
        actionLabel = if (scanning) "Stop" else "Scan",
        onAction = AppGraph.wifi::toggle,
        onExport = {
            val devices = buildList {
                link?.let { add(AppGraph.seen.device(aliases.displayName(it.key, it.title), it.subtitle, it.fields, it.key)) }
                sightings.forEach { add(AppGraph.seen.device(aliases.displayName(it.key, it.title), it.subtitle, it.fields, it.key)) }
            }
            context.shareTextFile(
                ExportText.fileName("wifi", "devices"),
                ExportText.listDocument("Wi-Fi", devices),
            )
        },
        onMap = onMap,
        actionModifier = Modifier.tourAnchor(TourAnchors.WIFI_SCAN),
        mapModifier = Modifier.tourAnchor(TourAnchors.LIST_MAP),
        exportModifier = Modifier.tourAnchor(TourAnchors.EXPORT),
        banner = {
            PermissionBanner(
                permissions = AppPermissions.wifi(),
                message = "Wi-Fi scan results are withheld until location and nearby-Wi-Fi permission are granted.",
            )
            Spacer(Modifier.height(10.dp))
            LocationBanner()
        },
        header = {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                Text("Open Wi-Fi settings")
            }
        },
    )
}

@Composable
fun WifiDetailScreen(key: String, onBack: () -> Unit, onFind: () -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val aliases by AppGraph.aliases.names.collectAsStateWithLifecycle()
    val freshness = rememberFreshness()
    val held = rememberHeldReading(key, AppGraph.wifi.sighting(key)) {
        AppGraph.wifi.poll()
        AppGraph.wifi.sighting(key)
    }
    val sighting = held.value
    val join by AppGraph.wifiJoin.state.collectAsStateWithLifecycle()
    var password by remember(key) { mutableStateOf("") }
    var radioNote by remember(key) { mutableStateOf<String?>(null) }
    val fields = sighting?.fields.orEmpty()
    val title = aliases.displayName(key, sighting?.title ?: "Wi-Fi")
    val heardElapsed = AppGraph.wifi.lastHeardElapsed(key)
    val lastHeard = rememberLastHeardAt(key, wallClockFromElapsed(heardElapsed))
    val stale = freshness.staleElapsed(heardElapsed) || !held.live
    FieldDetail(
        title = title,
        fields = fields,
        onBack = onBack,
        note = refreshNote(!stale),
        lastHeardMillis = lastHeard,
        stale = stale,
        onExport = {
            context.shareTextFile(
                ExportText.fileName("wifi", title),
                ExportText.document(
                    "Wi-Fi",
                    title,
                    fields,
                    stopped = stale,
                    seen = AppGraph.seen.span(key),
                ),
            )
        },
        onMap = onMap,
        missingMessage = if (key == WifiFields.LINK_KEY) {
            "This phone is not associated with a Wi-Fi network."
        } else {
            "This access point is no longer in the latest scan."
        },
        header = {
            AliasCard(key)
            Spacer(Modifier.height(12.dp))
            val network = sighting ?: return@FieldDetail
            FindDeviceCard(onFind)
            Spacer(Modifier.height(12.dp))
            if (key == WifiFields.LINK_KEY) {
                SectionCard(
                    title = "Disconnect",
                    subtitle = radioNote ?: "Asks Android to leave the Wi-Fi network this phone is using.",
                ) {
                    Button(onClick = { radioNote = AppGraph.wifiJoin.disconnectRadio() }) {
                        Text("Disconnect")
                    }
                }
            } else {
                JoinCard(
                    network = network,
                    password = password,
                    onPassword = { password = it },
                    status = when {
                        radioNote != null -> radioNote
                        join.ssid == network.ssid -> join.message
                        else -> "Android shows a prompt before it joins. The password is not stored."
                    },
                    holding = join.holding && join.ssid == network.ssid,
                    onConnect = { AppGraph.wifiJoin.connect(network, password) },
                    onDisconnect = { AppGraph.wifiJoin.disconnect() },
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                )
            }
        },
    )
}

@Composable
private fun JoinCard(
    network: WifiSighting,
    password: String,
    onPassword: (String) -> Unit,
    status: String?,
    holding: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val blocked = network.ssid.isNullOrBlank() ||
        network.security == WifiSecurity.ENTERPRISE ||
        network.security == WifiSecurity.WEP
    SectionCard(
        title = "Connect",
        subtitle = status,
    ) {
        if (network.ssid.isNullOrBlank()) {
            Text("No SSID was reported, so this app cannot ask Android to join it.")
            return@SectionCard
        }
        if (network.security == WifiSecurity.ENTERPRISE) {
            Text("This network wants an enterprise login. That has to be entered in the system Wi-Fi screen.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenSettings) { Text("Wi-Fi settings") }
            return@SectionCard
        }
        if (network.security == WifiSecurity.WEP) {
            Text("Android's connect request does not support WEP.")
            return@SectionCard
        }
        if (network.security.needsPassphrase) {
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (network.security == WifiSecurity.WPA3) "WPA3 password" else "WPA2 password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (holding) {
                Button(onClick = onDisconnect) { Text("Disconnect") }
            }
            Button(
                onClick = onConnect,
                enabled = !blocked && (!network.security.needsPassphrase || password.length in 8..63),
            ) {
                Text(if (holding) "Connect again" else "Connect")
            }
        }
    }
}
