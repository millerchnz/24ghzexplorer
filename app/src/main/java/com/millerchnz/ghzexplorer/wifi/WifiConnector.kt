package com.millerchnz.ghzexplorer.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiJoinState(
    val ssid: String? = null,
    val message: String = "Join asks Android to connect. You confirm it on the system prompt.",
    val holding: Boolean = false,
)

/**
 * Asks Android to join one scanned network. The platform shows its own confirm dialog.
 * The password is handed to that request and is not stored.
 */
class WifiConnector(context: Context) {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WifiJoinState())
    val state: StateFlow<WifiJoinState> = _state.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(sighting: WifiSighting, passphrase: String) {
        val ssid = sighting.ssid
        if (ssid.isNullOrBlank()) {
            _state.value = WifiJoinState(message = "This access point has no SSID, so Android cannot join it from an app.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _state.value = WifiJoinState(ssid, "Connecting from an app needs Android 10 or newer.")
            return
        }
        release()
        val failure = WifiSpecifierJoin.start(
            connectivity = connectivity,
            handler = main,
            ssid = ssid,
            bssid = sighting.bssid,
            security = sighting.security,
            passphrase = passphrase,
            onCallback = { callback = it },
            onState = { _state.value = it },
            onAbandon = { abandon() },
        )
        if (failure != null) {
            release()
            _state.value = WifiJoinState(ssid, failure)
        }
    }

    fun disconnect() {
        val ssid = _state.value.ssid
        release()
        _state.value = WifiJoinState(
            ssid = ssid,
            message = if (ssid == null) "Not connected from this app." else "Released the connection to $ssid.",
        )
    }

    /** Drops the phone's current Wi-Fi association. Android can ignore this. */
    fun disconnectRadio(): String {
        disconnect()
        val accepted = runCatching { wifi?.disconnect() == true }.getOrDefault(false)
        return if (accepted) {
            "Asked Android to disconnect Wi-Fi."
        } else {
            "Android did not disconnect Wi-Fi."
        }
    }

    private fun release() {
        val current = callback ?: return
        callback = null
        runCatching { connectivity?.unregisterNetworkCallback(current) }
    }

    private fun abandon() {
        release()
        _state.value = _state.value.copy(holding = false)
    }
}

/**
 * WifiNetworkSpecifier exists from API 29. This object is only entered on those releases.
 */
private object WifiSpecifierJoin {
    fun start(
        connectivity: ConnectivityManager?,
        handler: Handler,
        ssid: String,
        bssid: String?,
        security: WifiSecurity,
        passphrase: String,
        onCallback: (ConnectivityManager.NetworkCallback) -> Unit,
        onState: (WifiJoinState) -> Unit,
        onAbandon: () -> Unit,
    ): String? {
        if (connectivity == null) return "No connectivity service."
        val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!bssid.isNullOrBlank() && bssid != "02:00:00:00:00:00") {
            runCatching { builder.setBssid(android.net.MacAddress.fromString(bssid)) }
        }
        when (security) {
            WifiSecurity.OPEN -> Unit
            WifiSecurity.OWE -> builder.setIsEnhancedOpen(true)
            WifiSecurity.WPA2 -> {
                if (!acceptable(passphrase)) return "WPA2 passwords are 8 to 63 characters."
                builder.setWpa2Passphrase(passphrase)
            }
            WifiSecurity.WPA3 -> {
                if (!acceptable(passphrase)) return "WPA3 passwords are 8 to 63 characters."
                builder.setWpa3Passphrase(passphrase)
            }
            WifiSecurity.ENTERPRISE -> return "This network wants an enterprise login. Use the system Wi-Fi screen for that."
            WifiSecurity.WEP -> return "Android's connect request does not support WEP."
        }
        val specifier = try {
            builder.build()
        } catch (error: IllegalArgumentException) {
            return error.message ?: "That network description was rejected."
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onState(WifiJoinState(ssid, "Connected to $ssid. This link is for this app until you disconnect.", holding = true))
            }

            override fun onUnavailable() {
                onAbandon()
                onState(
                    WifiJoinState(
                        ssid,
                        "Android did not connect to $ssid. The prompt may have been dismissed, or the password was refused.",
                    ),
                )
            }

            override fun onLost(network: Network) {
                onAbandon()
                onState(WifiJoinState(ssid, "The link to $ssid dropped."))
            }
        }
        onCallback(callback)
        onState(WifiJoinState(ssid, "Android is asking you to connect to $ssid.", holding = true))
        return try {
            connectivity.requestNetwork(request, callback, handler)
            null
        } catch (error: Exception) {
            error.message ?: "Android refused the connection request."
        }
    }

    private fun acceptable(passphrase: String): Boolean = passphrase.length in 8..63
}
