package com.millerchnz.ghzexplorer.ui.find

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.millerchnz.ghzexplorer.find.DeviceEstimate
import com.millerchnz.ghzexplorer.find.SignalFix
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun FindMap(
    userLatitude: Double,
    userLongitude: Double,
    trail: List<SignalFix>,
    estimate: DeviceEstimate?,
    follow: Boolean,
    recenterGeneration: Int,
    onUserPan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dotPx = with(density) { 18.dp.roundToPx() }
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
                if (event.actionMasked == MotionEvent.ACTION_MOVE) onUserPan()
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
            estimate?.let { found ->
                val circle = Polygon(map)
                circle.points = Polygon.pointsAsCircle(GeoPoint(found.latitude, found.longitude), found.radiusMeters)
                circle.fillPaint.color = Color(0x66F4C95D).toArgb()
                circle.outlinePaint.color = Color(0xFFF4C95D).toArgb()
                circle.outlinePaint.strokeWidth = 4f
                map.overlays.add(circle)
            }
            if (trail.size >= 2) {
                val line = Polyline(map)
                line.setPoints(trail.map { GeoPoint(it.latitude, it.longitude) })
                line.outlinePaint.color = Color(0xFF4ECDC4).toArgb()
                line.outlinePaint.strokeWidth = 6f
                map.overlays.add(line)
            }
            map.overlays.add(mapDot(map, user, Color(0xFF4ECDC4), dotPx, "You"))
            estimate?.let { found ->
                map.overlays.add(mapDot(map, GeoPoint(found.latitude, found.longitude), Color(0xFFF4C95D), dotPx, "Device"))
            }
            map.invalidate()
        },
    )
}

internal fun mapDot(map: MapView, position: GeoPoint, color: Color, sizePx: Int, title: String): Marker {
    return Marker(map).apply {
        this.position = position
        this.title = title
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.toArgb())
            setSize(sizePx, sizePx)
            setStroke((sizePx / 6).coerceAtLeast(2), android.graphics.Color.WHITE)
        }
    }
}
