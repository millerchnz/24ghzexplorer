package com.millerchnz.ghzexplorer.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tags seen while the NFC tab is resumed. Reader mode is the only way a tag is delivered. */
class NfcCatalog {
    private val main = Handler(Looper.getMainLooper())
    private val tags = ArrayDeque<NfcSighting>()

    private val _sightings = MutableStateFlow<List<NfcSighting>>(emptyList())
    val sightings: StateFlow<List<NfcSighting>> = _sightings.asStateFlow()

    private val _status = MutableStateFlow("Hold a tag against the phone.")
    val status: StateFlow<String> = _status.asStateFlow()

    fun sighting(key: String): NfcSighting? = _sightings.value.firstOrNull { it.key == key }

    /** A later read of the same identifier, if the tag was presented again. */
    fun newerReading(uid: String, afterMillis: Long): NfcSighting? =
        _sightings.value.filter { it.uid == uid && it.scannedAt > afterMillis }.maxByOrNull { it.scannedAt }

    fun adapter(activity: Activity): NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun enable(activity: Activity) {
        val adapter = adapter(activity)
        if (adapter == null) {
            _status.value = "This phone has no NFC radio."
            return
        }
        if (!adapter.isEnabled) {
            _status.value = "NFC is turned off. Enable it in system settings, then come back."
            return
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        adapter.enableReaderMode(activity, { tag -> onTag(tag) }, flags, null)
        _status.value = "Reader mode is on. Hold a card or tag against the phone."
    }

    fun disable(activity: Activity) {
        val adapter = adapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }

    private fun onTag(tag: Tag) {
        val sighting = NfcFields.read(tag, System.currentTimeMillis())
        main.post {
            tags.addFirst(sighting)
            while (tags.size > MAX_TAGS) tags.removeLast()
            _sightings.value = tags.toList()
            _status.value = "Read ${sighting.title}. Hold another tag to add it."
        }
    }

    companion object {
        private const val MAX_TAGS = 30
    }
}
