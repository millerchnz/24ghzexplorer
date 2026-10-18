package com.millerchnz.ghzexplorer.data

import android.app.Application
import com.millerchnz.ghzexplorer.bluetooth.BluetoothCatalog
import com.millerchnz.ghzexplorer.bluetooth.BluetoothLink
import com.millerchnz.ghzexplorer.bluetooth.PairingLog
import com.millerchnz.ghzexplorer.bluetooth.ServiceLookup
import com.millerchnz.ghzexplorer.cell.CellCatalog
import com.millerchnz.ghzexplorer.nfc.NfcCatalog
import com.millerchnz.ghzexplorer.find.SeenLog
import com.millerchnz.ghzexplorer.wifi.WifiCatalog
import com.millerchnz.ghzexplorer.wifi.WifiConnector

object AppGraph {
    lateinit var settings: SettingsStore
        private set
    lateinit var wifi: WifiCatalog
        private set
    lateinit var wifiJoin: WifiConnector
        private set
    lateinit var bluetooth: BluetoothCatalog
        private set
    lateinit var links: BluetoothLink
        private set
    lateinit var pairing: PairingLog
        private set
    lateinit var services: ServiceLookup
        private set
    lateinit var nfc: NfcCatalog
        private set
    lateinit var cells: CellCatalog
        private set
    lateinit var aliases: AliasStore
        private set
    val seen: SeenLog = SeenLog()

    fun init(app: Application) {
        if (::settings.isInitialized) return
        settings = SettingsStore(app)
        wifi = WifiCatalog(app)
        wifiJoin = WifiConnector(app)
        bluetooth = BluetoothCatalog(app)
        links = BluetoothLink(app)
        pairing = PairingLog(app)
        services = ServiceLookup(app)
        nfc = NfcCatalog()
        cells = CellCatalog(app)
        aliases = AliasStore(app)
        pairing.start()
    }

    fun onBackground() {
        if (!::wifi.isInitialized) return
        wifi.onBackground()
        bluetooth.onBackground()
    }

    fun onForeground() {
        if (!::wifi.isInitialized) return
        wifi.onForeground()
        bluetooth.onForeground()
    }
}
