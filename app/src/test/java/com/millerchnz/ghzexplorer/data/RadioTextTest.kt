package com.millerchnz.ghzexplorer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTextTest {
    @Test
    fun channelNumbersFollowTheWifiRaster() {
        assertEquals(1, RadioText.wifiChannel(2412))
        assertEquals(6, RadioText.wifiChannel(2437))
        assertEquals(14, RadioText.wifiChannel(2484))
        assertEquals(36, RadioText.wifiChannel(5180))
        assertEquals(5, RadioText.wifiChannel(5975))
        assertNull(RadioText.wifiChannel(2400))
    }

    @Test
    fun bandsCoverTheRangesTheScannerReturns() {
        assertEquals("2.4 GHz", RadioText.wifiBand(2437))
        assertEquals("5 GHz", RadioText.wifiBand(5180))
        assertEquals("6 GHz", RadioText.wifiBand(5975))
    }

    @Test
    fun hiddenOrRedactedSsidsAreNotShownAsNames() {
        assertNull(RadioText.quoteStripped(null))
        assertNull(RadioText.quoteStripped("<unknown ssid>"))
        assertEquals("Home", RadioText.quoteStripped("\"Home\""))
    }

    @Test
    fun ndefUriUsesTheForumPrefixByte() {
        val payload = byteArrayOf(0x04) + "example.com".toByteArray()
        assertEquals("https://example.com", RadioText.ndefUri(payload))
    }

    @Test
    fun ndefTextStripsTheLanguageCode() {
        val payload = byteArrayOf(0x02, 'e'.code.toByte(), 'n'.code.toByte()) + "Hi".toByteArray()
        assertEquals("Hi", RadioText.ndefText(payload))
    }

    @Test
    fun hexTruncatesLongAdvertisements() {
        val bytes = ByteArray(4) { it.toByte() }
        assertEquals("00010203", RadioText.hex(bytes))
        assertEquals("00… (3 bytes)", RadioText.hex(byteArrayOf(0, 1, 2), limit = 1))
        assertEquals("FF", RadioText.hex(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun hexDumpKeepsEveryByte() {
        val dump = RadioText.hexDump(byteArrayOf(0x02, 0x65, 0x6E, 'H'.code.toByte(), 'i'.code.toByte()))
        assertTrue(dump.contains("02 65 6E 48 69"))
        assertTrue(dump.contains("enHi"))
    }

    @Test
    fun exportWritesSectionsAndSaysWhenRefreshStopped() {
        val text = ExportText.document(
            kind = "NFC",
            title = "NfcA",
            fields = listOf(ApiField("NDEF", "Payload hex", "0000  02 65")),
            stopped = true,
            atMillis = 0L,
        )
        assertTrue(text.contains("[NDEF]"))
        assertTrue(text.contains("Payload hex:"))
        assertTrue(text.contains("no longer heard"))
        assertTrue(text.contains("First location: —"))
        assertTrue(text.contains("Last strength: —"))
        assertEquals("nfc-nfca.txt", ExportText.fileName("nfc", "NfcA"))
    }

    @Test
    fun exportIncludesTheFirstAndLastPlace() {
        val text = ExportText.document(
            kind = "Wi-Fi",
            title = "Home",
            fields = emptyList(),
            stopped = false,
            atMillis = 0L,
            seen = SeenSpan(
                SeenPlace(-36.8, 174.7, -62),
                SeenPlace(-36.801, 174.702, -48),
            ),
        )
        assertTrue(text.contains("First location: -36.800000, 174.700000"))
        assertTrue(text.contains("First strength: -62 dBm"))
        assertTrue(text.contains("Last location: -36.801000, 174.702000"))
        assertTrue(text.contains("Last strength: -48 dBm"))
    }

    @Test
    fun listExportKeepsEveryDeviceOnThePage() {
        val text = ExportText.listDocument(
            kind = "Wi-Fi",
            devices = listOf(
                ExportDevice("Home", "aa:bb", listOf(ApiField("Signal", "RSSI", "-40", "dBm"))),
                ExportDevice("Cafe", "cc:dd", listOf(ApiField("Signal", "RSSI", "-70", "dBm"))),
            ),
            atMillis = 0L,
        )
        assertTrue(text.contains("2 devices"))
        assertTrue(text.contains("==== Home ===="))
        assertTrue(text.contains("aa:bb"))
        assertTrue(text.contains("RSSI: -40 dBm"))
        assertTrue(text.contains("==== Cafe ===="))
        assertEquals("wifi-devices.txt", ExportText.fileName("wifi", "devices"))
    }

    @Test
    fun aMissingFieldRendersAsADash() {
        assertEquals("—", ApiField("Signal", "RSSI", null, "dBm").display)
        assertEquals("-67 dBm", ApiField("Signal", "RSSI", "-67", "dBm").display)
    }
}
