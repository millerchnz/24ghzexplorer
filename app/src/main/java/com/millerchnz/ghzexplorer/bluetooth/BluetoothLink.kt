package com.millerchnz.ghzexplorer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothLinkState(
    val address: String,
    val message: String,
    val connected: Boolean,
)

/**
 * Opens a Bluetooth link, or starts pairing.
 *
 * Pairing is [BluetoothDevice.createBond]. The system dialog still asks you to confirm.
 * This class never calls setPin or setPairingConfirmation.
 * Connect opens a GATT link and does not read or write characteristics.
 */
class BluetoothLink(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<BluetoothLinkState?>(null)
    val state: StateFlow<BluetoothLinkState?> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var connected = false

    private val timeout = Runnable {
        if (connected) return@Runnable
        val address = _state.value?.address ?: return@Runnable
        _state.value = BluetoothLinkState(address, "The device did not accept the connection.", connected = false)
        closeGatt()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (gatt !== this@BluetoothLink.gatt) {
                    gatt.close()
                    return@post
                }
                val address = gatt.device?.address ?: return@post
                if (address != _state.value?.address) {
                    gatt.close()
                    return@post
                }
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        connected = true
                        main.removeCallbacks(timeout)
                        _state.value = BluetoothLinkState(address, "Connected. No data was read.", connected = true)
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        connected = false
                        main.removeCallbacks(timeout)
                        _state.value = BluetoothLinkState(
                            address,
                            "Disconnected (${BluetoothFields.gattStatus(status)}).",
                            connected = false,
                        )
                        gatt.close()
                        if (this@BluetoothLink.gatt === gatt) this@BluetoothLink.gatt = null
                    }
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        connected = false
                        main.removeCallbacks(timeout)
                        _state.value = BluetoothLinkState(
                            address,
                            "Connection failed (${BluetoothFields.gattStatus(status)}).",
                            connected = false,
                        )
                        gatt.close()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val address = device.address ?: return
        disconnect()
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            _state.value = BluetoothLinkState(
                address,
                "This is a classic device. Pair it, and Android connects its profiles from the system.",
                connected = false,
            )
            return
        }
        _state.value = BluetoothLinkState(address, "Connecting…", connected = false)
        connected = false
        val transport = BluetoothDevice.TRANSPORT_LE
        gatt = runCatching { device.connectGatt(app, false, callback, transport) }.getOrElse { error ->
            _state.value = BluetoothLinkState(address, error.message ?: "Bluetooth connect was blocked.", connected = false)
            null
        }
        if (gatt != null) main.postDelayed(timeout, TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        main.removeCallbacks(timeout)
        connected = false
        val address = _state.value?.address
        closeGatt()
        if (address != null) {
            _state.value = BluetoothLinkState(address, "Disconnected.", connected = false)
        }
    }

    /**
     * Starts pairing. Returns a message for the screen. The system dialog collects the confirmation.
     */
    @SuppressLint("MissingPermission")
    fun pair(device: BluetoothDevice): String {
        val address = device.address ?: return "This device has no address."
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> return "Already paired."
            BluetoothDevice.BOND_BONDING -> return "Pairing is already in progress. Confirm the system dialog."
        }
        runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter?.cancelDiscovery() }
        val started = runCatching { device.createBond() }.getOrDefault(false)
        val message = if (started) {
            val label = BluetoothFields.safeName(device) ?: address
            "Pairing started with $label. Confirm it on the system dialog."
        } else {
            "Android did not start pairing."
        }
        _state.value = BluetoothLinkState(address, message, connected = _state.value?.connected == true && _state.value?.address == address)
        return message
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val current = gatt ?: return
        gatt = null
        runCatching { current.disconnect() }
        runCatching { current.close() }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
