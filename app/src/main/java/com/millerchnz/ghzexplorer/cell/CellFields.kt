package com.millerchnz.ghzexplorer.cell

import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.data.reportedInt
import com.millerchnz.ghzexplorer.data.reportedLong

data class CellSighting(
    val key: String,
    val title: String,
    val subtitle: String,
    val trailing: String,
    val fields: List<ApiField>,
    val heardElapsedMs: Long = 0L,
)

object CellFields {
    const val PHONE_KEY = "phone"

    fun of(info: CellInfo): CellSighting {
        val fields = buildList {
            common(info)
            when (info) {
                is CellInfoLte -> lte(info.cellIdentity, info.cellSignalStrength)
                is CellInfoGsm -> gsm(info.cellIdentity, info.cellSignalStrength)
                is CellInfoWcdma -> wcdma(info.cellIdentity, info.cellSignalStrength)
                is CellInfoCdma -> cdma(info.cellIdentity, info.cellSignalStrength)
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) NewerCells.add(this, info)
            }
            reported("Platform", "CellInfo", info.toString())
        }
        val dbm = info.cellSignalStrength.dbm.takeUnless { it == Int.MAX_VALUE }
        val title = fields.firstOrNull { it.label == "Radio" }?.raw ?: "Cell"
        val operator = fields.firstOrNull { it.label == "Operator" }?.raw
        return CellSighting(
            key = info.cellIdentity.toString(),
            title = title,
            subtitle = operator ?: if (info.isRegistered) "Registered" else "Neighbour",
            trailing = dbm?.let { "$it dBm" } ?: "—",
            fields = fields,
            heardElapsedMs = info.timeStamp.takeIf { it > 0L }?.div(1_000_000) ?: 0L,
        )
    }

    private fun MutableList<ApiField>.common(info: CellInfo) {
        reportedBool("Cell", "Registered", info.isRegistered)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            reported("Cell", "Connection", connection(info.cellConnectionStatus))
        }
        reported("Cell", "Timestamp", info.timeStamp.takeIf { it > 0L }?.toString(), "ns")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reportedLong("Cell", "Timestamp", info.timestampMillis, "ms")
        }
    }

    private fun MutableList<ApiField>.lte(identity: CellIdentityLte, signal: CellSignalStrengthLte) {
        reported("Cell", "Radio", "LTE")
        names(
            { identity.operatorAlphaLong },
            { identity.operatorAlphaShort },
            { identity.mccString },
            { identity.mncString },
        )
        reportedInt("LTE identity", "CI", identity.ci)
        reportedInt("LTE identity", "PCI", identity.pci)
        reportedInt("LTE identity", "TAC", identity.tac)
        reportedInt("LTE identity", "EARFCN", identity.earfcn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            reportedInt("LTE identity", "Bandwidth", identity.bandwidth, "kHz")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reported("LTE identity", "Bands", identity.bands.takeIf { it.isNotEmpty() }?.joinToString(", "))
            reported("LTE identity", "Additional PLMNs", identity.additionalPlmns.takeIf { it.isNotEmpty() }?.joinToString(", "))
            reported("LTE identity", "Closed subscriber group", identity.closedSubscriberGroupInfo?.toString())
        }
        reportedInt("LTE signal", "RSRP", signal.rsrp, "dBm")
        reportedInt("LTE signal", "RSRQ", signal.rsrq, "dB")
        reportedInt("LTE signal", "RSSNR", signal.rssnr, "dB")
        reportedInt("LTE signal", "CQI", signal.cqi)
        reportedInt("LTE signal", "Timing advance", signal.timingAdvance)
        reportedInt("LTE signal", "Level", signal.level, unavailable = -1)
        reportedInt("LTE signal", "ASU", signal.asuLevel)
        reportedInt("LTE signal", "dBm", signal.dbm, "dBm")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            reportedInt("LTE signal", "RSSI", signal.rssi, "dBm")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            reportedInt("LTE signal", "CQI table", signal.cqiTableIndex)
        }
    }

    private fun MutableList<ApiField>.gsm(identity: CellIdentityGsm, signal: CellSignalStrengthGsm) {
        reported("Cell", "Radio", "GSM")
        names(
            { identity.operatorAlphaLong },
            { identity.operatorAlphaShort },
            { identity.mccString },
            { identity.mncString },
        )
        reportedInt("GSM identity", "CID", identity.cid)
        reportedInt("GSM identity", "LAC", identity.lac)
        reportedInt("GSM identity", "ARFCN", identity.arfcn)
        reportedInt("GSM identity", "BSIC", identity.bsic)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reported("GSM identity", "Additional PLMNs", identity.additionalPlmns.takeIf { it.isNotEmpty() }?.joinToString(", "))
        }
        reportedInt("GSM signal", "dBm", signal.dbm, "dBm")
        reportedInt("GSM signal", "Level", signal.level, unavailable = -1)
        reportedInt("GSM signal", "ASU", signal.asuLevel)
        reportedInt("GSM signal", "Timing advance", signal.timingAdvance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            reportedInt("GSM signal", "RSSI", signal.rssi, "dBm")
            reportedInt("GSM signal", "Bit error rate", signal.bitErrorRate)
        }
    }

    private fun MutableList<ApiField>.wcdma(identity: CellIdentityWcdma, signal: CellSignalStrengthWcdma) {
        reported("Cell", "Radio", "WCDMA")
        names(
            { identity.operatorAlphaLong },
            { identity.operatorAlphaShort },
            { identity.mccString },
            { identity.mncString },
        )
        reportedInt("WCDMA identity", "CID", identity.cid)
        reportedInt("WCDMA identity", "LAC", identity.lac)
        reportedInt("WCDMA identity", "PSC", identity.psc)
        reportedInt("WCDMA identity", "UARFCN", identity.uarfcn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reported("WCDMA identity", "Additional PLMNs", identity.additionalPlmns.takeIf { it.isNotEmpty() }?.joinToString(", "))
            reported("WCDMA identity", "Closed subscriber group", identity.closedSubscriberGroupInfo?.toString())
        }
        reportedInt("WCDMA signal", "dBm", signal.dbm, "dBm")
        reportedInt("WCDMA signal", "Level", signal.level, unavailable = -1)
        reportedInt("WCDMA signal", "ASU", signal.asuLevel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reportedInt("WCDMA signal", "Ec/No", signal.ecNo, "dB")
        }
    }

    private fun MutableList<ApiField>.cdma(identity: CellIdentityCdma, signal: CellSignalStrengthCdma) {
        reported("Cell", "Radio", "CDMA")
        reportedInt("CDMA identity", "Base station", identity.basestationId)
        reportedInt("CDMA identity", "Network id", identity.networkId)
        reportedInt("CDMA identity", "System id", identity.systemId)
        reportedInt("CDMA identity", "Latitude", identity.latitude)
        reportedInt("CDMA identity", "Longitude", identity.longitude)
        reportedInt("CDMA signal", "CDMA", signal.cdmaDbm, "dBm")
        reportedInt("CDMA signal", "CDMA Ec/Io", signal.cdmaEcio, "dB")
        reportedInt("CDMA signal", "EVDO", signal.evdoDbm, "dBm")
        reportedInt("CDMA signal", "EVDO Ec/Io", signal.evdoEcio, "dB")
        reportedInt("CDMA signal", "EVDO SNR", signal.evdoSnr)
        reportedInt("CDMA signal", "dBm", signal.dbm, "dBm")
        reportedInt("CDMA signal", "Level", signal.level, unavailable = -1)
    }

    private fun MutableList<ApiField>.names(
        longName: () -> CharSequence?,
        shortName: () -> CharSequence?,
        mcc: () -> String?,
        mnc: () -> String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            reported("Cell", "Operator", longName()?.toString())
            reported("Cell", "Operator short", shortName()?.toString())
            reported("Cell", "MCC", mcc())
            reported("Cell", "MNC", mnc())
        }
    }

    fun networkType(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO 0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO A"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO B"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "unknown"
        else -> type.toString()
    }

    private fun connection(status: Int): String = when (status) {
        CellInfo.CONNECTION_NONE -> "none"
        CellInfo.CONNECTION_PRIMARY_SERVING -> "primary serving"
        CellInfo.CONNECTION_SECONDARY_SERVING -> "secondary serving"
        CellInfo.CONNECTION_UNKNOWN -> "unknown"
        else -> status.toString()
    }
}

