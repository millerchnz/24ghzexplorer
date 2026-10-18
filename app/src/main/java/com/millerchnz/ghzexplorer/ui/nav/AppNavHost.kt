package com.millerchnz.ghzexplorer.ui.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.millerchnz.ghzexplorer.bluetooth.BluetoothFields
import com.millerchnz.ghzexplorer.cell.CellFields
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.ui.bluetooth.BluetoothDetailScreen
import com.millerchnz.ghzexplorer.ui.bluetooth.BluetoothScreen
import com.millerchnz.ghzexplorer.ui.nfc.NfcDetailScreen
import com.millerchnz.ghzexplorer.ui.nfc.NfcScreen
import com.millerchnz.ghzexplorer.ui.radio.RadioDetailScreen
import com.millerchnz.ghzexplorer.ui.radio.RadioScreen
import com.millerchnz.ghzexplorer.ui.find.FindScreen
import com.millerchnz.ghzexplorer.ui.find.PlotMapScreen
import com.millerchnz.ghzexplorer.ui.settings.SettingsScreen
import com.millerchnz.ghzexplorer.ui.tour.FeatureTourController
import com.millerchnz.ghzexplorer.ui.tour.FeatureTourOverlay
import com.millerchnz.ghzexplorer.ui.tour.LocalFeatureTour
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor
import com.millerchnz.ghzexplorer.ui.wifi.WifiDetailScreen
import com.millerchnz.ghzexplorer.ui.wifi.WifiScreen

object AppRoutes {
    const val SETTINGS = "settings"
    const val WIFI = "wifi"
    const val BLUETOOTH = "bluetooth"
    const val NFC = "nfc"
    const val RADIO = "radio"
    const val WIFI_DETAIL = "wifi/detail?key={key}"
    const val BLUETOOTH_DETAIL = "bluetooth/detail?key={key}"
    const val NFC_DETAIL = "nfc/detail?key={key}"
    const val RADIO_DETAIL = "radio/detail?key={key}"
    const val FIND = "find/{kind}?key={key}"
    const val PLOT_MAP = "map/{kind}"

