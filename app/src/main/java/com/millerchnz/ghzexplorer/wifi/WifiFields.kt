package com.millerchnz.ghzexplorer.wifi

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Build
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.RadioText
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.data.reportedInt

data class WifiSighting(
    val key: String,
    val title: String,
    val subtitle: String,
    val trailing: String,
    val fields: List<ApiField>,
    val ssid: String?,
    val bssid: String?,
    val security: WifiSecurity,
)

enum class WifiSecurity {
    OPEN,
    OWE,
    WPA2,
    WPA3,
    ENTERPRISE,
    WEP,
    ;

    val needsPassphrase: Boolean
        get() = this == WPA2 || this == WPA3

    companion object {
        fun of(capabilities: String, types: IntArray?): WifiSecurity {
            val caps = capabilities.uppercase()
            val set = types?.toSet().orEmpty()
            val enterprise = WifiInfo.SECURITY_TYPE_EAP in set ||
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE in set ||
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT in set ||
                caps.contains("EAP")
            if (enterprise) return ENTERPRISE
            val sae = WifiInfo.SECURITY_TYPE_SAE in set || caps.contains("SAE")
            val psk = WifiInfo.SECURITY_TYPE_PSK in set || caps.contains("PSK")
            if (sae && !psk) return WPA3
            if (psk || caps.contains("WPA")) return WPA2
            if (WifiInfo.SECURITY_TYPE_OWE in set || caps.contains("OWE")) return OWE
            if (WifiInfo.SECURITY_TYPE_WEP in set || caps.contains("WEP")) return WEP
            return OPEN
        }
    }
}

object WifiFields {
    const val LINK_KEY = "link"

    fun fromScan(result: ScanResult): WifiSighting {
        val bareSsid = RadioText.quoteStripped(ssidOf(result))
        val ssid = bareSsid ?: "(hidden or withheld)"
        val channel = RadioText.wifiChannel(result.frequency)
        val securityTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { result.securityTypes }.getOrNull()
        } else {
            null
        }
        val fields = buildList {
            reported("Identity", "SSID", RadioText.quoteStripped(ssidOf(result)))
            reported("Identity", "BSSID", result.BSSID)
            reported("Identity", "Capabilities", result.capabilities)
            reportedInt("Signal", "RSSI", result.level, "dBm")
            reportedInt("Radio", "Frequency", result.frequency, "MHz")
            reported("Radio", "Band", RadioText.wifiBand(result.frequency))
            reported("Radio", "Channel", channel?.toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val platform = ScanResult.convertFrequencyMhzToChannelIfSupported(result.frequency)
                reported("Radio", "Channel (platform)", platform.takeIf { it >= 0 }?.toString())
            }
            reported("Radio", "Channel width", channelWidth(result.channelWidth))
            reportedInt("Radio", "Centre frequency 0", result.centerFreq0, "MHz", unavailable = 0)
            reportedInt("Radio", "Centre frequency 1", result.centerFreq1, "MHz", unavailable = 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                reported("Radio", "Wi-Fi standard", wifiStandard(result.wifiStandard))
            }
            reportedBool("Capabilities", "802.11mc responder", result.is80211mcResponder)
            reportedBool("Capabilities", "Passpoint", result.isPasspointNetwork)
            if (Build.VERSION.SDK_INT >= 36) {
                reportedBool("Capabilities", "802.11az NTB responder", result.is80211azNtbResponder)
                reportedBool("Capabilities", "TWT responder", result.isTwtResponder)
                reportedBool("Capabilities", "Ranging frame protection required", result.isRangingFrameProtectionRequired)
                reportedBool("Capabilities", "Secure HE-LTF", result.isSecureHeLtfSupported)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reported(
                    "Capabilities",
                    "Security types",
                    securityTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ") { securityType(it) },
                )
            }
            reported("Identity", "Venue", result.venueName?.toString())
            reported("Identity", "Operator", result.operatorFriendlyName?.toString())
            reported("Timing", "Age", age(result.timestamp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mlo("Multi-link", result.apMldMacAddress?.toString(), result.apMloLinkId, result.affiliatedMloLinks)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                informationElements(result.informationElements)
            }
            reported("Platform", "ScanResult", result.toString())
        }
        return WifiSighting(
            key = "${result.BSSID}|${result.frequency}",
            title = ssid,
            subtitle = "${result.BSSID} · ${RadioText.wifiBand(result.frequency)}",
            trailing = RadioText.rssi(result.level),
            fields = fields,
            ssid = bareSsid,
            bssid = result.BSSID,
            security = WifiSecurity.of(result.capabilities.orEmpty(), securityTypes),
        )
    }