/** NR and TD-SCDMA classes do not exist before API 29, so they stay in this object. */
private object NewerCells {
    fun add(fields: MutableList<ApiField>, info: CellInfo) {
        when (info) {
            is CellInfoNr -> fields.nr(info)
            is CellInfoTdscdma -> fields.tdscdma(info)
        }
    }

    private fun MutableList<ApiField>.nr(info: CellInfoNr) {
        val identity = info.cellIdentity as android.telephony.CellIdentityNr
        val signal = info.cellSignalStrength as CellSignalStrengthNr
        reported("Cell", "Radio", "NR")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            reported("Cell", "Operator", identity.operatorAlphaLong?.toString())
            reported("Cell", "Operator short", identity.operatorAlphaShort?.toString())
        }
        reported("NR identity", "MCC", identity.mccString)
        reported("NR identity", "MNC", identity.mncString)
        reportedLong("NR identity", "NCI", identity.nci)
        reportedInt("NR identity", "PCI", identity.pci)
        reportedInt("NR identity", "TAC", identity.tac)
        reportedInt("NR identity", "NR-ARFCN", identity.nrarfcn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reported("NR identity", "Bands", identity.bands.takeIf { it.isNotEmpty() }?.joinToString(", "))
            reported("NR identity", "Additional PLMNs", identity.additionalPlmns.takeIf { it.isNotEmpty() }?.joinToString(", "))
        }
        reportedInt("NR signal", "SS-RSRP", signal.ssRsrp, "dBm")
        reportedInt("NR signal", "SS-RSRQ", signal.ssRsrq, "dB")
        reportedInt("NR signal", "SS-SINR", signal.ssSinr, "dB")
        reportedInt("NR signal", "CSI-RSRP", signal.csiRsrp, "dBm")
        reportedInt("NR signal", "CSI-RSRQ", signal.csiRsrq, "dB")
        reportedInt("NR signal", "CSI-SINR", signal.csiSinr, "dB")
        reportedInt("NR signal", "Level", signal.level, unavailable = -1)
        reportedInt("NR signal", "dBm", signal.dbm, "dBm")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            reported("NR signal", "CSI CQI", signal.csiCqiReport.takeIf { it.isNotEmpty() }?.joinToString(", "))
            reportedInt("NR signal", "CSI CQI table", signal.csiCqiTableIndex)
            reportedInt("NR signal", "Timing advance", signal.timingAdvanceMicros, "µs")
        }
    }

    private fun MutableList<ApiField>.tdscdma(info: CellInfoTdscdma) {
        val identity = info.cellIdentity
        val signal = info.cellSignalStrength
        reported("Cell", "Radio", "TD-SCDMA")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            reported("Cell", "Operator", identity.operatorAlphaLong?.toString())
            reported("Cell", "Operator short", identity.operatorAlphaShort?.toString())
        }
        reported("TD-SCDMA identity", "MCC", identity.mccString)
        reported("TD-SCDMA identity", "MNC", identity.mncString)
        reportedInt("TD-SCDMA identity", "CID", identity.cid)
        reportedInt("TD-SCDMA identity", "LAC", identity.lac)
        reportedInt("TD-SCDMA identity", "CPID", identity.cpid)
        reportedInt("TD-SCDMA identity", "UARFCN", identity.uarfcn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reported("TD-SCDMA identity", "Additional PLMNs", identity.additionalPlmns.takeIf { it.isNotEmpty() }?.joinToString(", "))
        }
        reportedInt("TD-SCDMA signal", "RSCP", signal.rscp, "dBm")
        reportedInt("TD-SCDMA signal", "dBm", signal.dbm, "dBm")
        reportedInt("TD-SCDMA signal", "Level", signal.level, unavailable = -1)
    }
}
