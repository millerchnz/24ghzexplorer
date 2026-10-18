package com.millerchnz.ghzexplorer.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import com.millerchnz.ghzexplorer.ui.common.PermissionBanner
import com.millerchnz.ghzexplorer.ui.common.PrivacyPolicyLink
import com.millerchnz.ghzexplorer.ui.common.SectionCard
import com.millerchnz.ghzexplorer.ui.tour.TourAnchors
import com.millerchnz.ghzexplorer.ui.tour.tourAnchor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onShowHelp: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val all = remember(tick) {
        (AppPermissions.wifi() + AppPermissions.bluetooth() + AppPermissions.cells() + AppPermissions.notifications())
            .distinct()
            .toTypedArray()
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "2.4 GHz Explorer reads the Wi-Fi, Bluetooth, NFC and cellular APIs and shows every " +
                    "value they return. Connect and pair use Android's own prompts. This app does not " +
                    "confirm a PIN for you, write to a tag, or send commands to a device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(Modifier.tourAnchor(TourAnchors.PERMISSIONS)) {
                SectionCard(
                    title = "Permissions",
                    subtitle = "Each radio API stays empty until its permission is granted. A dash on a " +
                        "detail screen means the platform did not report that value.",
                ) {
                    Text(
                        "Use the button on a tab when that radio asks, or grant them all here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PermissionBanner(
                    permissions = all,
                    message = "Grant these so each tab can show what the radio APIs report. You can deny " +
                        "phone state and still see signal strength; cell identity may then be blank.",
                )
            }
            SectionCard(title = "Permission status") {
                all.forEach { permission ->
                    val granted = AppPermissions.granted(context, permission)
                    Text(
                        "${AppPermissions.label(permission)}: ${if (granted) "granted" else "not granted"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nearby Wi-Fi and notification prompts appear on Android 13 and newer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "System location: ${if (AppPermissions.locationEnabled(context)) "on" else "off"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionCard(
                title = "What a reading cannot prove",
                subtitle = "An advertisement is how a device introduces itself. It is not a security audit.",
            ) {
                Text(
                    "A door lock that shows up over Bluetooth will list its address, name, bond, service " +
                        "UUIDs and manufacturer bytes. Discovering services connects, lists characteristics, " +
                        "and disconnects. It does not read or write those values, so it cannot unlock the " +
                        "door, and it cannot tell you whether the lock stores its keys safely.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Wi-Fi scans show access points, not other phones' traffic. BLE addresses usually " +
                        "rotate. This phone's own Bluetooth address is redacted. Android throttles Wi-Fi " +
                        "scans. NFC only works while a tag is against the phone and this tab is open. " +
                        "Pairing alerts are delivered while the app process is alive.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionCard(title = "Radios") {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text("Wi-Fi settings")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                    Text("Bluetooth settings")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text("NFC settings")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Location settings")
                }
            }
            Button(onClick = onShowHelp, modifier = Modifier.fillMaxWidth()) {
                Text("Show help")
            }
            PrivacyPolicyLink()
        }
    }
}
