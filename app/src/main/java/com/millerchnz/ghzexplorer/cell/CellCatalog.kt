package com.millerchnz.ghzexplorer.cell

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import com.millerchnz.ghzexplorer.find.SignalReading
import com.millerchnz.ghzexplorer.find.signalDbm
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.ui.common.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cellular cells, plus a single row describing the radios in this phone. */
class CellCatalog(context: Context) {
    private val app = context.applicationContext
    private val telephony = app.getSystemService(TelephonyManager::class.java)
    private val wifi = app.getSystemService(WifiManager::class.java)

    private val _cells = MutableStateFlow<List<CellSighting>>(emptyList())
    val cells: StateFlow<List<CellSighting>> = _cells.asStateFlow()

    private val _phone = MutableStateFlow<CellSighting?>(null)
    val phone: StateFlow<CellSighting?> = _phone.asStateFlow()

    private val _status = MutableStateFlow("Refresh to ask the modem which cells it can see.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates.asStateFlow()

    private val discovered = linkedMapOf<String, CellSighting>()

    fun sighting(key: String): CellSighting? =
        if (key == CellFields.PHONE_KEY) _phone.value else _cells.value.firstOrNull { it.key == key }

    /** Latest cell signal. The stamp changes when the modem report is replaced. */
    fun signal(key: String): SignalReading? {
        if (key == CellFields.PHONE_KEY) return null
        val rssi = sighting(key)?.fields?.signalDbm() ?: return null
        return SignalReading(rssi, _updates.value.toLong())
    }

    fun refresh() {
        _phone.value = phoneSighting()
        if (!AppPermissions.cellsGranted(app)) {
            _status.value = "Precise location is required before Android will return cell information."
            discovered.clear()
            _cells.value = emptyList()
            bump()
            return
        }
        if (telephony == null) {
            _status.value = "This phone has no cellular radio. Wi-Fi, Bluetooth and NFC are unaffected."
            bump()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            _status.value = "Asking the modem for a fresh cell list…"
            val requested = runCatching {
                telephony.requestCellInfoUpdate(
                    app.mainExecutor,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) = publish(cellInfo)
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            _status.value = "The modem refused a fresh scan (error $errorCode). Showing the cached list."
                            publish(cached())
                        }
                    },
                )
            }
            if (requested.isFailure) {
                _status.value = requested.exceptionOrNull()?.message ?: "Cell scan was blocked."
                publish(cached())
            }
        } else {
            publish(cached())
        }
    }

    @SuppressLint("MissingPermission")
    private fun cached(): List<CellInfo> = runCatching { telephony?.allCellInfo }.getOrNull().orEmpty()

    @SuppressLint("MissingPermission")
    private fun publish(cells: List<CellInfo>) {
        val latest = cells.map { CellFields.of(it) }
            .distinctBy { it.key }
            .sortedWith(compareByDescending<CellSighting> { it.fields.any { field -> field.label == "Registered" && field.raw == "yes" } })
        val latestKeys = latest.mapTo(mutableSetOf()) { it.key }
        // Keep a cell where it already is. New cells are appended, registered ones among them first.
        discovered.keys.retainAll(latestKeys)
        for (cell in latest) {
            discovered[cell.key] = cell
        }
        _cells.value = discovered.values.toList()
        _status.value = if (cells.isEmpty()) {
            "The modem returned no cells. Some phones hide neighbours from ordinary apps."
        } else {
            "${cells.size} cells. A neighbour the radio can hear may still be withheld by the phone."
        }
        bump()
    }

    private fun bump() {
        _updates.value = _updates.value + 1
    }

    private fun phoneSighting(): CellSighting {
        val nfc = NfcAdapter.getDefaultAdapter(app)
        val bluetooth = app.getSystemService(BluetoothManager::class.java)?.adapter
        val fields = buildList {
            reportedBool("Wi-Fi", "Enabled", wifi?.isWifiEnabled == true)
            reported("Wi-Fi", "State", wifiState(wifi?.wifiState))
            reported("Bluetooth", "State", bluetooth?.let { adapterState(it.state) } ?: "not present")
            reported("Bluetooth", "Name", runCatching { bluetooth?.name }.getOrNull())
            reported("NFC", "Present", if (nfc == null) "no" else "yes")
            reportedBool("NFC", "Enabled", nfc?.isEnabled == true)
            if (telephony == null) {
                reported("Cellular", "Radio", null)
            } else {
                reported("Cellular", "Operator", runCatching { telephony.networkOperatorName }.getOrNull())
                reported("Cellular", "Phone type", phoneType(telephony.phoneType))
                val dataType = runCatching { telephony.dataNetworkType }.getOrNull()
                reported("Cellular", "Data network", dataType?.let { CellFields.networkType(it) })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val strength = runCatching { telephony.signalStrength }.getOrNull()
                    reported("Cellular", "Signal level", strength?.level?.toString())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        strength?.cellSignalStrengths?.forEachIndexed { index, cell ->
                            reported("Cellular", "Signal ${index + 1}", "${cell.dbm} dBm")
                        }
                    }
                    reported("Cellular", "SignalStrength", strength?.toString())
                }
            }
        }
        return CellSighting(
            key = CellFields.PHONE_KEY,
            title = "Radios in this phone",
            subtitle = "Wi-Fi, Bluetooth, NFC and cellular state",
            trailing = "",
            fields = fields,
        )
    }

    private fun wifiState(state: Int?): String = when (state) {
        WifiManager.WIFI_STATE_DISABLED -> "disabled"
        WifiManager.WIFI_STATE_DISABLING -> "disabling"
        WifiManager.WIFI_STATE_ENABLED -> "enabled"
        WifiManager.WIFI_STATE_ENABLING -> "enabling"
        WifiManager.WIFI_STATE_UNKNOWN -> "unknown"
        else -> "not present"
    }

    private fun adapterState(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "off"
        BluetoothAdapter.STATE_TURNING_ON -> "turning on"
        BluetoothAdapter.STATE_ON -> "on"
        BluetoothAdapter.STATE_TURNING_OFF -> "turning off"
        else -> state.toString()
    }

    private fun phoneType(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "none"
        else -> type.toString()
    }
}
