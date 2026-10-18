package com.millerchnz.ghzexplorer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A name the user gave a device, stored against that device's own id.
 * Bluetooth uses the address, Wi-Fi the BSSID and frequency, NFC the tag uid, and a cell its identity.
 */
class AliasStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
    private val _names = MutableStateFlow(load())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    fun current(id: String): String = _names.value[id].orEmpty()

    fun name(id: String, reported: String): String = current(id).ifBlank { reported }

    fun set(id: String, alias: String) {
        val trimmed = alias.trim()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_PREFIX + id) else putString(KEY_PREFIX + id, trimmed)
        }.apply()
        _names.value = load()
    }

    private fun load(): Map<String, String> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY_PREFIX) || value !is String || value.isBlank()) null
        else key.removePrefix(KEY_PREFIX) to value
    }.toMap()

    companion object {
        private const val KEY_PREFIX = "alias:"
    }
}
