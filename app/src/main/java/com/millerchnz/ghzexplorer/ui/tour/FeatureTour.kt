package com.millerchnz.ghzexplorer.ui.tour

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.millerchnz.ghzexplorer.ui.nav.AppRoutes
import kotlinx.coroutines.flow.first

object TourAnchors {
    const val PERMISSIONS = "permissions"
    const val WIFI_SCAN = "wifi_scan"
    const val EXPORT = "export"
    const val LIST_MAP = "list_map"
    const val FIND_DEVICE = "find_device"
    const val BLUETOOTH_SCAN = "bluetooth_scan"
    const val PAIRING = "pairing"
    const val NFC_READER = "nfc_reader"
    const val RADIO_REFRESH = "radio_refresh"
    const val BOTTOM_NAV = "bottom_nav"
}

data class TourStep(
    val anchor: String,
    val route: String,
    val title: String,
    val body: String,
)

val TourSteps: List<TourStep> = listOf(
    TourStep(
        anchor = TourAnchors.PERMISSIONS,
        route = AppRoutes.SETTINGS,
        title = "What this app can read",
        body = "Each tab asks for the permission that radio needs. Android hides scan results until " +
            "you grant them. A value the API leaves unset is shown as a dash, so a missing reading " +
            "never looks like zero.",
    ),
    TourStep(
        anchor = TourAnchors.WIFI_SCAN,
        route = AppRoutes.WIFI,
        title = "Wi-Fi networks",
        body = "Scan lists access points the phone can hear. Tap one for the SSID, BSSID, security, " +
            "channel, and every information element Android returned. Scans are throttled to a few " +
            "every two minutes. A row turns red when that access point has not been heard for 10 " +
            "seconds. The network this phone is using stays the normal colour.",
    ),
    TourStep(
        anchor = TourAnchors.EXPORT,
        route = AppRoutes.WIFI,
        title = "Export a reading",
        body = "Export shares every device on this page as a text file. On an open device, Export " +
            "shares that device alone. Bluetooth, NFC and Radio have the same button. A Wi-Fi " +
            "password is left out.",
    ),
    TourStep(
        anchor = TourAnchors.LIST_MAP,
        route = AppRoutes.WIFI,
        title = "Map of this tab",
        body = "Map is a live view of every device this tab is hearing. A device is drawn after " +
            "three places. A fourth place is kept, and any later place replaces the oldest. It " +
            "leaves when those samples stop. Gold marks are devices. The teal mark is you. Tap a " +
            "marker for its latest fields. Bluetooth and Radio have the same map.",
    ),
    TourStep(
        anchor = TourAnchors.FIND_DEVICE,
        route = AppRoutes.WIFI,
        title = "Find one device",
        body = "Find device follows one signal while you walk. The device is drawn after three " +
            "places, in gold, and you are the teal mark. A fourth place is kept. A later place " +
            "replaces the oldest, and the circle shrinks as the signal is heard from more " +
            "directions. It is on a Wi-Fi network, a remote Bluetooth device, or a cell.",
    ),
    TourStep(
        anchor = TourAnchors.BLUETOOTH_SCAN,
        route = AppRoutes.BLUETOOTH,
        title = "Bluetooth devices",
        body = "This listens for BLE advertisements and classic inquiry, and keeps bonded devices in " +
            "the list even when they are quiet. A row turns red when that device has not been heard " +
            "for 10 seconds, including a bonded device this scan has not heard. This phone's own " +
            "radio stays the normal colour. Tap a device for the address, bond, class, and advertisement.",
    ),
    TourStep(
        anchor = TourAnchors.PAIRING,
        route = AppRoutes.BLUETOOTH,
        title = "Who is pairing",
        body = "Pairing requests, bond changes, and connections to this phone are listed here. The app " +
            "does not accept the pairing. The system dialog still does. A notification appears if a " +
            "request arrives while this app is in the background.",
    ),
    TourStep(
        anchor = TourAnchors.NFC_READER,
        route = AppRoutes.NFC,
        title = "NFC tags",
        body = "Hold a card or tag against the phone while this tab is open. The reading includes the " +
            "identifier, technologies, and any NDEF records. Nothing is written back to the tag.",
    ),
    TourStep(
        anchor = TourAnchors.RADIO_REFRESH,
        route = AppRoutes.RADIO,
        title = "Cellular cells",
        body = "Refresh asks the modem which cells it can see, and opens every identity and signal " +
            "field Android reports. A cell turns red when its report is more than 10 seconds old. " +
            "This phone's own radios stay the normal colour. Neighbours are often withheld by the phone itself.",
    ),
)

val LocalFeatureTour = staticCompositionLocalOf<FeatureTourController?> { null }

class FeatureTourController(
    initialIndex: Int = -1,
    private val onFinished: () -> Unit = {},
    private val onIndex: (Int) -> Unit = {},
) {
    var index by mutableIntStateOf(initialIndex.coerceIn(-1, TourSteps.lastIndex))
        private set

    val bounds = mutableStateMapOf<String, Rect>()

    val step: TourStep? get() = TourSteps.getOrNull(index)
    val active: Boolean get() = step != null
    val isLast: Boolean get() = index == TourSteps.lastIndex

    fun start() = moveTo(0)

    fun next() {
        if (!active) return
        if (isLast) finish() else moveTo(index + 1)
    }

    fun previous() {
        if (index > 0) moveTo(index - 1)
    }

    fun skipAll() {
        if (active) finish()
    }

    fun report(key: String, rect: Rect) {
        val stable = rect.rounded()
        if (bounds[key] == stable) return
        bounds[key] = stable
    }

    fun forget(key: String) {
        bounds.remove(key)
    }

    private fun finish() {
        moveTo(-1)
        bounds.clear()
        onFinished()
    }

    private fun moveTo(value: Int) {
        index = value
        onIndex(value)
    }
}

private fun Rect.rounded(): Rect = Rect(
    left = left.toInt().toFloat(),
    top = top.toInt().toFloat(),
    right = right.toInt().toFloat(),
    bottom = bottom.toInt().toFloat(),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourAnchor(key: String): Modifier {
    val tour = LocalFeatureTour.current
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var nodeSize by remember { mutableStateOf(IntSize.Zero) }
    var positioned by remember { mutableStateOf(false) }
    val active = tour?.step?.anchor == key

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { positioned }.first { it }
        val size = nodeSize
        val extraBelow = with(density) { 220.dp.toPx() }
        requester.bringIntoView(
            Rect(
                left = 0f,
                top = 0f,
                right = size.width.toFloat().coerceAtLeast(1f),
                bottom = size.height + extraBelow,
            ),
        )
    }
    DisposableEffect(tour, key) {
        onDispose { tour?.forget(key) }
    }
    if (tour == null) return this
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            val size = coordinates.size
            if (size.width <= 0 || size.height <= 0) return@onGloballyPositioned
            nodeSize = size
            positioned = true
            tour.report(key, coordinates.boundsInRoot())
        }
}
