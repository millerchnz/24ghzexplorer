package com.millerchnz.ghzexplorer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatting that does not need an Android framework class, so the unit tests can
 * run on a plain JVM. Names that come from Android constants stay next to those calls.
 */
object RadioText {
    private val clock: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM HH:mm:ss").withZone(ZoneId.systemDefault())
    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

    fun formatTime(epochMillis: Long): String = clock.format(Instant.ofEpochMilli(epochMillis))

    /** Date and time of day, including seconds. */
    fun formatDateTime(epochMillis: Long): String = dateTime.format(Instant.ofEpochMilli(epochMillis))

    fun hex(bytes: ByteArray, limit: Int = 48): String {
        val shown = if (bytes.size <= limit) bytes else bytes.copyOf(limit)
        val body = shown.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return if (bytes.size <= limit) body else "$body… (${bytes.size} bytes)"
    }

    /** Every byte, 16 per line, with an ASCII column. Empty input is an empty string. */
    fun hexDump(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return bytes.toList().chunked(16).mapIndexed { row, chunk ->
            val offset = "%04X".format(row * 16)
            val hex = chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                .padEnd(16 * 3 - 1, ' ')
            val ascii = chunk.joinToString("") { byte ->
                val c = byte.toInt() and 0xFF
                if (c in 32..126) c.toChar().toString() else "."
            }
            "$offset  $hex  $ascii"
        }.joinToString("\n")
    }

    /**
     * IEEE channel for a centre frequency. Returns null when the frequency is not on
     * a 2.4, 5, or 6 GHz channel raster Android's scanner uses.
     */
    fun wifiChannel(frequencyMhz: Int): Int? = when (frequencyMhz) {
        2484 -> 14
        in 2412..2472 -> if ((frequencyMhz - 2412) % 5 == 0) (frequencyMhz - 2412) / 5 + 1 else null
        in 5160..5885 -> if ((frequencyMhz - 5000) % 5 == 0) (frequencyMhz - 5000) / 5 else null
        in 5955..7115 -> if ((frequencyMhz - 5950) % 5 == 0) (frequencyMhz - 5950) / 5 else null
        else -> null
    }

    fun wifiBand(frequencyMhz: Int): String = when (frequencyMhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> "other"
    }

    /** NFC Forum URI record prefix codes. Index is the first payload byte. */
    val ndefUriPrefixes: List<String> = listOf(
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:",
    )

    fun ndefUri(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefix = ndefUriPrefixes.getOrNull(payload[0].toInt() and 0xFF) ?: return null
        val rest = payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
        return prefix + rest
    }

    /**
     * NFC Forum Text record: status byte, language code, then the text.
     * Bit 7 of the status selects UTF-16 when set.
     */
    fun ndefText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val langLength = status and 0x3F
        val utf16 = status and 0x80 != 0
        if (payload.size < 1 + langLength) return null
        val textBytes = payload.copyOfRange(1 + langLength, payload.size)
        val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
        return textBytes.toString(charset)
    }

    fun quoteStripped(ssid: String?): String? {
        if (ssid.isNullOrBlank()) return null
        val bare = ssid.removePrefix("\"").removeSuffix("\"")
        if (bare.isBlank() || bare == "<unknown ssid>") return null
        return bare
    }

    fun rssi(dbm: Int?): String = dbm?.let { "$it dBm" } ?: "—"
}
