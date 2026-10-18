package com.millerchnz.ghzexplorer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.reported
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lists GATT services and classic SDP UUIDs for a device the user picked.
 *
 * The connection is read-only on purpose: this never calls readCharacteristic or
 * writeCharacteristic, and it never confirms a pairing. A lock's service list shows
 * what it exposes. It does not show whether the lock's keys are stored safely.
 */
class ServiceLookup(context: Context) {
    data class Look(
        val status: String,
        val running: Boolean,
        val fields: List<ApiField>,
    )

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _looks = MutableStateFlow<Map<String, Look>>(emptyMap())
    val looks: StateFlow<Map<String, Look>> = _looks.asStateFlow()

    private var activeAddress: String? = null
    private var activeGatt: BluetoothGatt? = null
    private var sdpRegistered = false
    private var finished = false

    private val timeout = Runnable {
        val address = activeAddress ?: return@Runnable
        val look = _looks.value[address] ?: return@Runnable
        if (!look.running) return@Runnable
        if (look.fields.any { it.section == "SDP" && it.raw != null }) {
            finish(address, "SDP finished. No values were read.", look.fields)
        } else {
            finish(address, "Timed out before the device finished service discovery.", look.fields)
        }
        closeGatt()
    }

    private val sdpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_UUID) return
            val device = intent.bluetoothDevice() ?: return
            val address = device.address ?: return
            if (address != activeAddress) return
            val uuids = readUuids(intent)
            val fields = uuidFields(uuids)
            main.post {
                val current = _looks.value[address]
                val gattStillRunning = activeGatt != null && current?.running == true
                val hasGatt = current?.fields.orEmpty().any { it.section.startsWith("GATT") }
                update(
                    address,
                    Look(
                        status = when {
                            gattStillRunning || hasGatt -> current?.status ?: "SDP results arrived."
                            else -> "SDP finished. No values were read."
                        },
                        running = gattStillRunning,
                        fields = (current?.fields.orEmpty().filterNot { it.section == "SDP" }) + fields,
                    ),
                )
                if (!gattStillRunning) main.removeCallbacks(timeout)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(device: BluetoothDevice) {
        val address = device.address ?: return
        cancel()
        finished = false
        activeAddress = address
        update(address, Look("Connecting…", running = true, fields = emptyList()))
        registerSdp()
        runCatching { device.fetchUuidsWithSdp() }
        val transport = if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            BluetoothDevice.TRANSPORT_BREDR
        } else {
            BluetoothDevice.TRANSPORT_LE
        }
        activeGatt = if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            null
        } else {
            device.connectGatt(app, false, callback, transport)
        }
        if (activeGatt == null && device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            update(address, Look("Asking classic SDP for service UUIDs…", running = true, fields = emptyList()))
        }
        main.postDelayed(timeout, TIMEOUT_MS)
    }

    fun cancel() {
        main.removeCallbacks(timeout)
        val address = activeAddress
        closeGatt()
        if (address != null && _looks.value[address]?.running == true) {
            update(address, Look("Cancelled.", running = false, fields = _looks.value[address]?.fields.orEmpty()))
        }
        activeAddress = null
        unregisterSdp()
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                val address = gatt.device?.address ?: return@post
                if (address != activeAddress) {
                    gatt.close()
                    return@post
                }
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        update(address, Look("Connected. Discovering services…", running = true, fields = emptyList()))
                        gatt.discoverServices()
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        if (!finished) {
                            finish(address, "Disconnected (${BluetoothFields.gattStatus(status)}).", _looks.value[address]?.fields.orEmpty())
                        }
                        gatt.close()
                        if (activeGatt === gatt) activeGatt = null
                    }
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        finish(address, "Connection failed (${BluetoothFields.gattStatus(status)}).", emptyList())
                        gatt.close()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            main.post {
                val address = gatt.device?.address ?: return@post
                val fields = if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.services.orEmpty().flatMap { serviceFields(it) }
                } else {
                    emptyList()
                }
                val sdp = _looks.value[address]?.fields.orEmpty().filter { it.section == "SDP" }
                finished = true
                finish(
                    address,
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        "Discovered ${gatt.services.orEmpty().size} services. No values were read."
                    } else {
                        "Service discovery failed (${BluetoothFields.gattStatus(status)})."
                    },
                    fields + sdp,
                )
                gatt.disconnect()
            }
        }
    }

    private fun serviceFields(service: BluetoothGattService): List<ApiField> = buildList {
        val name = BluetoothFields.uuidLabel(service.uuid.toString())
        val type = if (service.type == BluetoothGattService.SERVICE_TYPE_SECONDARY) "secondary" else "primary"
        reported("GATT $name", "UUID", service.uuid.toString())
        reported("GATT $name", "Type", type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reported("GATT $name", "Instance", service.instanceId.toString())
        }
        service.includedServices.orEmpty().forEach { included ->
            reported("GATT $name", "Includes", BluetoothFields.uuidLabel(included.uuid.toString()))
        }
        service.characteristics.orEmpty().forEach { characteristic ->
            val label = BluetoothFields.uuidLabel(characteristic.uuid.toString())
            reported("GATT $name", label, characteristic.uuid.toString())
            reported("GATT $name", "$label properties", BluetoothFields.properties(characteristic.properties))
            reported("GATT $name", "$label permissions", BluetoothFields.permissions(characteristic.permissions))
            characteristic.descriptors.orEmpty().forEach { descriptor ->
                val descriptorName = BluetoothFields.uuidLabel(descriptor.uuid.toString())
                reported("GATT $name", "$label descriptor", "$descriptorName ${descriptor.uuid}")
            }
        }
    }

    private fun uuidFields(uuids: List<ParcelUuid>): List<ApiField> = buildList {
        if (uuids.isEmpty()) {
            reported("SDP", "UUIDs", null)
        } else {
            uuids.forEach { uuid ->
                reported("SDP", BluetoothFields.uuidLabel(uuid.uuid.toString()), uuid.uuid.toString())
            }
        }
    }

    private fun finish(address: String, status: String, fields: List<ApiField>) {
        finished = true
        main.removeCallbacks(timeout)
        update(address, Look(status, running = false, fields = fields))
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val gatt = activeGatt ?: return
        activeGatt = null
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun update(address: String, look: Look) {
        _looks.value = _looks.value + (address to look)
    }

    private fun registerSdp() {
        if (sdpRegistered) return
        ContextCompat.registerReceiver(
            app,
            sdpReceiver,
            IntentFilter(BluetoothDevice.ACTION_UUID),
            ContextCompat.RECEIVER_EXPORTED,
        )
        sdpRegistered = true
    }

    private fun unregisterSdp() {
        if (!sdpRegistered) return
        runCatching { app.unregisterReceiver(sdpReceiver) }
        sdpRegistered = false
    }

    private fun readUuids(intent: Intent): List<ParcelUuid> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
                ?.toList()
                .orEmpty()
        }
        @Suppress("DEPRECATION")
        val raw = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
        return raw?.mapNotNull { it as? ParcelUuid }.orEmpty()
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val TIMEOUT_MS = 15_000L
    }
}