    fun fromLink(info: WifiInfo): WifiSighting {
        val ssid = RadioText.quoteStripped(info.ssid) ?: "Connected network"
        val fields = buildList {
            reported("Link", "SSID", RadioText.quoteStripped(info.ssid))
            reported("Link", "BSSID", info.bssid.takeUnless { it.isNullOrBlank() || it == "02:00:00:00:00:00" })
            reportedInt("Link", "RSSI", info.rssi, "dBm")
            reportedInt("Link", "Frequency", info.frequency, "MHz")
            reported("Link", "Band", RadioText.wifiBand(info.frequency))
            reported("Link", "Channel", RadioText.wifiChannel(info.frequency)?.toString())
            reportedInt("Link", "Link speed", info.linkSpeed, "Mbps", unavailable = -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                reportedInt("Link", "TX link speed", info.txLinkSpeedMbps, "Mbps", unavailable = -1)
                reportedInt("Link", "RX link speed", info.rxLinkSpeedMbps, "Mbps", unavailable = -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                reported("Link", "Wi-Fi standard", wifiStandard(info.wifiStandard))
                reportedInt("Link", "Max TX", info.maxSupportedTxLinkSpeedMbps, "Mbps", unavailable = -1)
                reportedInt("Link", "Max RX", info.maxSupportedRxLinkSpeedMbps, "Mbps", unavailable = -1)
            }
            reported("Link", "Supplicant", info.supplicantState?.name)
            reportedBool("Link", "Hidden SSID", info.hiddenSSID)
            reportedInt("Link", "Network id", info.networkId, unavailable = -1)
            reported("Link", "IP address", ipv4(info.ipAddress))
            val mac = info.macAddress
            reported(
                "Link",
                "STA MAC",
                if (mac.isNullOrBlank() || mac == "02:00:00:00:00:00") null else mac,
            )
            if (mac == "02:00:00:00:00:00") {
                reported("Link", "STA MAC note", "Android returns 02:00:00:00:00:00 to ordinary apps")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                reported("Link", "Security", securityType(info.currentSecurityType))
                reportedInt("Link", "Subscription id", info.subscriptionId, unavailable = -1)
                reported("Link", "Passpoint FQDN", info.passpointFqdn)
                reported("Link", "Passpoint provider", info.passpointProviderFriendlyName)
                informationElements(info.informationElements)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mlo("Multi-link", info.apMldMacAddress?.toString(), info.apMloLinkId, info.affiliatedMloLinks)
            }
            reported("Platform", "WifiInfo", info.toString())
        }
        return WifiSighting(
            key = LINK_KEY,
            title = ssid,
            subtitle = "This phone's Wi-Fi link",
            trailing = RadioText.rssi(info.rssi),
            fields = fields,
            ssid = RadioText.quoteStripped(info.ssid),
            bssid = info.bssid,
            security = WifiSecurity.OPEN,
        )
    }

    private fun MutableList<ApiField>.mlo(
        section: String,
        mldMac: String?,
        linkId: Int,
        links: List<android.net.wifi.MloLink>?,
    ) {
        reported(section, "AP MLD MAC", mldMac)
        reportedInt(section, "AP MLO link id", linkId, unavailable = -1)
        links.orEmpty().forEachIndexed { index, link ->
            val prefix = "Link ${index + 1}"
            reported(section, "$prefix band", bandName(link.band))
            reportedInt(section, "$prefix channel", link.channel, unavailable = 0)
            reportedInt(section, "$prefix RSSI", link.rssi, "dBm")
            reported(section, "$prefix state", mloState(link.state))
            reported(section, "$prefix AP MAC", link.apMacAddress?.toString())
            reported(section, "$prefix STA MAC", link.staMacAddress?.toString())
            reportedInt(section, "$prefix TX", link.txLinkSpeedMbps, "Mbps", unavailable = -1)
            reportedInt(section, "$prefix RX", link.rxLinkSpeedMbps, "Mbps", unavailable = -1)
        }
    }

    private fun MutableList<ApiField>.informationElements(
        elements: List<ScanResult.InformationElement>?,
    ) {
        val list = elements.orEmpty()
        if (list.isEmpty()) {
            reported("Information elements", "Elements", null)
            return
        }
        list.forEachIndexed { index, element ->
            val buffer = element.bytes.duplicate()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val ext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) element.idExt else null
            val idLabel = if (ext != null && ext != 0) {
                "IE ${index + 1} id ${element.id} ext $ext"
            } else {
                "IE ${index + 1} id ${element.id}"
            }
            reported("Information elements", idLabel, RadioText.hex(bytes))
        }
    }

