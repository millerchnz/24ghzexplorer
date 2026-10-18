package com.millerchnz.ghzexplorer.ui.find

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.find.SignalReading
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor
import com.millerchnz.ghzexplorer.ui.common.LocationBanner
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(kind: String, key: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = viewModel<FindSession>()
    var follow by remember { mutableStateOf(true) }
    var recenter by remember { mutableIntStateOf(0) }
    var locationTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) locationTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permissions = findPermissions(kind)
    val allowed = remember(locationTick) {
        AppPermissions.missing(context, permissions).isEmpty() && AppPermissions.locationEnabled(context)
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(kind) {
        val startedBluetooth = kind == "bluetooth" && !AppGraph.bluetooth.scanning.value
        if (startedBluetooth) AppGraph.bluetooth.start(clear = false)
        onDispose {
            if (startedBluetooth) AppGraph.bluetooth.stop()
        }
    }
    if (allowed) {
        WalkUpdates(kind = kind, deviceKey = key, session = session)
    }
    val title = deviceTitle(kind, key)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find $title", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val latitude = session.userLatitude
            val longitude = session.userLongitude
            if (latitude != null && longitude != null) {
                FindMap(
                    userLatitude = latitude,
                    userLongitude = longitude,
                    trail = session.trail,
                    estimate = session.estimate,
                    follow = follow,
                    recenterGeneration = recenter,
                    onUserPan = { follow = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PermissionBanner(
                    permissions = permissions,
                    message = "Precise location is what places you on the map. The radio permission is what hears the device.",
                    onChanged = { locationTick++ },
                )
                LocationBanner()
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(findStatus(session, allowed), style = MaterialTheme.typography.bodyMedium)
                        if (!follow && latitude != null) {
                            Button(onClick = {
                                follow = true
                                recenter++
                            }) { Text("Recenter") }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun WalkUpdates(kind: String, deviceKey: String, session: FindSession) {
    val context = LocalContext.current
    var latest by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location -> latest = location }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) return@forEach
            runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 1f, listener, Looper.getMainLooper())
            }
            runCatching { manager.getLastKnownLocation(provider) }?.getOrNull()?.let { known ->
                val current = latest
                if (current == null || known.time > current.time) latest = known
            }
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }
    LaunchedEffect(kind, deviceKey) {
        var ticks = 0
        while (true) {
            if (kind == "wifi") AppGraph.wifi.pokeScan()
            if (kind == "radio" && ticks % 8 == 0) AppGraph.cells.refresh()
            val location = latest
            if (location != null) {
                session.onFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.toDouble(),
                    deviceKey = deviceKey,
                    reading = readSignal(kind, deviceKey),
                )
            }
            delay(1_000)
            ticks++
        }
    }
}

private fun readSignal(kind: String, key: String): SignalReading? = when (kind) {
    "wifi" -> AppGraph.wifi.signal(key)
    "bluetooth" -> AppGraph.bluetooth.signal(key)
    "radio" -> AppGraph.cells.signal(key)
    else -> null
}

private fun deviceTitle(kind: String, key: String): String = when (kind) {
    "wifi" -> AppGraph.wifi.sighting(key)?.title ?: "Wi-Fi"
    "bluetooth" -> AppGraph.bluetooth.sighting(key)?.title ?: "Bluetooth"
    "radio" -> AppGraph.cells.sighting(key)?.title ?: "Cell"
    else -> "Device"
}

private fun findPermissions(kind: String): Array<String> = when (kind) {
    "wifi" -> AppPermissions.wifi()
    "bluetooth" -> (AppPermissions.bluetooth().toList() + Manifest.permission.ACCESS_FINE_LOCATION)
        .distinct()
        .toTypedArray()
    else -> AppPermissions.cells()
}

private fun findStatus(session: FindSession, allowed: Boolean): String {
    if (!allowed) return "Grant location, then walk outdoors around the device."
    if (session.userLatitude == null) return "Waiting for GPS. A clear view of the sky helps."
    val radius = session.estimate?.radiusMeters?.toInt()
    val rssi = session.lastRssi
    if (!session.hearing || rssi == null) {
        return if (session.estimate == null) {
            "Waiting for a signal from this device."
        } else {
            "Not hearing it now. The last circle is still on the map."
        }
    }
    if (session.sampleCount < 3 || radius == null) {
        val left = (3 - session.sampleCount).coerceAtLeast(1)
        val places = if (left == 1) "place" else "places"
        return "Signal $rssi dBm. Walk to $left more $places."
    }
    return "$rssi dBm from ${session.sampleCount} places. The circle is about $radius m."
}

@Composable
fun FindDeviceCard(onFind: () -> Unit) {
    com.millerchnz.ghzexplorer.ui.common.SectionCard(
        title = "Find",
        subtitle = "Walk outdoors. The device is drawn after three places, and the newest four are kept.",
    ) {
        Button(
            onClick = onFind,
            modifier = Modifier.tourAnchor(TourAnchors.FIND_DEVICE),
        ) { Text("Find device") }
    }
}
