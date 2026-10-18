package com.millerchnz.ghzexplorer.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcBarcode
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.RadioText
import com.millerchnz.ghzexplorer.data.reported
import com.millerchnz.ghzexplorer.data.reportedBool
import com.millerchnz.ghzexplorer.data.reportedInt

data class NfcSighting(
    val key: String,
    val uid: String,
    val scannedAt: Long,
    val title: String,
    val subtitle: String,
    val trailing: String,
    val fields: List<ApiField>,
)

object NfcFields {
    fun read(tag: Tag, atMillis: Long): NfcSighting {
        val id = tag.id ?: ByteArray(0)
        val uid = if (id.isEmpty()) "no-id" else RadioText.hex(id, limit = Int.MAX_VALUE)
        val idHex = if (id.isEmpty()) "no id" else RadioText.hex(id, limit = 16)
        val techs = tag.techList.orEmpty().map { it.substringAfterLast('.') }
        val fields = buildList {
            reported("Tag", "Identifier", if (id.isEmpty()) null else RadioText.hexDump(id))
            reportedInt("Tag", "Identifier length", id.size, "bytes", unavailable = -1)
            reported("Tag", "Technologies", techs.joinToString(", ").ifBlank { null })
            reported("Tag", "Scanned", RadioText.formatTime(atMillis))
            nfcA(tag)
            nfcB(tag)
            nfcF(tag)
            nfcV(tag)
            isoDep(tag)
            barcode(tag)
            mifareClassic(tag)
            mifareUltralight(tag)
            ndef(tag)
            reportedBool("Tag", "NDEF formattable", NdefFormatable.get(tag) != null)
        }
        return NfcSighting(
            key = "$uid@$atMillis",
            uid = uid,
            scannedAt = atMillis,
            title = techs.firstOrNull() ?: "NFC tag",
            subtitle = idHex,
            trailing = RadioText.formatTime(atMillis).substringAfter(' '),
            fields = fields,
        )
    }

    private fun MutableList<ApiField>.nfcA(tag: Tag) {
        val tech = NfcA.get(tag) ?: return
        reported("NfcA", "ATQA", RadioText.hex(tech.atqa))
        reported("NfcA", "SAK", "0x${tech.sak.toString(16)}")
        reportedInt("NfcA", "Max transceive", tech.maxTransceiveLength, "bytes")
    }

    private fun MutableList<ApiField>.nfcB(tag: Tag) {
        val tech = NfcB.get(tag) ?: return
        reported("NfcB", "Application data", RadioText.hexDump(tech.applicationData).ifBlank { null })
        reported("NfcB", "Protocol info", RadioText.hex(tech.protocolInfo))
        reportedInt("NfcB", "Max transceive", tech.maxTransceiveLength, "bytes")
    }

    private fun MutableList<ApiField>.nfcF(tag: Tag) {
        val tech = NfcF.get(tag) ?: return
        reported("NfcF", "Manufacturer", RadioText.hex(tech.manufacturer))
        reported("NfcF", "System code", RadioText.hex(tech.systemCode))
        reportedInt("NfcF", "Max transceive", tech.maxTransceiveLength, "bytes")
    }

    private fun MutableList<ApiField>.nfcV(tag: Tag) {
        val tech = NfcV.get(tag) ?: return
        reported("NfcV", "DSF id", "0x${tech.dsfId.toString(16)}")
        reported("NfcV", "Response flags", "0x${tech.responseFlags.toString(16)}")
        reportedInt("NfcV", "Max transceive", tech.maxTransceiveLength, "bytes")
    }

    private fun MutableList<ApiField>.isoDep(tag: Tag) {
        val tech = IsoDep.get(tag) ?: return
        reported("IsoDep", "Historical bytes", tech.historicalBytes?.let { RadioText.hexDump(it).ifBlank { null } })
        reported("IsoDep", "Hi-layer response", tech.hiLayerResponse?.let { RadioText.hexDump(it).ifBlank { null } })
        runCatching { tech.isExtendedLengthApduSupported }.getOrNull()?.let {
            reportedBool("IsoDep", "Extended length", it)
        }
        reportedInt("IsoDep", "Max transceive", tech.maxTransceiveLength, "bytes")
    }

    private fun MutableList<ApiField>.barcode(tag: Tag) {
        val tech = NfcBarcode.get(tag) ?: return
        reported("Barcode", "Type", barcodeType(tech.type))
        reported("Barcode", "Barcode", tech.barcode?.let { RadioText.hexDump(it).ifBlank { null } })
    }