    fun wifiDetail(key: String) = "wifi/detail?key=${Uri.encode(key)}"
    fun bluetoothDetail(key: String) = "bluetooth/detail?key=${Uri.encode(key)}"
    fun nfcDetail(key: String) = "nfc/detail?key=${Uri.encode(key)}"
    fun radioDetail(key: String) = "radio/detail?key=${Uri.encode(key)}"
    fun find(kind: String, key: String) = "find/$kind?key=${Uri.encode(key)}"
    fun plotMap(kind: String) = "map/$kind"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(AppRoutes.WIFI, "Wi-Fi", Icons.Outlined.Wifi),
    BottomDestination(AppRoutes.BLUETOOTH, "BLE", Icons.Outlined.Bluetooth),
    BottomDestination(AppRoutes.NFC, "NFC", Icons.Outlined.Nfc),
    BottomDestination(AppRoutes.RADIO, "Radio", Icons.Outlined.CellTower),
    BottomDestination(AppRoutes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var savedTourIndex by rememberSaveable { mutableIntStateOf(-1) }
    val tour = remember {
        FeatureTourController(
            initialIndex = savedTourIndex,
            onFinished = { AppGraph.settings.setFeatureTourCompleted() },
            onIndex = { savedTourIndex = it },
        )
    }
    BackHandler(enabled = tour.active) { tour.previous() }
    LaunchedEffect(Unit) {
        if (!tour.active && !AppGraph.settings.isFeatureTourCompleted()) {
            tour.start()
        }
    }
    val stepAnchor = tour.step?.anchor
    val findTarget = remember(stepAnchor) {
        if (stepAnchor == TourAnchors.FIND_DEVICE) findDeviceTourRoute() else null
    }
    val tourDestination = when {
        tour.step == null -> null
        stepAnchor == TourAnchors.FIND_DEVICE -> findTarget ?: tour.step?.route
        else -> tour.step?.route
    }
    LaunchedEffect(tourDestination) {
        val route = tourDestination ?: return@LaunchedEffect
        if (navController.currentBackStackEntry.shows(route)) return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalFeatureTour provides tour) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (bottomDestinations.any { it.route == currentRoute }) {
                        NavigationBar(modifier = Modifier.tourAnchor(TourAnchors.BOTTOM_NAV)) {
                            bottomDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        if (currentRoute == destination.route) return@NavigationBarItem
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.SETTINGS,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(onShowHelp = tour::start)
                    }
                    composable(AppRoutes.WIFI) {
                        WifiScreen(
                            onOpen = { navController.navigate(AppRoutes.wifiDetail(it)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("wifi")) },
                        )
                    }
                    composable(AppRoutes.BLUETOOTH) {
                        BluetoothScreen(
                            onOpen = { navController.navigate(AppRoutes.bluetoothDetail(it)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("bluetooth")) },
                        )
                    }
                    composable(AppRoutes.NFC) {
                        NfcScreen(onOpen = { navController.navigate(AppRoutes.nfcDetail(it)) })
                    }
                    composable(AppRoutes.RADIO) {
                        RadioScreen(
                            onOpen = { navController.navigate(AppRoutes.radioDetail(it)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("radio")) },
                        )
                    }
                    detail(AppRoutes.WIFI_DETAIL) { key ->
                        WifiDetailScreen(
                            key = key,
                            onBack = { navController.popBackStack() },
                            onFind = { navController.navigate(AppRoutes.find("wifi", key)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("wifi")) },
                        )
                    }
                    detail(AppRoutes.BLUETOOTH_DETAIL) { key ->
                        BluetoothDetailScreen(
                            key = key,
                            onBack = { navController.popBackStack() },
                            onFind = { navController.navigate(AppRoutes.find("bluetooth", key)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("bluetooth")) },
                        )
                    }
                    detail(AppRoutes.NFC_DETAIL) { key ->
                        NfcDetailScreen(key = key, onBack = { navController.popBackStack() })
                    }
                    detail(AppRoutes.RADIO_DETAIL) { key ->
                        RadioDetailScreen(
                            key = key,
                            onBack = { navController.popBackStack() },
                            onFind = { navController.navigate(AppRoutes.find("radio", key)) },
                            onMap = { navController.navigate(AppRoutes.plotMap("radio")) },
                        )
                    }
                    composable(
                        route = AppRoutes.PLOT_MAP,
                        arguments = listOf(navArgument("kind") { type = NavType.StringType }),
                    ) { entry ->
                        PlotMapScreen(
                            kind = entry.arguments?.getString("kind").orEmpty(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = AppRoutes.FIND,
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("key") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        FindScreen(
                            kind = entry.arguments?.getString("kind").orEmpty(),
                            key = Uri.decode(entry.arguments?.getString("key").orEmpty()),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
            FeatureTourOverlay(tour)
        }
    }
}

private fun NavBackStackEntry?.shows(route: String): Boolean {
    if (this == null) return false
    val pattern = destination.route
    if (pattern == route) return true
    val key = arguments?.getString("key") ?: return false
    return route == when (pattern) {
        AppRoutes.WIFI_DETAIL -> AppRoutes.wifiDetail(key)
        AppRoutes.BLUETOOTH_DETAIL -> AppRoutes.bluetoothDetail(key)
        AppRoutes.NFC_DETAIL -> AppRoutes.nfcDetail(key)
        AppRoutes.RADIO_DETAIL -> AppRoutes.radioDetail(key)
        else -> null
    }
}

/** Opens a device that has Find device, so the tour can point at that button. */
private fun findDeviceTourRoute(): String? {
    val wifi = AppGraph.wifi.link.value ?: AppGraph.wifi.sightings.value.firstOrNull()
    if (wifi != null) return AppRoutes.wifiDetail(wifi.key)
    val bluetooth = AppGraph.bluetooth.sightings.value.firstOrNull { it.key != BluetoothFields.ADAPTER_KEY }
    if (bluetooth != null) return AppRoutes.bluetoothDetail(bluetooth.key)
    val cell = AppGraph.cells.cells.value.firstOrNull { it.key != CellFields.PHONE_KEY }
    if (cell != null) return AppRoutes.radioDetail(cell.key)
    return null
}

private fun androidx.navigation.NavGraphBuilder.detail(
    route: String,
    content: @Composable (String) -> Unit,
) {
    composable(
        route = route,
        arguments = listOf(navArgument("key") { type = NavType.StringType; defaultValue = "" }),
    ) { entry ->
        val key = Uri.decode(entry.arguments?.getString("key").orEmpty())
        content(key)
    }
}
