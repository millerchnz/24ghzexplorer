package com.millerchnz.ghzexplorer.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions each tab needs. Install-time permissions are omitted. */
object AppPermissions {
    fun wifi(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun bluetooth(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    fun cells(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
    )

    fun notifications(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context, permissions: Array<String>): List<String> =
        permissions.filterNot { granted(context, it) }

    fun wifiGranted(context: Context): Boolean = missing(context, wifi()).isEmpty()

    fun bluetoothGranted(context: Context): Boolean = missing(context, bluetooth()).isEmpty()

    fun cellsGranted(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun locationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun label(permission: String): String = when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location"
        Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location"
        Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi devices"
        Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth scan"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth connect"
        Manifest.permission.READ_PHONE_STATE -> "Phone state"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast('.')
    }
}