    private fun MutableList<ApiField>.mifareClassic(tag: Tag) {
        val tech = MifareClassic.get(tag) ?: return
        try {
            tech.connect()
            reported("Mifare Classic", "Type", mifareType(tech.type))
            reported("Mifare Classic", "Size", mifareSize(tech.size))
            reportedInt("Mifare Classic", "Sector count", tech.sectorCount)
            reportedInt("Mifare Classic", "Block count", tech.blockCount)
            reported(
                "Mifare Classic",
                "Block contents",
                "Not read. Those blocks stay on the card until a key is presented, and this app does not present one.",
            )
        } catch (error: Exception) {
            reported("Mifare Classic", "Read note", error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { tech.close() }
        }
    }

    private fun MutableList<ApiField>.mifareUltralight(tag: Tag) {
        val tech = MifareUltralight.get(tag) ?: return
        try {
            tech.connect()
            reported("Mifare Ultralight", "Type", ultralightType(tech.type))
        } catch (error: Exception) {
            reported("Mifare Ultralight", "Read note", error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { tech.close() }
        }
    }

    private fun MutableList<ApiField>.ndef(tag: Tag) {
        val tech = Ndef.get(tag) ?: return
        val cached = runCatching { tech.cachedNdefMessage }.getOrNull()
        reported("NDEF", "Type", runCatching { tech.type }.getOrNull())
        runCatching { tech.isWritable }.getOrNull()?.let { reportedBool("NDEF", "Writable", it) }
        runCatching { tech.canMakeReadOnly() }.getOrNull()?.let { reportedBool("NDEF", "Can be made read-only", it) }
        runCatching { tech.maxSize }.getOrNull()?.let { reportedInt("NDEF", "Max size", it, "bytes") }
        if (cached != null) {
            message(cached, "cached")
            return
        }
        var live: NdefMessage? = null
        try {
            tech.connect()
            live = tech.ndefMessage
        } catch (error: Exception) {
            reported("NDEF", "Read note", error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { tech.close() }
        }
        if (live != null) message(live, "read") else reported("NDEF", "Message", null)
    }

    private fun MutableList<ApiField>.message(message: NdefMessage, source: String) {
        val records = message.records.orEmpty()
        reported(
            "NDEF",
            "Contents",
            "Not encrypted. The hex below is the bytes on the tag. Text and URI are those same bytes, decoded.",
        )
        reported("NDEF", "Records ($source)", records.size.toString())
        reported("NDEF", "Message hex", RadioText.hexDump(message.toByteArray()).ifBlank { null })
        records.forEachIndexed { index, record -> recordFields(index, record) }
    }

    private fun MutableList<ApiField>.recordFields(index: Int, record: NdefRecord) {
        val section = "NDEF record ${index + 1}"
        reported(section, "TNF", tnfName(record.tnf))
        reported(section, "Type", record.type?.let { it.toString(Charsets.UTF_8).ifBlank { RadioText.hex(it) } })
        reported(section, "Id", record.id?.takeIf { it.isNotEmpty() }?.let { RadioText.hex(it) })
        val payload = record.payload ?: ByteArray(0)
        reported(section, "Payload length", payload.size.toString(), "bytes")
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                reported(section, "Text", RadioText.ndefText(payload))
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) ->
                reported(section, "URI", RadioText.ndefUri(payload))
            else -> reported(section, "URI", record.toUri()?.toString())
        }
        reported(section, "Payload hex", RadioText.hexDump(payload).ifBlank { null })
    }

    private fun barcodeType(type: Int): String = when (type) {
        NfcBarcode.TYPE_KOVIO -> "Kovio"
        NfcBarcode.TYPE_UNKNOWN -> "unknown"
        else -> type.toString()
    }

    private fun mifareType(type: Int): String = when (type) {
        MifareClassic.TYPE_CLASSIC -> "Classic"
        MifareClassic.TYPE_PLUS -> "Plus"
        MifareClassic.TYPE_PRO -> "Pro"
        else -> "unknown"
    }

    private fun mifareSize(size: Int): String = when (size) {
        MifareClassic.SIZE_MINI -> "320 bytes"
        MifareClassic.SIZE_1K -> "1 KB"
        MifareClassic.SIZE_2K -> "2 KB"
        MifareClassic.SIZE_4K -> "4 KB"
        else -> size.toString()
    }

    private fun ultralightType(type: Int): String = when (type) {
        MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
        MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
        else -> "unknown"
    }

    private fun tnfName(tnf: Short): String = when (tnf) {
        NdefRecord.TNF_EMPTY -> "empty"
        NdefRecord.TNF_WELL_KNOWN -> "well known"
        NdefRecord.TNF_MIME_MEDIA -> "MIME"
        NdefRecord.TNF_ABSOLUTE_URI -> "absolute URI"
        NdefRecord.TNF_EXTERNAL_TYPE -> "external"
        NdefRecord.TNF_UNKNOWN -> "unknown"
        NdefRecord.TNF_UNCHANGED -> "unchanged"
        else -> tnf.toString()
    }
}
