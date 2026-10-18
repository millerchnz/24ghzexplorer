package com.millerchnz.ghzexplorer.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothListLabelTest {
    @Test
    fun knownMakersUseTheCompanyId() {
        assertEquals("Apple", BluetoothFields.knownCompany(0x004C))
        assertEquals("Samsung", BluetoothFields.knownCompany(0x0075))
        assertNull(BluetoothFields.knownCompany(0xFFFF))
    }

    @Test
    fun listSubtitleAddsAliasAndMakerWhenTheyAreNotAlreadyTheTitle() {
        val line = BluetoothFields.listSubtitle(
            shownTitle = "Front door",
            alias = "Kitchen lock",
            manufacturers = listOf("Samsung", "Samsung"),
            subtitle = "LE · AA:BB:CC:DD:EE:FF",
        )
        assertEquals("Kitchen lock · Samsung · LE · AA:BB:CC:DD:EE:FF", line)
    }

    @Test
    fun listSubtitleSkipsAnAliasThatIsAlreadyTheTitle() {
        val line = BluetoothFields.listSubtitle(
            shownTitle = "Kitchen lock",
            alias = "Kitchen lock",
            manufacturers = listOf("Apple"),
            subtitle = "LE · AA:BB:CC:DD:EE:FF",
        )
        assertEquals("Apple · LE · AA:BB:CC:DD:EE:FF", line)
    }
}
