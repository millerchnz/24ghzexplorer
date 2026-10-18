package com.millerchnz.ghzexplorer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.millerchnz.ghzexplorer.find.SignalReading
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.data.reportedInt
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Classic inquiry plus a BLE scan, merged by address. Bonded devices stay in the list
 * even when they are quiet, which is how a lock that advertises rarely still shows up.
 */
class BluetoothCatalog(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val devices = linkedMapOf<String, Accum>()

    private val _sightings = MutableStateFlow<List<BtSighting>>(emptyList())
    val sightings: StateFlow<List<BtSighting>> = _sightings.asStateFlow()

    private val _adapter = MutableStateFlow<BtSighting?>(null)
    val adapter: StateFlow<BtSighting?> = _adapter.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _status = MutableStateFlow("Scan to list Bluetooth devices, including ones already bonded.")
    val status: StateFlow<String> = _status.asStateFlow()

    private var discoveryPaused = false
    private var leStarted = false
    private var classicRegistered = false
    private var resumeWhenForeground = false
    private var retriedLegacy = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { ingestLe(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            main.post { results.forEach { ingestLe(it) } }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                if (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED && !retriedLegacy) {
                    retriedLegacy = true
                    stopLe()
                    startLe(legacyOnly = true)
                } else {
                    _status.value = "BLE scan failed (error $errorCode)."
                }
            }
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> ingestClassic(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_scanning.value && !discoveryPaused) main.postDelayed({ startClassic() }, 2_000L)
                }
            }
        }
    }

    fun sighting(key: String): BtSighting? =
        if (key == BluetoothFields.ADAPTER_KEY) _adapter.value else _sightings.value.firstOrNull { it.key == key }

    fun device(address: String): BluetoothDevice? =
        manager.adapter?.getRemoteDevice(address)

    /** Classic inquiry steals the radio from an in-progress bond, so pairing pauses it briefly. */
    @SuppressLint("MissingPermission")
    fun pauseDiscoveryForPairing() {
        discoveryPaused = true
        runCatching { manager.adapter?.cancelDiscovery() }
        main.removeCallbacks(resumeDiscovery)
        main.postDelayed(resumeDiscovery, PAIRING_PAUSE_MS)
    }

    private val resumeDiscovery = Runnable {
        discoveryPaused = false
        if (_scanning.value) startClassic()
    }

    fun toggle() {
        if (_scanning.value) stop() else start(clear = true)
    }

    @SuppressLint("MissingPermission")
    fun start(clear: Boolean = false) {
        val adapter = manager.adapter
        if (adapter == null) {
            _status.value = "This phone has no Bluetooth radio."
            return
        }
        if (!AppPermissions.bluetoothGranted(app)) {
            _status.value = "Bluetooth permission is required before Android will return devices."
            return
        }
        if (!adapter.isEnabled) {
            _status.value = "Bluetooth is off. Turn it on, then scan again."
            publishAdapter(adapter)
            return
        }
        _scanning.value = true
        resumeWhenForeground = true
        retriedLegacy = false
        if (clear) {
            devices.clear()
            _sightings.value = emptyList()
        }
        seedBonded(adapter)
        startLe(legacyOnly = false)
        startClassic()
        publishAdapter(adapter)
        _status.value = "Scanning. BLE stays on; classic inquiry restarts when each 12-second sweep finishes."
    }

    fun stop() {
        resumeWhenForeground = false
        pause()
        _status.value = "Scan stopped."
    }

    fun onBackground() {
        if (_scanning.value) pause()
    }

    fun onForeground() {
        if (resumeWhenForeground) start()
    }

    @SuppressLint("MissingPermission")
    private fun pause() {
        _scanning.value = false
        discoveryPaused = false
        main.removeCallbacksAndMessages(null)
        stopLe()
        val adapter = manager.adapter
        if (classicRegistered) {
            runCatching { adapter?.cancelDiscovery() }
            runCatching { app.unregisterReceiver(classicReceiver) }
            classicRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLe(legacyOnly: Boolean) {
        val adapter = manager.adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: run {
            _status.value = "The BLE scanner is not available. Classic inquiry will still run."
            return
        }
        val builder = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        if (!legacyOnly && adapter.isLeExtendedAdvertisingSupported) {
            builder.setLegacy(false)
        }
        runCatching {
            scanner.startScan(null, builder.build(), scanCallback)
            leStarted = true
        }.onFailure { error ->
            _status.value = error.message ?: "BLE scan did not start."
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopLe() {
        if (!leStarted) return
        runCatching { manager.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        leStarted = false
    }

    @SuppressLint("MissingPermission")
    private fun startClassic() {
        val adapter = manager.adapter ?: return
        if (!_scanning.value || !adapter.isEnabled || discoveryPaused) return
        if (!classicRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(app, classicReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            classicRegistered = true
        }
        if (!adapter.isDiscovering) {
            runCatching { adapter.startDiscovery() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun seedBonded(adapter: BluetoothAdapter) {
        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        bonded.forEach { device -> upsert(device, rssi = null, deviceClass = device.bluetoothClass, advertisement = null, fromClassic = false, fromBonded = true) }
        publish()
    }

    @SuppressLint("MissingPermission")
    private fun ingestLe(result: ScanResult) {
        val device = result.device ?: return
        upsert(
            device = device,
            rssi = result.rssi,
            deviceClass = runCatching { device.bluetoothClass }.getOrNull(),
            advertisement = BluetoothFields.advertisement(result),
            fromClassic = false,
            fromBonded = false,
        )
        publish()
    }

    @SuppressLint("MissingPermission")
    private fun ingestClassic(intent: Intent) {
        val device = intent.bluetoothDevice() ?: return
        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
        val klass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
        }
        upsert(
            device = device,
            rssi = rssi.takeUnless { it == Short.MIN_VALUE.toInt() },
            deviceClass = klass,
            advertisement = null,
            fromClassic = true,
            fromBonded = false,
        )
        publish()
    }

    @SuppressLint("MissingPermission")
    private fun upsert(
        device: BluetoothDevice,
        rssi: Int?,
        deviceClass: BluetoothClass?,
        advertisement: LeAdvertisement?,
        fromClassic: Boolean,
        fromBonded: Boolean,
    ) {
        val address = device.address ?: return
        val current = devices[address] ?: Accum(address)
        val heardName = BluetoothFields.safeName(device)
        if (!heardName.isNullOrBlank()) current.name = heardName
        current.alias = BluetoothFields.safeAlias(device) ?: current.alias
        if (device.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) current.type = device.type
        current.bondState = device.bondState
        if (rssi != null) current.rssi = rssi
        if (deviceClass != null) current.deviceClass = deviceClass
        current.cachedUuids = runCatching { device.uuids?.toList() }.getOrNull() ?: current.cachedUuids
        if (Build.VERSION.SDK_INT >= 35) {
            current.addressType = runCatching { device.addressType }.getOrNull() ?: current.addressType
        }
        if (advertisement != null) current.advertisement = advertisement
        if (fromClassic) current.fromClassic = true
        if (fromBonded) current.fromBonded = true
        if (advertisement != null || fromClassic) current.lastHeardElapsed = SystemClock.elapsedRealtime()
        devices[address] = current
    }

    /**
     * The latest reading when this device was heard inside [withinMs], otherwise null.
     * The phone's own radio does not disappear, so that row is always returned.
     */
    @SuppressLint("MissingPermission")
    fun readingIfPresent(key: String, withinMs: Long): BtSighting? {
        if (key == BluetoothFields.ADAPTER_KEY) {
            manager.adapter?.let { publishAdapter(it) }
            return _adapter.value
        }
        val heard = devices[key]?.lastHeardElapsed ?: return null
        if (heard == 0L || SystemClock.elapsedRealtime() - heard > withinMs) return null
        return sighting(key)
    }

    /** When an advertisement or inquiry was last heard. The phone's own radio is always current. */
    fun lastHeardElapsed(key: String): Long {
        if (key == BluetoothFields.ADAPTER_KEY) return SystemClock.elapsedRealtime()
        return devices[key]?.lastHeardElapsed ?: 0L
    }

    /** Latest advertisement or inquiry RSSI. The stamp is when that packet was heard. */
    fun signal(key: String): SignalReading? {
        if (key == BluetoothFields.ADAPTER_KEY) return null
        val device = devices[key] ?: return null
        val rssi = device.rssi ?: return null
        if (device.lastHeardElapsed == 0L) return null
        return SignalReading(rssi, device.lastHeardElapsed)
    }

    private fun publish() {
        _sightings.value = devices.values.map { it.toSighting() }
        manager.adapter?.let { publishAdapter(it) }
    }

    @SuppressLint("MissingPermission")
    private fun publishAdapter(adapter: BluetoothAdapter) {
        val address = runCatching { adapter.address }.getOrNull()
        val fields = buildList {
            reported("This phone", "Name", runCatching { adapter.name }.getOrNull())
            reported(
                "This phone",
                "Adapter address",
                address?.takeUnless { it == "02:00:00:00:00:00" },
            )
            if (address == "02:00:00:00:00:00") {
                reported("This phone", "Address note", "Android hides this radio's MAC from ordinary apps")
            }
            reported("This phone", "State", adapterState(adapter.state))
            reported("This phone", "Scan mode", scanMode(adapter.scanMode))
            reportedBool("This phone", "Enabled", adapter.isEnabled)
            reportedBool("LE features", "2M PHY", adapter.isLe2MPhySupported)
            reportedBool("LE features", "Coded PHY", adapter.isLeCodedPhySupported)
            reportedBool("LE features", "Extended advertising", adapter.isLeExtendedAdvertisingSupported)
            reportedBool("LE features", "Periodic advertising", adapter.isLePeriodicAdvertisingSupported)
            reportedBool("LE features", "Multiple advertisement", adapter.isMultipleAdvertisementSupported)
            reportedBool("LE features", "Offloaded filtering", adapter.isOffloadedFilteringSupported)
            reportedBool("LE features", "Offloaded scan batching", adapter.isOffloadedScanBatchingSupported)
            reportedInt("LE features", "Max advertising data", adapter.leMaximumAdvertisingDataLength, "bytes", unavailable = 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                reportedInt("This phone", "Max connected audio devices", adapter.maxConnectedAudioDevices, unavailable = -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reported("LE features", "LE Audio", feature(adapter.isLeAudioSupported))
                reported("LE features", "LE Audio broadcast source", feature(adapter.isLeAudioBroadcastSourceSupported))
                reported("LE features", "LE Audio broadcast assistant", feature(adapter.isLeAudioBroadcastAssistantSupported))
                val timeout = runCatching { adapter.discoverableTimeout }.getOrNull()
                reported("This phone", "Discoverable timeout", timeout?.toSeconds()?.takeIf { it > 0 }?.let { "$it s" })
            }
        }
        _adapter.value = BtSighting(
            key = BluetoothFields.ADAPTER_KEY,
            title = runCatching { adapter.name }.getOrNull() ?: "This phone",
            subtitle = "Bluetooth radio in this phone",
            trailing = adapterState(adapter.state),
            address = null,
            fields = fields,
        )
    }

    private fun feature(value: Int): String = when (value) {
        android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED -> "supported"
        android.bluetooth.BluetoothStatusCodes.FEATURE_NOT_SUPPORTED -> "not supported"
        android.bluetooth.BluetoothStatusCodes.FEATURE_NOT_CONFIGURED -> "not configured"
        else -> value.toString()
    }

    private fun adapterState(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "off"
        BluetoothAdapter.STATE_TURNING_ON -> "turning on"
        BluetoothAdapter.STATE_ON -> "on"
        BluetoothAdapter.STATE_TURNING_OFF -> "turning off"
        else -> state.toString()
    }

    private fun scanMode(mode: Int): String = when (mode) {
        BluetoothAdapter.SCAN_MODE_NONE -> "not connectable"
        BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "connectable"
        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "connectable and discoverable"
        else -> mode.toString()
    }

    private class Accum(val address: String) {
        var name: String? = null
        var alias: String? = null
        var type: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN
        var bondState: Int = BluetoothDevice.BOND_NONE
        var rssi: Int? = null
        var deviceClass: BluetoothClass? = null
        var cachedUuids: List<ParcelUuid>? = null
        var addressType: Int? = null
        var advertisement: LeAdvertisement? = null
        var fromClassic: Boolean = false
        var fromBonded: Boolean = false
        var lastHeardElapsed: Long = 0

        fun toSighting(): BtSighting = BluetoothFields.sighting(
            address = address,
            name = name,
            alias = alias,
            type = type,
            bondState = bondState,
            deviceClass = deviceClass,
            rssi = rssi,
            cachedUuids = cachedUuids,
            addressType = addressType,
            advertisement = advertisement,
            fromClassic = fromClassic,
            fromBonded = fromBonded,
        )
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private companion object {
        const val PAIRING_PAUSE_MS = 30_000L
    }
}
