package com.millerchnz.ghzexplorer.ui.find

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.millerchnz.ghzexplorer.bluetooth.BluetoothFields
import com.millerchnz.ghzexplorer.cell.CellFields
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.data.ExportText
import com.millerchnz.ghzexplorer.find.DevicePlot
import com.millerchnz.ghzexplorer.find.LiveSurvey
import com.millerchnz.ghzexplorer.find.SurveyDevice
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import com.millerchnz.ghzexplorer.ui.common.HEARD_WINDOW_MS
import com.millerchnz.ghzexplorer.ui.common.LocationBanner
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import com.millerchnz.ghzexplorer.ui.common.shareTextFile
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon

private val deviceColor = Color(0xFFF4C95D)
private val youColor = Color(0xFF4ECDC4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotMapScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val survey = remember(kind) { LiveSurvey(kind, AppGraph.seen) }
    var userLatitude by remember { mutableStateOf<Double?>(null) }
    var userLongitude by remember { mutableStateOf<Double?>(null) }
    var plotted by remember { mutableStateOf<List<DevicePlot>>(emptyList()) }
    var hearing by remember { mutableIntStateOf(0) }
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
        LiveSamples(
            kind = kind,
            survey = survey,
            onFrame = { latitude, longitude, devices, count ->
                userLatitude = latitude
                userLongitude = longitude
                plotted = devices
                hearing = count
            },
        )
    }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val chosen = plotted.firstOrNull { it.key == selectedKey }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${kindLabel(kind)} map") },
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
            val latitude = userLatitude
            val longitude = userLongitude
            if (latitude != null && longitude != null) {
                PlotMap(
                    userLatitude = latitude,
                    userLongitude = longitude,
                    plots = plotted,
                    follow = follow,
                    recenterGeneration = recenter,
                    onUserPan = { follow = false },
                    onSelect = { selectedKey = it },
                    onDismiss = { selectedKey = null },
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
                chosen?.let { plot ->
                    PlotDetails(plot = plot)
                }
                PermissionBanner(
                    permissions = permissions,
                    message = "Precise location places you on the map. The radio permission is what hears the devices.",
                    onChanged = { locationTick++ },
                )
                LocationBanner()
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            liveStatus(allowed, latitude != null, hearing, plotted.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
private fun LiveSamples(
    kind: String,
    survey: LiveSurvey,
    onFrame: (latitude: Double, longitude: Double, plotted: List<DevicePlot>, hearing: Int) -> Unit,
) {
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
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { known ->
                val current = latest
                if (current == null || known.time > current.time) latest = known
            }
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }
    LaunchedEffect(kind) {
        var ticks = 0
        while (true) {
            if (kind == "wifi") AppGraph.wifi.pokeScan()
            if (kind == "radio" && ticks % 8 == 0) AppGraph.cells.refresh()
            val location = latest
            if (location != null) {
                val heard = devicesBeingHeard(kind)
                onFrame(
                    location.latitude,
                    location.longitude,
                    survey.update(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.toDouble(),
                        heard = heard,
                    ),
                    heard.size,
                )
            }
            delay(1_000)
            ticks++
        }
    }
}

private fun devicesBeingHeard(kind: String): List<SurveyDevice> = when (kind) {
    "wifi" -> {
        val rows = buildList {
            AppGraph.wifi.link.value?.let { add(it) }
            addAll(AppGraph.wifi.sightings.value)
        }
        rows.mapNotNull { sighting ->
            val reading = AppGraph.wifi.signal(sighting.key) ?: return@mapNotNull null
            SurveyDevice(sighting.key, sighting.title, sighting.subtitle, sighting.fields, reading.rssiDbm, reading.stamp)
        }
    }
    "bluetooth" -> {
        val now = SystemClock.elapsedRealtime()
        AppGraph.bluetooth.sightings.value.mapNotNull { sighting ->
            if (sighting.key == BluetoothFields.ADAPTER_KEY) return@mapNotNull null
            val reading = AppGraph.bluetooth.signal(sighting.key) ?: return@mapNotNull null
            if (now - reading.stamp > HEARD_WINDOW_MS) return@mapNotNull null
            val extra = sighting.address?.let { AppGraph.services.looks.value[it]?.fields }.orEmpty()
            SurveyDevice(
                sighting.key,
                sighting.title,
                sighting.subtitle,
                sighting.fields + extra,
                reading.rssiDbm,
                reading.stamp,
            )
        }
    }
    "radio" -> AppGraph.cells.cells.value.mapNotNull { sighting ->
        if (sighting.key == CellFields.PHONE_KEY) return@mapNotNull null
        val reading = AppGraph.cells.signal(sighting.key) ?: return@mapNotNull null
        SurveyDevice(sighting.key, sighting.title, sighting.subtitle, sighting.fields, reading.rssiDbm, reading.stamp)
    }
    else -> emptyList()
}

@Composable
private fun PlotMap(
    userLatitude: Double,
    userLongitude: Double,
    plots: List<DevicePlot>,
    follow: Boolean,
    recenterGeneration: Int,
    onUserPan: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dotPx = with(density) { 22.dp.roundToPx() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            setDestroyMode(false)
            controller.setZoom(18.0)
            controller.setCenter(GeoPoint(userLatitude, userLongitude))
            setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) onUserPan()
                false
            }
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
        }
    }
    val lastRecenter = remember { intArrayOf(-1) }
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            val user = GeoPoint(userLatitude, userLongitude)
            if (follow || lastRecenter[0] != recenterGeneration) {
                lastRecenter[0] = recenterGeneration
                if (map.zoomLevelDouble < 16.0) map.controller.setZoom(18.0)
                map.controller.setCenter(user)
            }
            map.overlays.clear()
            map.overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onDismiss()
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    },
                ),
            )
            plots.forEach { plot ->
                val estimate = plot.estimate ?: return@forEach
                val at = GeoPoint(estimate.latitude, estimate.longitude)
                val circle = Polygon(map)
                circle.points = Polygon.pointsAsCircle(at, estimate.radiusMeters)
                circle.fillPaint.color = deviceColor.copy(alpha = 0.28f).toArgb()
                circle.outlinePaint.color = deviceColor.toArgb()
                circle.outlinePaint.strokeWidth = 4f
                circle.title = plot.title
                circle.infoWindow = null
                circle.setOnClickListener { _, _, _ ->
                    onSelect(plot.key)
                    true
                }
                map.overlays.add(circle)
                val marker = mapDot(map, at, deviceColor, dotPx, plot.title)
                marker.infoWindow = null
                marker.setOnMarkerClickListener { _, _ ->
                    onSelect(plot.key)
                    true
                }
                map.overlays.add(marker)
            }
            val you = mapDot(map, user, youColor, dotPx, "You")
            you.infoWindow = null
            you.setOnMarkerClickListener { _, _ -> false }
            map.overlays.add(you)
            map.invalidate()
        },
    )
}

