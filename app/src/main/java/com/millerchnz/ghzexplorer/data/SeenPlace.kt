package com.millerchnz.ghzexplorer.data

/** Where the phone was when it heard a device, and how strong that signal was. */
data class SeenPlace(
    val latitude: Double,
    val longitude: Double,
    val rssiDbm: Int,
)

/** The earliest and latest places a device has been heard in this process. */
data class SeenSpan(
    val first: SeenPlace,
    val last: SeenPlace,
)
