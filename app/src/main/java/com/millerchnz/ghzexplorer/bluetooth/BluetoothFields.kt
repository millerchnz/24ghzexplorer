package com.millerchnz.ghzexplorer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.ParcelUuid
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.RadioText
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.data.reportedInt

data class BtSighting(
    val key: String,
    val title: String,
    val subtitle: String,
    val trailing: String,
    val address: String?,
    val fields: List<ApiField>,
    val alias: String? = null,
    val manufacturers: List<String> = emptyList(),
)

/** One BLE advertisement's parsed fields, kept so a later classic sighting does not wipe them. */
data class LeAdvertisement(
    val fields: List<ApiField>,
    val manufacturers: List<String> = emptyList(),
)

object BluetoothFields {
    const val ADAPTER_KEY = "adapter"
    private const val SIG_BASE = "-0000-1000-8000-00805f9b34fb"

    fun sighting(
        address: String,
        name: String?,
        alias: String?,
        type: Int,
        bondState: Int,
        deviceClass: BluetoothClass?,
        rssi: Int?,
        cachedUuids: List<ParcelUuid>?,
        addressType: Int?,
        advertisement: LeAdvertisement?,
        fromClassic: Boolean,
        fromBonded: Boolean,
    ): BtSighting {
        val title = name ?: alias ?: "Unnamed device"
        val kind = typeName(type)
        val heard = when {
            advertisement != null && fromClassic -> "LE + classic"
            advertisement != null -> "LE"
            fromClassic -> "classic"
            fromBonded -> "bonded, not heard this scan"
            else -> kind
        }
        val fields = buildList {
            reported("Identity", "Name", name)
            reported("Identity", "Alias", alias)
            reported("Identity", "Address", address)
            if (address == "02:00:00:00:00:00") {
                reported("Identity", "Address note", "Android redacted the address for this app")
            }
            reported("Identity", "Address type", addressType?.let { addressTypeName(it) })
            reported("Identity", "Device type", kind)
            reported("Bond", "Bond state", bondName(bondState))
            if (deviceClass != null) {
                reported("Class", "Major class", majorName(deviceClass.majorDeviceClass))
                reported("Class", "Device class", "0x${deviceClass.deviceClass.toString(16)}")
                reported("Class", "Services", serviceBits(deviceClass).ifEmpty { null }?.joinToString(", "))
            } else {
                reported("Class", "Major class", null)
                reported("Class", "Device class", null)
                reported("Class", "Services", null)
            }
            reportedInt("Signal", "RSSI", rssi ?: Int.MAX_VALUE, "dBm")
            reported("How it was seen", "Seen as", heard)
            val uuids = cachedUuids.orEmpty()
            if (uuids.isEmpty()) {
                reported("Cached UUIDs", "UUIDs", null)
            } else {
                uuids.forEach { reported("Cached UUIDs", uuidLabel(it.uuid.toString()), it.uuid.toString()) }
            }
            addAll(advertisement?.fields.orEmpty())
        }
        val manufacturers = advertisement?.manufacturers.orEmpty()
        return BtSighting(
            key = address,
            title = title,
            subtitle = "$heard · $address",
            trailing = RadioText.rssi(rssi),
            address = address,
            fields = fields,
            alias = alias?.takeIf { it.isNotBlank() },
            manufacturers = manufacturers,
        )
    }

    /** List subtitle: alias, then a known maker, then how the device was seen. */
    fun listSubtitle(shownTitle: String, alias: String?, manufacturers: List<String>, subtitle: String): String {
        val extra = buildList {
            val nick = alias?.trim().orEmpty()
            if (nick.isNotEmpty() && !nick.equals(shownTitle, ignoreCase = true)) add(nick)
            val makers = manufacturers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (makers.isNotEmpty()) add(makers.joinToString(", "))
        }
        return if (extra.isEmpty()) subtitle else extra.joinToString(" · ") + " · " + subtitle
    }