@Composable
private fun PlotDetails(plot: DevicePlot) {
    val context = LocalContext.current
    val radius = plot.estimate?.radiusMeters?.toInt()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    plot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    context.shareTextFile(
                        ExportText.fileName(plot.kind, plot.title),
                        ExportText.document(
                            kind = kindLabel(plot.kind),
                            title = plot.title,
                            fields = plot.fields,
                            stopped = false,
                            seen = AppGraph.seen.span(plot.key),
                        ),
                    )
                }) { Text("Export") }
            }
            if (plot.subtitle.isNotBlank()) {
                Text(
                    plot.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${plot.samples.size} locations${radius?.let { " · circle about $it m" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plot.fields.groupBy { it.section }.forEach { (section, fields) ->
                        Text(section, style = MaterialTheme.typography.titleSmall)
                        fields.forEach { field ->
                            Text(
                                "${field.label}: ${field.display}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun liveStatus(allowed: Boolean, hasFix: Boolean, hearing: Int, plotted: Int): String = when {
    !allowed -> "Grant location, then walk. Devices appear after they are sampled in three places."
    !hasFix -> "Waiting for GPS. A clear view of the sky helps."
    hearing == 0 -> "No devices are being sampled. A device leaves the map when its samples stop."
    else -> "Sampling $hearing devices. $plotted on the map. A device needs three places. The newest four are kept, and it leaves when its samples stop."
}

private fun kindLabel(kind: String): String = when (kind) {
    "wifi" -> "Wi-Fi"
    "bluetooth" -> "Bluetooth"
    "radio" -> "Radio"
    else -> "Device"
}

private fun findPermissions(kind: String): Array<String> = when (kind) {
    "wifi" -> AppPermissions.wifi()
    "bluetooth" -> (AppPermissions.bluetooth().toList() + Manifest.permission.ACCESS_FINE_LOCATION)
        .distinct()
        .toTypedArray()
    else -> AppPermissions.cells()
}