    private fun ssidOf(result: ScanResult): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val parsed = result.wifiSsid?.toString()
            if (!parsed.isNullOrBlank()) return parsed
        }
        @Suppress("DEPRECATION")
        return result.SSID
    }

    private fun age(timestampMicros: Long): String? {
        if (timestampMicros <= 0L) return null
        val ageMs = (android.os.SystemClock.elapsedRealtime() * 1000 - timestampMicros) / 1000
        if (ageMs < 0) return null
        return when {
            ageMs < 1_000 -> "${ageMs} ms"
            ageMs < 60_000 -> "${ageMs / 1000} s"
            else -> "${ageMs / 60_000} min"
        }
    }

    fun channelWidth(width: Int): String = when (width) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
        ScanResult.CHANNEL_WIDTH_320MHZ -> "320 MHz"
        else -> width.toString()
    }

    fun wifiStandard(standard: Int): String = when (standard) {
        ScanResult.WIFI_STANDARD_UNKNOWN -> null
        ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
        ScanResult.WIFI_STANDARD_11N -> "802.11n (Wi-Fi 4)"
        ScanResult.WIFI_STANDARD_11AC -> "802.11ac (Wi-Fi 5)"
        ScanResult.WIFI_STANDARD_11AX -> "802.11ax (Wi-Fi 6)"
        ScanResult.WIFI_STANDARD_11AD -> "802.11ad"
        ScanResult.WIFI_STANDARD_11BE -> "802.11be (Wi-Fi 7)"
        else -> standard.toString()
    } ?: "unknown"

    fun securityType(type: Int): String = when (type) {
        WifiInfo.SECURITY_TYPE_OPEN -> "Open"
        WifiInfo.SECURITY_TYPE_WEP -> "WEP"
        WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2-PSK"
        WifiInfo.SECURITY_TYPE_EAP -> "WPA/WPA2-Enterprise"
        WifiInfo.SECURITY_TYPE_SAE -> "WPA3-Personal (SAE)"
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise 192-bit"
        WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
        WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI-PSK"
        WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-CERT"
        WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> "Passpoint R1/R2"
        WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint R3"
        WifiInfo.SECURITY_TYPE_DPP -> "DPP"
        WifiInfo.SECURITY_TYPE_OSEN -> "OSEN"
        WifiInfo.SECURITY_TYPE_UNKNOWN -> "unknown"
        else -> type.toString()
    }

    private fun bandName(band: Int): String = when (band) {
        ScanResult.WIFI_BAND_24_GHZ -> "2.4 GHz"
        ScanResult.WIFI_BAND_5_GHZ -> "5 GHz"
        ScanResult.WIFI_BAND_6_GHZ -> "6 GHz"
        ScanResult.WIFI_BAND_60_GHZ -> "60 GHz"
        else -> band.toString()
    }

    private fun mloState(state: Int): String = when (state) {
        android.net.wifi.MloLink.MLO_LINK_STATE_ACTIVE -> "active"
        android.net.wifi.MloLink.MLO_LINK_STATE_IDLE -> "idle"
        android.net.wifi.MloLink.MLO_LINK_STATE_UNASSOCIATED -> "unassociated"
        android.net.wifi.MloLink.MLO_LINK_STATE_INVALID -> "invalid"
        else -> state.toString()
    }

    private fun ipv4(address: Int): String? {
        if (address == 0) return null
        return listOf(
            address and 0xff,
            address shr 8 and 0xff,
            address shr 16 and 0xff,
            address shr 24 and 0xff,
        ).joinToString(".")
    }
}