    fun advertisement(result: ScanResult): LeAdvertisement {
        val record = result.scanRecord
        val fields = buildList {
            reportedBool("Advertisement", "Connectable", result.isConnectable)
            reportedBool("Advertisement", "Legacy advertising", result.isLegacy)
            reported("Advertisement", "Primary PHY", phyName(result.primaryPhy))
            reported("Advertisement", "Secondary PHY", phyName(result.secondaryPhy))
            reportedInt("Advertisement", "RSSI", result.rssi, "dBm")
            val tx = result.txPower
            reported("Advertisement", "TX power", tx.takeUnless { it == ScanResult.TX_POWER_NOT_PRESENT }?.let { "$it dBm" })
            val sid = result.advertisingSid
            reported("Advertisement", "Advertising SID", sid.takeUnless { it == ScanResult.SID_NOT_PRESENT }?.toString())
            reported("Advertisement", "Data status", dataStatus(result.dataStatus))
            val interval = result.periodicAdvertisingInterval
            reported(
                "Advertisement",
                "Periodic advertising interval",
                interval.takeUnless { it == ScanResult.PERIODIC_INTERVAL_NOT_PRESENT }?.let { "$it × 1.25 ms" },
            )
            reported("Advertisement", "Timestamp", result.timestampNanos.takeIf { it > 0L }?.toString(), "ns")
            if (record != null) appendRecord(record)
        }
        return LeAdvertisement(fields, knownManufacturers(record))
    }

    private fun knownManufacturers(record: ScanRecord?): List<String> {
        val data = record?.manufacturerSpecificData ?: return emptyList()
        return (0 until data.size()).mapNotNull { knownCompany(data.keyAt(it)) }.distinct()
    }

    fun knownCompany(id: Int): String? = COMPANIES[id]

