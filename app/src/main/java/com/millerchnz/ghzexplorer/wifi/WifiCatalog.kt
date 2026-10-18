package com.millerchnz.ghzexplorer.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.millerchnz.ghzexplorer.find.SignalReading
import com.millerchnz.ghzexplorer.find.signalDbm
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Wi-Fi scan. Results stay in memory for the process. Scanning stops when
 * the activity leaves the foreground and resumes if the user had left it on.
 */
class WifiCatalog(context: Context) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _sightings = MutableStateFlow<List<WifiSighting>>(emptyList())
    val sightings: StateFlow<List<WifiSighting>> = _sightings.asStateFlow()

    private val _link = MutableStateFlow<WifiSighting?>(null)
    val link: StateFlow<WifiSighting?> = _link.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _status = MutableStateFlow("Scan to list nearby access points.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val discovered = linkedMapOf<String, WifiSighting>()
    private var receiverRegistered = false
    private var lastPokeElapsed = 0L
    private var resumeWhenForeground = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                publish()
            }
        }
    }

    private val rescan = object : Runnable {
        override fun run() {
            if (!_scanning.value) return
            requestScan()
            main.postDelayed(this, RESCAN_MS)
        }
    }

    fun sighting(key: String): WifiSighting? =
        if (key == WifiFields.LINK_KEY) _link.value else _sightings.value.firstOrNull { it.key == key }

    /** Re-reads the results Android already holds. A new scan stays on the list's own timer. */
    @SuppressLint("MissingPermission")
    fun poll() {
        publish()
    }

    /**
     * Used while finding a device. Republishes what Android already holds, and asks for a
     * scan at most every 15 seconds without changing the list's scan button.
     */
    @SuppressLint("MissingPermission")
    fun pokeScan() {
        if (AppPermissions.wifiGranted(app) && wifi.isWifiEnabled) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastPokeElapsed > 15_000L) {
                lastPokeElapsed = now
                runCatching { wifi.startScan() }
            }
        }
        publish()
    }

    /** Latest signal for one access point. The stamp changes only when the radio reports again. */
    @SuppressLint("MissingPermission")
    fun signal(key: String): SignalReading? {
        if (key == WifiFields.LINK_KEY) {
            val rssi = _link.value?.fields?.signalDbm() ?: return null
            return SignalReading(rssi, SystemClock.elapsedRealtime())
        }
        val result = runCatching { wifi.scanResults }.getOrNull().orEmpty().firstOrNull {
            "${it.BSSID}|${it.frequency}" == key
        } ?: return null
        return SignalReading(result.level, result.timestamp)
    }

    /** Elapsed-realtime millis of the scan that reported this access point, or 0 when it is gone. */
    @SuppressLint("MissingPermission")
    fun lastHeardElapsed(key: String): Long {
        if (key == WifiFields.LINK_KEY) {
            return if (_link.value != null) SystemClock.elapsedRealtime() else 0L
        }
        val micros = runCatching { wifi.scanResults }.getOrNull().orEmpty().firstOrNull {
            "${it.BSSID}|${it.frequency}" == key
        }?.timestamp ?: return 0L
        if (micros <= 0L) return 0L
        return micros / 1_000
    }

    fun heardWithin(key: String, withinMs: Long): Boolean {
        if (key == WifiFields.LINK_KEY) return _link.value != null
        val heard = lastHeardElapsed(key)
        return heard > 0L && SystemClock.elapsedRealtime() - heard <= withinMs
    }

    fun toggle() {
        if (_scanning.value) stop() else start()
    }

    fun start() {
        if (!AppPermissions.wifiGranted(app)) {
            _status.value = "Location and nearby Wi-Fi permission are required before Android will return scan results."
            return
        }
        if (!wifi.isWifiEnabled) {
            _status.value = "Wi-Fi is off. Turn it on, then scan again."
            publish()
            return
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        _scanning.value = true
        resumeWhenForeground = true
        publish()
        requestScan()
        main.removeCallbacks(rescan)
        main.postDelayed(rescan, RESCAN_MS)
    }

    fun stop() {
        resumeWhenForeground = false
        pause()
        _status.value = "Scan stopped. The list is the last set of results Android returned."
    }

    fun onBackground() {
        if (_scanning.value) pause()
    }

    fun onForeground() {
        if (resumeWhenForeground) start()
    }

    private fun pause() {
        _scanning.value = false
        main.removeCallbacks(rescan)
        if (receiverRegistered) {
            runCatching { app.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestScan() {
        val accepted = runCatching { wifi.startScan() }.getOrDefault(false)
        _status.value = if (accepted) {
            "Scan requested. Android throttles foreground scans to a few every two minutes."
        } else {
            "Android did not start a new scan (throttled or Wi-Fi is off). Showing the results it still holds."
        }
        publish()
    }

    @SuppressLint("MissingPermission")
    private fun publish() {
        val latest = runCatching { wifi.scanResults }.getOrNull().orEmpty().map { WifiFields.fromScan(it) }
        val latestKeys = latest.mapTo(mutableSetOf()) { it.key }
        // Drop access points that have gone, and append new ones. An update keeps its place.
        discovered.keys.retainAll(latestKeys)
        for (sighting in latest) {
            discovered[sighting.key] = sighting
        }
        _sightings.value = discovered.values.toList()
        _link.value = currentLink()
    }

    @SuppressLint("MissingPermission")
    private fun currentLink(): WifiSighting? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivity.allNetworks.firstNotNullOfOrNull { network ->
                val caps = connectivity.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@firstNotNullOfOrNull null
                caps.transportInfo as? WifiInfo
            }
        } else {
            @Suppress("DEPRECATION")
            wifi.connectionInfo?.takeIf { it.networkId != -1 }
        }
        return info?.let { WifiFields.fromLink(it) }
    }

    companion object {
        private const val RESCAN_MS = 30_000L
    }
}
