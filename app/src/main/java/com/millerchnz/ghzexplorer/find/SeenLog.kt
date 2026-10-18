package com.millerchnz.ghzexplorer.find

import com.millerchnz.ghzexplorer.data.ApiField
import com.millerchnz.ghzexplorer.data.ExportDevice
import com.millerchnz.ghzexplorer.data.SeenPlace
import com.millerchnz.ghzexplorer.data.SeenSpan

/**
 * Remembers the first and latest place each device was heard, including after it leaves the map.
 * The first place stays. Later samples replace the latest place.
 */
class SeenLog {
    private val first = linkedMapOf<String, SeenPlace>()
    private val last = linkedMapOf<String, SeenPlace>()

    fun note(key: String, latitude: Double, longitude: Double, rssiDbm: Int) {
        val place = SeenPlace(latitude, longitude, rssiDbm)
        if (first[key] == null) first[key] = place
        last[key] = place
    }

    fun span(key: String): SeenSpan? {
        val start = first[key] ?: return null
        val end = last[key] ?: return null
        return SeenSpan(start, end)
    }

    fun device(title: String, subtitle: String, fields: List<ApiField>, key: String): ExportDevice =
        ExportDevice(title, subtitle, fields, span(key))
}