    private fun MutableList<ApiField>.appendRecord(record: ScanRecord) {
        reported("Advertisement", "Local name", record.deviceName)
        val flags = record.advertiseFlags
        reported("Advertisement", "Flags", if (flags < 0) null else flagsText(flags))
        val recordTx = record.txPowerLevel
        reported("Advertisement", "TX power in AD", recordTx.takeUnless { it == Int.MIN_VALUE }?.let { "$it dBm" })
        val services = record.serviceUuids.orEmpty()
        if (services.isEmpty()) {
            reported("Services in advertisement", "Service UUIDs", null)
        } else {
            services.forEach { uuid ->
                reported("Services in advertisement", uuidLabel(uuid.uuid.toString()), uuid.uuid.toString())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val solicited = record.serviceSolicitationUuids.orEmpty()
            if (solicited.isEmpty()) {
                reported("Services in advertisement", "Solicited UUIDs", null)
            } else {
                solicited.forEach { uuid ->
                    reported("Services in advertisement", "Solicits ${uuidLabel(uuid.uuid.toString())}", uuid.uuid.toString())
                }
            }
        }
        val manufacturers = record.manufacturerSpecificData
        if (manufacturers == null || manufacturers.size() == 0) {
            reported("Manufacturer data", "Company data", null)
        } else {
            for (i in 0 until manufacturers.size()) {
                val company = manufacturers.keyAt(i)
                val bytes = manufacturers.valueAt(i)
                reported(
                    "Manufacturer data",
                    companyName(company),
                    RadioText.hex(bytes),
                )
            }
        }
        val serviceData = record.serviceData
        if (serviceData.isNullOrEmpty()) {
            reported("Service data", "Service data", null)
        } else {
            serviceData.forEach { (uuid, bytes) ->
                reported("Service data", uuidLabel(uuid.uuid.toString()), RadioText.hex(bytes))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val raw = record.advertisingDataMap
            if (raw.isEmpty()) {
                reported("Raw AD structures", "Structures", null)
            } else {
                raw.forEach { (type, bytes) ->
                    reported("Raw AD structures", adTypeName(type), RadioText.hex(bytes))
                }
            }
        }
        reported("Advertisement", "Raw bytes", record.bytes?.let { RadioText.hex(it, limit = 64) })
    }

    fun properties(bits: Int): String {
        val names = listOf(
            BluetoothGattCharacteristic.PROPERTY_BROADCAST to "broadcast",
            BluetoothGattCharacteristic.PROPERTY_READ to "read",
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE to "write without response",
            BluetoothGattCharacteristic.PROPERTY_WRITE to "write",
            BluetoothGattCharacteristic.PROPERTY_NOTIFY to "notify",
            BluetoothGattCharacteristic.PROPERTY_INDICATE to "indicate",
            BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE to "signed write",
            BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS to "extended properties",
        )
        return names.filter { bits and it.first != 0 }.joinToString(", ").ifEmpty { "none" }
    }

    fun permissions(bits: Int): String {
        if (bits == 0) return "not reported"
        val names = listOf(
            BluetoothGattCharacteristic.PERMISSION_READ to "read",
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED to "read encrypted",
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM to "read encrypted with MITM",
            BluetoothGattCharacteristic.PERMISSION_WRITE to "write",
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED to "write encrypted",
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM to "write encrypted with MITM",
            BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED to "write signed",
            BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM to "write signed with MITM",
        )
        return names.filter { bits and it.first != 0 }.joinToString(", ").ifEmpty { "0x${bits.toString(16)}" }
    }

    fun typeName(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual (classic + LE)"
        else -> "Unknown"
    }

    fun bondName(state: Int): String = when (state) {
        BluetoothDevice.BOND_NONE -> "not bonded"
        BluetoothDevice.BOND_BONDING -> "bonding"
        BluetoothDevice.BOND_BONDED -> "bonded"
        else -> state.toString()
    }

    /**
     * Only PIN and passkey-confirmation are public constants. The other variant numbers
     * are the ones the platform has always put in [BluetoothDevice.EXTRA_PAIRING_VARIANT].
     */
    fun pairingVariant(variant: Int): String = when (variant) {
        BluetoothDevice.PAIRING_VARIANT_PIN -> "PIN entry"
        1 -> "passkey entry"
        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "confirm passkey"
        3 -> "consent"
        4 -> "display passkey"
        5 -> "display PIN"
        6 -> "out-of-band consent"
        7 -> "16-digit PIN"
        else -> "variant $variant"
    }

    fun phyName(phy: Int): String = when (phy) {
        BluetoothDevice.PHY_LE_1M -> "LE 1M"
        BluetoothDevice.PHY_LE_2M -> "LE 2M"
        BluetoothDevice.PHY_LE_CODED -> "LE Coded"
        ScanResult.PHY_UNUSED -> "unused"
        else -> phy.toString()
    }

    fun gattStatus(status: Int): String = when (status) {
        0 -> "success"
        2 -> "read not permitted"
        3 -> "write not permitted"
        5 -> "insufficient authentication"
        6 -> "request not supported"
        15 -> "insufficient encryption"
        133 -> "connection failed (133)"
        143 -> "connection congested"
        257 -> "failure"
        else -> "status $status"
    }

    fun uuidLabel(uuid: String): String {
        val lower = uuid.lowercase()
        if (!lower.endsWith(SIG_BASE)) return uuid
        val short = lower.substring(4, 8)
        val name = SIG_NAMES[short]
        return if (name == null) "0x$short" else "$name (0x$short)"
    }

    private fun flagsText(flags: Int): String {
        val parts = buildList {
            if (flags and 0x01 != 0) add("LE limited discoverable")
            if (flags and 0x02 != 0) add("LE general discoverable")
            if (flags and 0x04 != 0) add("BR/EDR not supported")
            if (flags and 0x08 != 0) add("simultaneous LE and BR/EDR (controller)")
            if (flags and 0x10 != 0) add("simultaneous LE and BR/EDR (host)")
        }
        val hex = "0x${flags.toString(16)}"
        return if (parts.isEmpty()) hex else "$hex · ${parts.joinToString(", ")}"
    }

    private fun dataStatus(status: Int): String = when (status) {
        ScanResult.DATA_COMPLETE -> "complete"
        ScanResult.DATA_TRUNCATED -> "truncated"
        else -> status.toString()
    }

    private fun addressTypeName(type: Int): String = when (type) {
        BluetoothDevice.ADDRESS_TYPE_PUBLIC -> "public"
        BluetoothDevice.ADDRESS_TYPE_RANDOM -> "random"
        BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> "anonymous"
        else -> type.toString()
    }

    private fun majorName(major: Int): String = when (major) {
        BluetoothClass.Device.Major.MISC -> "Miscellaneous"
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.NETWORKING -> "Networking"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / video"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"
        else -> "0x${major.toString(16)}"
    }

    private fun serviceBits(deviceClass: BluetoothClass): List<String> {
        val known = listOf(
            BluetoothClass.Service.LIMITED_DISCOVERABILITY to "limited discoverable",
            BluetoothClass.Service.POSITIONING to "positioning",
            BluetoothClass.Service.NETWORKING to "networking",
            BluetoothClass.Service.RENDER to "render",
            BluetoothClass.Service.CAPTURE to "capture",
            BluetoothClass.Service.OBJECT_TRANSFER to "object transfer",
            BluetoothClass.Service.AUDIO to "audio",
            BluetoothClass.Service.TELEPHONY to "telephony",
            BluetoothClass.Service.INFORMATION to "information",
        )
        return known.filter { deviceClass.hasService(it.first) }.map { it.second }
    }

    private fun companyName(id: Int): String {
        val known = knownCompany(id)
        val hex = "0x${id.toString(16).padStart(4, '0')}"
        return if (known == null) "Company $hex" else "$known ($hex)"
    }

    private fun adTypeName(type: Int): String {
        val name = when (type) {
            ScanRecord.DATA_TYPE_FLAGS -> "Flags"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL -> "16-bit UUIDs partial"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE -> "16-bit UUIDs complete"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL -> "32-bit UUIDs partial"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE -> "32-bit UUIDs complete"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL -> "128-bit UUIDs partial"
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE -> "128-bit UUIDs complete"
            ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT -> "Short name"
            ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE -> "Complete name"
            ScanRecord.DATA_TYPE_TX_POWER_LEVEL -> "TX power"
            ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT -> "Service data 16-bit"
            ScanRecord.DATA_TYPE_SERVICE_DATA_32_BIT -> "Service data 32-bit"
            ScanRecord.DATA_TYPE_SERVICE_DATA_128_BIT -> "Service data 128-bit"
            ScanRecord.DATA_TYPE_MANUFACTURER_SPECIFIC_DATA -> "Manufacturer data"
            ScanRecord.DATA_TYPE_APPEARANCE -> "Appearance"
            else -> null
        }
        val hex = "0x${type.toString(16).padStart(2, '0')}"
        return if (name == null) "AD $hex" else "$name ($hex)"
    }

    private val COMPANIES = mapOf(
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x004C to "Apple",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung",
        0x00E0 to "Google",
        0x038F to "Xiaomi",
    )

    private val SIG_NAMES = mapOf(
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "1802" to "Immediate Alert",
        "1803" to "Link Loss",
        "1804" to "Tx Power",
        "1805" to "Current Time",
        "180a" to "Device Information",
        "180d" to "Heart Rate",
        "180f" to "Battery",
        "1812" to "Human Interface Device",
        "1815" to "Automation IO",
        "181a" to "Environmental Sensing",
        "181c" to "User Data",
        "fe2c" to "Google Fast Pair",
    )

    @SuppressLint("MissingPermission")
    fun safeName(device: BluetoothDevice): String? = runCatching { device.name }.getOrNull()

    @SuppressLint("MissingPermission")
    fun safeAlias(device: BluetoothDevice): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { device.alias }.getOrNull()
        } else {
            null
        }
}
